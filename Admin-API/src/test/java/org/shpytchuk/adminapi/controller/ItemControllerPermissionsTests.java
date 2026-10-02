package org.shpytchuk.adminapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.shpytchuk.adminapi.config.CountriesConfig;
import org.shpytchuk.adminapi.config.GlobalExceptionHandler;
import org.shpytchuk.adminapi.config.MapsConfig;
import org.shpytchuk.adminapi.config.SecurityConfig;
import org.shpytchuk.adminapi.entity.found.FoundItem;
import org.shpytchuk.adminapi.entity.found.FoundItemHistory;
import org.shpytchuk.adminapi.entity.lost.LostItem;
import org.shpytchuk.adminapi.entity.lost.LostItemHistory;
import org.shpytchuk.adminapi.entity.thing.Thing;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.form.ItemFormValidator;
import org.shpytchuk.adminapi.model.GlobalModelAdvice;
import org.shpytchuk.adminapi.model.ItemModel;
import org.shpytchuk.adminapi.repository.thing.ThingCategoryRepository;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.Permissions;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.AdminItemService;
import org.shpytchuk.adminapi.service.ImageStorage;
import org.shpytchuk.adminapi.service.found.FoundItemAdminService;
import org.shpytchuk.adminapi.service.found.FoundItemHistoryAdminService;
import org.shpytchuk.adminapi.service.lost.LostItemAdminService;
import org.shpytchuk.adminapi.service.lost.LostItemHistoryAdminService;
import org.shpytchuk.adminapi.view.Formats;
import org.shpytchuk.adminapi.view.Plurals;
import org.shpytchuk.adminapi.view.detail.SocialMediaIcons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The four item controllers share {@link ItemController}, but each one repeats the {@code @RequirePermission}
 * on every handler because the annotation needs a constant scope. This walks every route of every controller:
 * its own {@code SCOPE:ACTION} opens it, the same action on a neighbouring scope (the likely copy-paste slip)
 * does not.
 */
@WebMvcTest(value = {LostItemController.class, FoundItemController.class,
        LostItemHistoryController.class, FoundItemHistoryController.class},
        properties = {"derechi.admin.client-id=derechi-admin", "derechi.admin.page-size=20",
                "derechi.notifications.exchange=derechi.notifications",
                "derechi.notifications.routing-key=notification.match.found",
                "derechi.maps.api-key=",
                "derechi.countries.supported=UA,PL,DE,FR", "derechi.countries.fallback=UA"})
@Import({SecurityConfig.class, MapsConfig.class, CountriesConfig.class, GlobalModelAdvice.class,
        GlobalExceptionHandler.class, ItemModel.class, ItemFormValidator.class,
        Formats.class, Plurals.class, SocialMediaIcons.class,
        ItemControllerPermissionsTests.TestClients.class})
class ItemControllerPermissionsTests {

    private record Route(String name, Action action,
                         Function<String, MockHttpServletRequestBuilder> request,
                         Consumer<AdminItemService<?>> serviceCall) {

        @Override
        public String toString() {
            return name;
        }
    }

    private record Kind(Scope scope, Scope neighbour, String basePath, boolean archivable) {

        @Override
        public String toString() {
            return scope.name();
        }
    }

    private static final List<Route> ROUTES = List.of(
            new Route("list", Action.VIEW, base -> get(base), service -> service.page(any(), any())),
            new Route("create form", Action.CREATE, base -> get(base + "/new"), null),
            new Route("create", Action.CREATE, base -> validForm(post(base)), service -> service.create(any())),
            new Route("edit form", Action.EDIT, base -> get(base + "/7/edit"), service -> service.form(7L)),
            new Route("update", Action.EDIT, base -> validForm(post(base + "/7")),
                    service -> service.update(eq(7L), any())),
            new Route("archive", Action.ARCHIVE, base -> post(base + "/7/archive"),
                    service -> service.archive(eq(7L), any())),
            new Route("delete", Action.DELETE, base -> post(base + "/7/delete"), service -> service.delete(7L)));

    private static final List<Kind> KINDS = List.of(
            new Kind(Scope.LOST_ITEM, Scope.FOUND_ITEM, LostItemController.BASE_PATH, true),
            new Kind(Scope.FOUND_ITEM, Scope.LOST_ITEM, FoundItemController.BASE_PATH, true),
            new Kind(Scope.LOST_ITEM_HISTORY, Scope.LOST_ITEM, LostItemHistoryController.BASE_PATH, false),
            new Kind(Scope.FOUND_ITEM_HISTORY, Scope.FOUND_ITEM, FoundItemHistoryController.BASE_PATH, false));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LostItemAdminService lostItems;

    @MockitoBean
    private FoundItemAdminService foundItems;

    @MockitoBean
    private LostItemHistoryAdminService lostHistory;

    @MockitoBean
    private FoundItemHistoryAdminService foundHistory;

    @MockitoBean
    private ThingCategoryRepository categoryRepository;

    @MockitoBean
    private ImageStorage imageStorage;

    static Stream<Arguments> routes() {
        List<Arguments> routes = new ArrayList<>();
        for (Kind kind : KINDS) {
            for (Route route : ROUTES) {
                if (route.action() != Action.ARCHIVE || kind.archivable()) {
                    routes.add(arguments(kind, route));
                }
            }
        }
        return routes.stream();
    }

    @BeforeEach
    void stubServices() {
        stub(lostItems, Scope.LOST_ITEM, LostItem::new);
        stub(foundItems, Scope.FOUND_ITEM, FoundItem::new);
        stub(lostHistory, Scope.LOST_ITEM_HISTORY, LostItemHistory::new);
        stub(foundHistory, Scope.FOUND_ITEM_HISTORY, FoundItemHistory::new);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("routes")
    void opensTheRouteToItsOwnScopeAndAction(Kind kind, Route route) throws Exception {
        mockMvc.perform(route.request().apply(kind.basePath())
                        .with(oidcLogin().authorities(authority(kind.scope(), route.action())))
                        .with(csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("status").isBetween(200, 399));

        if (route.serviceCall() != null) {
            route.serviceCall().accept(verify(serviceOf(kind.scope())));
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("routes")
    void refusesTheSameActionOnTheNeighbouringScope(Kind kind, Route route) throws Exception {
        mockMvc.perform(route.request().apply(kind.basePath())
                        .with(oidcLogin().authorities(authority(kind.neighbour(), route.action())))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        if (route.serviceCall() != null) {
            route.serviceCall().accept(verify(serviceOf(kind.scope()), never()));
        }
    }

    private AdminItemService<?> serviceOf(Scope scope) {
        return switch (scope) {
            case LOST_ITEM -> lostItems;
            case FOUND_ITEM -> foundItems;
            case LOST_ITEM_HISTORY -> lostHistory;
            case FOUND_ITEM_HISTORY -> foundHistory;
            default -> throw new IllegalArgumentException(scope.name());
        };
    }

    private static <T extends Thing> void stub(AdminItemService<T> service, Scope scope, Supplier<T> factory) {
        T saved = factory.get();
        saved.setId(7L);
        when(service.scopeKey()).thenReturn(scope.name());
        when(service.page(any(), any())).thenReturn(Page.empty());
        when(service.claims(anyCollection())).thenReturn(Map.of());
        when(service.form(7L)).thenReturn(form());
        when(service.create(any())).thenReturn(saved);
        when(service.update(eq(7L), any())).thenReturn(saved);
    }

    private static ItemForm form() {
        ItemForm form = new ItemForm();
        form.setId(7L);
        form.setTitle("Рюкзак");
        form.setDate(LocalDate.of(2026, 1, 2));
        form.setCategoryId(1L);
        form.setPlaceId("ChIJtest");
        form.setPlaceName("Park");
        form.setCountryCode("UA");
        form.setLat(49.8);
        form.setLon(24.0);
        form.setPhone("+380671234567");
        form.setEmail("a@b.test");
        return form;
    }

    private static MockHttpServletRequestBuilder validForm(MockHttpServletRequestBuilder request) {
        return request
                .param("title", "Рюкзак")
                .param("date", "2026-01-02")
                .param("categoryId", "1")
                .param("placeId", "ChIJtest")
                .param("placeName", "Park")
                .param("countryCode", "UA")
                .param("lat", "49.8")
                .param("lon", "24.0")
                .param("phone", "+380671234567")
                .param("email", "a@b.test");
    }

    private static SimpleGrantedAuthority authority(Scope scope, Action action) {
        return new SimpleGrantedAuthority(Permissions.authority(scope, action));
    }

    @TestConfiguration
    static class TestClients {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration
                    .withRegistrationId("keycloak")
                    .clientId("derechi-admin")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid")
                    .issuerUri("http://localhost:8180/realms/derechi")
                    .authorizationUri("http://localhost:8180/realms/derechi/protocol/openid-connect/auth")
                    .tokenUri("http://localhost:8180/realms/derechi/protocol/openid-connect/token")
                    .jwkSetUri("http://localhost:8180/realms/derechi/protocol/openid-connect/certs")
                    .userInfoUri("http://localhost:8180/realms/derechi/protocol/openid-connect/userinfo")
                    .userNameAttributeName("preferred_username")
                    .build());
        }
    }
}

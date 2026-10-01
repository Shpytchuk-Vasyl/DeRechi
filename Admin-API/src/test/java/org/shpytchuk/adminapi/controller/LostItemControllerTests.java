package org.shpytchuk.adminapi.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.adminapi.config.CountriesConfig;
import org.shpytchuk.adminapi.config.GlobalExceptionHandler;
import org.shpytchuk.adminapi.config.MapsConfig;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.form.ItemFormValidator;
import org.shpytchuk.adminapi.service.ImageStorage;
import org.shpytchuk.adminapi.view.Formats;
import org.shpytchuk.adminapi.view.Plurals;
import org.shpytchuk.adminapi.view.SocialMediaIcons;
import org.shpytchuk.adminapi.config.SecurityConfig;
import org.shpytchuk.adminapi.entity.items.LostItem;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.Permissions;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.shpytchuk.adminapi.service.LostItemAdminService;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.shpytchuk.adminapi.view.ItemView;
import org.shpytchuk.adminapi.view.ClaimStatus;
import org.shpytchuk.adminapi.view.ClaimView;
import org.shpytchuk.adminapi.entity.ContactInfo.SocialMediaEnum;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = LostItemController.class,
        properties = {"derechi.admin.client-id=derechi-admin", "derechi.admin.page-size=20",
                "derechi.notifications.exchange=derechi.notifications",
                "derechi.notifications.routing-key=notification.match.found",
                "derechi.maps.api-key=",
                "derechi.countries.supported=UA,PL,DE,FR", "derechi.countries.fallback=UA"})
@Import({SecurityConfig.class, MapsConfig.class, CountriesConfig.class, GlobalModelAdvice.class,
        GlobalExceptionHandler.class, ItemModel.class, ItemFormValidator.class,
        Formats.class, Plurals.class, SocialMediaIcons.class,
        LostItemControllerTests.TestClients.class})
class LostItemControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LostItemAdminService service;

    @MockitoBean
    private ThingCategoryRepository categoryRepository;

    /** Реальне сховище тягне S3-клієнт, якого у зрізі @WebMvcTest немає. */
    @MockitoBean
    private ImageStorage imageStorage;

    @Test
    void showsTheTableToAnAdminWithViewPermission() throws Exception {
        when(service.page(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/admin/lost-items").with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.VIEW))))
                .andExpect(status().isOk())
                .andExpect(view().name("items/list"));
    }

    @Test
    void bindsTheFilterAndKeepsItInTheFormAndThePager() throws Exception {
        when(service.page(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/admin/lost-items")
                        .param("q", "  rukzak  ")
                        .param("categoryId", "3")
                        .param("from", "2026-03-01")
                        .param("to", "2026-01-31")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.VIEW))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("value=\"rukzak\""),
                        containsString("value=\"2026-01-31\""),
                        containsString("value=\"2026-03-01\""),
                        containsString("Скинути"))));

        ArgumentCaptor<ItemFilter> filter = ArgumentCaptor.forClass(ItemFilter.class);
        verify(service).page(any(), filter.capture());

        assertThat(filter.getValue().q()).as("пошук обрізається").isEqualTo("rukzak");
        assertThat(filter.getValue().categoryId()).isEqualTo(3L);
        assertThat(filter.getValue().from()).as("перевернутий діапазон").isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(filter.getValue().to()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void treatsBlankFilterFieldsAsNoFilter() throws Exception {
        when(service.page(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/admin/lost-items")
                        .param("q", "   ").param("categoryId", "").param("from", "").param("to", "")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.VIEW))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Скинути"))));

        ArgumentCaptor<ItemFilter> filter = ArgumentCaptor.forClass(ItemFilter.class);
        verify(service).page(any(), filter.capture());

        assertThat(filter.getValue().active()).isFalse();
    }

    @Test
    void rendersThumbnailAndDialogTriggersForEachRow() throws Exception {
        when(service.page(any(), any())).thenReturn(new PageImpl<>(List.of(itemView())));

        mockMvc.perform(get("/admin/lost-items").with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.VIEW))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("command=\"show-modal\""),
                        containsString("commandfor=\"item-7\""),
                        containsString("commandfor=\"photo-item-7\""),
                        containsString("command=\"close\""),
                        containsString("<dialog class=\"app-dialog\" closedby=\"any\" id=\"item-7\">"),
                        containsString("<dialog class=\"photo-dialog\" closedby=\"any\" id=\"photo-item-7\">"),
                        containsString("class=\"thumb-img\""))));
    }

    @Test
    void fallsBackToThePlaceholderWhenAnItemHasNoImage() throws Exception {
        when(service.page(any(), any())).thenReturn(new PageImpl<>(List.of(itemViewWithoutImage())));

        mockMvc.perform(get("/admin/lost-items").with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.VIEW))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("thumb image is-48x48"),
                        not(containsString("thumb-img")),
                        not(containsString("commandfor=\"photo-item-7\"")),
                        not(containsString("photo-dialog")))));
    }

    private static ItemView itemView() {
        return new ItemView(7L, "Rukzak", "Opys", LocalDate.of(2026, 1, 2), 500, "PLN",
                "https://example.test/photo.jpg", "BAG", "Park", "PL", 49.8, 24.0,
                "+380671234567", "a@b.test", List.of());
    }

    private static ItemView itemViewWithoutImage() {
        return new ItemView(7L, "Rukzak", null, LocalDate.of(2026, 1, 2), null, "UAH",
                null, "BAG", "Park", "UA", 49.8, 24.0,
                "+380671234567", "a@b.test", List.of());
    }

    @Test
    void showsTheRewardInTheItemCurrencyAndTheCountryOfThePlace() throws Exception {
        when(service.page(any(), any())).thenReturn(new PageImpl<>(List.of(itemView())));

        mockMvc.perform(get("/admin/lost-items").with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.VIEW))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Польща"),
                        containsString("500"),
                        anyOf(containsString("zł"), containsString("PLN")),
                        not(containsString("₴")))));
    }

    @Test
    void showsTheClaimCountNextToTheTitleAndListsTheClaimsInTheDialog() throws Exception {
        when(service.page(any(), any())).thenReturn(new PageImpl<>(List.of(itemView())));
        when(service.claims(List.of(7L))).thenReturn(Map.of(7L, List.of(
                new ClaimView("+48501234567", "claimant@example.test", List.of(SocialMediaEnum.TELEGRAM),
                        Instant.parse("2026-09-01T10:00:00Z"), ClaimStatus.CONFIRMED),
                new ClaimView("+380509876543", "second@example.test", List.of(),
                        Instant.parse("2026-09-02T10:00:00Z"), ClaimStatus.NEW))));

        mockMvc.perform(get("/admin/lost-items").with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.VIEW))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("2 відгуки"),
                        containsString("Відгуки"),
                        containsString("+48 501 234 567"),
                        containsString("claimant@example.test"),
                        containsString("second@example.test"),
                        containsString("messenger-telegram"),
                        containsString("2026"),
                        containsString("is-success"),
                        containsString("Підтверджено"),
                        containsString("Новий"),
                        not(containsString("Відгуків ще немає")))));
    }

    @Test
    void saysThereAreNoClaimsYetWhenTheItemHasNone() throws Exception {
        when(service.page(any(), any())).thenReturn(new PageImpl<>(List.of(itemView())));
        when(service.claims(List.of(7L))).thenReturn(Map.of(7L, List.of()));

        mockMvc.perform(get("/admin/lost-items").with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.VIEW))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Відгуків ще немає"),
                        not(containsString("відгуки")),
                        not(containsString("tag is-info is-light is-rounded ml-1")))));
    }

    @Test
    void rendersWithoutTheClaimsBlockWhenTheServiceHasNoClaimsMap() throws Exception {
        when(service.page(any(), any())).thenReturn(new PageImpl<>(List.of(itemView())));
        when(service.claims(any())).thenReturn(Map.of());

        mockMvc.perform(get("/admin/lost-items").with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.VIEW))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"item-7\""),
                        not(containsString("Відгук")),
                        not(containsString("відгук")))));
    }

    @Test
    void archivesOnlyWithArchivePermission() throws Exception {
        mockMvc.perform(post("/admin/lost-items/7/archive")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.EDIT)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verify(service, never()).archive(anyLong(), any());

        when(service.scopeKey()).thenReturn("LOST_ITEM");

        mockMvc.perform(post("/admin/lost-items/7/archive")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.ARCHIVE)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(service).archive(eq(7L), any());
    }

    @Test
    void hidesTheTableFromAnAdminWithoutViewPermission() throws Exception {
        mockMvc.perform(get("/admin/lost-items").with(oidcLogin().authorities(authority(Scope.FOUND_ITEM, Action.VIEW))))
                .andExpect(status().isForbidden());

        verify(service, never()).page(any(), any());
    }

    @Test
    void deletesOnlyWithDeletePermission() throws Exception {
        mockMvc.perform(post("/admin/lost-items/7/delete")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.EDIT)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verify(service, never()).delete(anyLong());

        when(service.scopeKey()).thenReturn("LOST_ITEM");

        mockMvc.perform(post("/admin/lost-items/7/delete")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.DELETE)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(service).delete(7L);
    }

    @Test
    void rendersTheCreateFormWithPlaceholdersAndAutofillHints() throws Exception {
        mockMvc.perform(get("/admin/lost-items/new")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.CREATE))))
                .andExpect(status().isOk())
                .andExpect(view().name("items/form"))
                .andExpect(content().string(allOf(
                        containsString("autocomplete=\"tel\""),
                        containsString("autocomplete=\"email\""),
                        containsString("placeholder=\"+380671234567\""))));
    }


    @Test
    void offersManualPlaceFieldsWhenTheMapsKeyIsMissing() throws Exception {
        mockMvc.perform(get("/admin/lost-items/new")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.CREATE))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        not(containsString("maps.googleapis.com")),
                        containsString("id=\"place-manual\""),
                        containsString("open"))));
    }

    @Test
    void offersSupportedCountriesWithTheFallbackPreselectedAndCurrenciesByCountry() throws Exception {
        mockMvc.perform(get("/admin/lost-items/new")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.CREATE))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"countryCode\""),
                        containsString("value=\"UA\" selected=\"selected\""),
                        containsString("Польща"),
                        containsString("id=\"currency\""),
                        containsString("За країною"),
                        containsString("value=\"PLN\""),
                        containsString("value=\"EUR\""),
                        not(containsString("value=\"USD\"")))));
    }

    @Test
    void rejectsAnUnsupportedCountryOrCurrencyAsAFieldError() throws Exception {
        mockMvc.perform(validCreate()
                        .param("countryCode", "US")
                        .param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(view().name("items/form"))
                .andExpect(content().string(allOf(
                        containsString("Ця країна не підтримується"),
                        containsString("Ця валюта не підтримується"))));

        verify(service, never()).create(any());
    }

    @Test
    void passesTheCountryAndAnEmptyCurrencyToTheService() throws Exception {
        when(service.create(any())).thenReturn(lostItem());
        when(service.scopeKey()).thenReturn("LOST_ITEM");

        mockMvc.perform(validCreate()
                        .param("countryCode", "PL")
                        .param("currency", ""))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<ItemForm> form = ArgumentCaptor.forClass(ItemForm.class);
        verify(service).create(form.capture());
        assertThat(form.getValue().getCountryCode()).isEqualTo("PL");
        assertThat(form.getValue().getCurrency()).as("«за країною» приходить порожнім і стає null").isNull();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validCreate() {
        return post("/admin/lost-items")
                .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.CREATE)))
                .with(csrf())
                .param("title", "Рюкзак")
                .param("date", "2026-01-02")
                .param("categoryId", "1")
                .param("placeId", "ChIJtest")
                .param("placeName", "Park")
                .param("lat", "52.2")
                .param("lon", "21.0")
                .param("phone", "+48501234567")
                .param("email", "a@b.test");
    }

    @Test
    void createsOnlyWithCreatePermission() throws Exception {
        when(service.create(org.mockito.ArgumentMatchers.any())).thenReturn(lostItem());

        mockMvc.perform(post("/admin/lost-items")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.VIEW)))
                        .with(csrf())
                        .param("title", "Рюкзак"))
                .andExpect(status().isForbidden());
    }

    private static LostItem lostItem() {
        LostItem item = new LostItem();
        item.setId(7L);
        return item;
    }

    private static org.springframework.security.core.GrantedAuthority authority(Scope scope, Action action) {
        return new org.springframework.security.core.authority.SimpleGrantedAuthority(Permissions.authority(scope, action));
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

package org.shpytchuk.adminapi.config;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.controller.LostItemController;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.form.ItemFormValidator;
import org.shpytchuk.adminapi.model.GlobalModelAdvice;
import org.shpytchuk.adminapi.model.ItemModel;
import org.shpytchuk.adminapi.repository.thing.ThingCategoryRepository;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.Permissions;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.ImageStorage;
import org.shpytchuk.adminapi.service.lost.LostItemAdminService;
import org.shpytchuk.adminapi.view.Formats;
import org.shpytchuk.adminapi.view.Plurals;
import org.shpytchuk.adminapi.view.detail.SocialMediaIcons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Every failure becomes a localized error page with the right status; the lost-item pages are the vehicle. */
@WebMvcTest(value = LostItemController.class,
        properties = {"derechi.admin.client-id=derechi-admin", "derechi.admin.page-size=20",
                "derechi.notifications.exchange=derechi.notifications",
                "derechi.notifications.routing-key=notification.match.found",
                "derechi.maps.api-key=",
                "derechi.countries.supported=UA,PL,DE,FR", "derechi.countries.fallback=UA"})
@Import({SecurityConfig.class, MapsConfig.class, CountriesConfig.class, GlobalModelAdvice.class,
        GlobalExceptionHandler.class, ItemModel.class, ItemFormValidator.class,
        Formats.class, Plurals.class, SocialMediaIcons.class,
        GlobalExceptionHandlerTests.TestClients.class})
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LostItemAdminService service;

    @MockitoBean
    private ThingCategoryRepository categoryRepository;

    @MockitoBean
    private ImageStorage imageStorage;

    @Test
    void rendersAMissingItemAsA404NamingWhatWasMissing() throws Exception {
        when(service.form(404L)).thenThrow(new NotFoundException("entity.LOST_ITEM", 404L));

        mockMvc.perform(get("/admin/lost-items/404/edit").with(admin(Action.EDIT)))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(containsString("Немає такої загубленої речі (404).")));
    }

    @Test
    void rendersADatabaseTimeoutAsA408() throws Exception {
        when(service.page(any(), any())).thenThrow(new QueryTimeoutException("canceling statement due to statement timeout"));

        mockMvc.perform(get("/admin/lost-items").with(admin(Action.VIEW)))
                .andExpect(status().isRequestTimeout())
                .andExpect(view().name("error/408"))
                .andExpect(content().string(containsString("Запит виконувався надто довго")));
    }

    @Test
    void rendersAnythingElseAsA500WithoutLeakingTheCause() throws Exception {
        when(service.page(any(), any())).thenThrow(new IllegalStateException("column secret_column does not exist"));

        mockMvc.perform(get("/admin/lost-items").with(admin(Action.VIEW)))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"))
                .andExpect(content().string(allOf(
                        containsString("Щось пішло не так"),
                        not(containsString("secret_column")))));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(Action action) {
        return oidcLogin().authorities(new SimpleGrantedAuthority(Permissions.authority(Scope.LOST_ITEM, action)));
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

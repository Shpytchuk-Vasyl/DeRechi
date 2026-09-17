package org.shpytchuk.adminapi.controller;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.config.GlobalExceptionHandler;
import org.shpytchuk.adminapi.config.SecurityConfig;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.Permissions;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.MatchNotificationService;
import org.shpytchuk.adminapi.service.MatchService;
import org.shpytchuk.adminapi.view.CandidateView;
import org.shpytchuk.adminapi.view.ItemView;
import org.shpytchuk.adminapi.view.MatchRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = MatchController.class,
        properties = {"derechi.admin.client-id=derechi-admin", "derechi.admin.page-size=20",
                "derechi.notifications.exchange=derechi.notifications",
                "derechi.notifications.routing-key=notification.match.found"})
@Import({SecurityConfig.class, GlobalModelAdvice.class, GlobalExceptionHandler.class,
        MatchControllerTests.TestClients.class})
class MatchControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchService matchService;

    @MockitoBean
    private MatchNotificationService notificationService;

    /**
     * Загублена і знайдена речі можуть мати однаковий id, тому фрагмент діалогу отримує
     * префікс простору імен — інакше обидві картки на сторінці мали б один id.
     */
    @Test
    void rendersSeparateDialogsForLostAndFoundItemsWithTheSameId() throws Exception {
        when(matchService.page(anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(
                new MatchRow(item(3L, "Lost"), List.of(
                        new CandidateView(item(3L, "Found"), 0.94, null, null))))));

        mockMvc.perform(get("/admin/matches")
                        .with(oidcLogin().authorities(authority(Scope.MATCH, Action.VIEW))))
                .andExpect(status().isOk())
                .andExpect(view().name("matches"))
                .andExpect(content().string(allOf(
                        containsString("id=\"lost-3\""),
                        containsString("id=\"found-3\""),
                        containsString("id=\"photo-lost-3\""),
                        containsString("id=\"photo-found-3\""),
                        containsString("commandfor=\"lost-3\""),
                        containsString("commandfor=\"found-3\""),
                        containsString("class=\"thumb-img\""))));
    }

    private static ItemView item(Long id, String title) {
        return new ItemView(id, title, "Opys", LocalDate.of(2026, 1, 2), 500,
                "https://example.test/photo.jpg", "BAG", "Park", 49.8, 24.0,
                "+380671234567", "a@b.test", List.of());
    }

    private static GrantedAuthority authority(Scope scope, Action action) {
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

package org.shpytchuk.adminapi.controller;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.config.GlobalExceptionHandler;
import org.shpytchuk.adminapi.config.MapsConfig;
import org.shpytchuk.adminapi.view.Formats;
import org.shpytchuk.adminapi.view.Plurals;
import org.shpytchuk.adminapi.view.SocialMediaIcons;
import org.shpytchuk.adminapi.config.SecurityConfig;
import org.shpytchuk.adminapi.entity.items.LostItemHistory;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.Permissions;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.shpytchuk.adminapi.service.LostItemHistoryAdminService;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = LostItemHistoryController.class,
        properties = {"derechi.admin.client-id=derechi-admin", "derechi.admin.page-size=20",
                "derechi.notifications.exchange=derechi.notifications",
                "derechi.notifications.routing-key=notification.match.found",
                "derechi.maps.api-key="})
@Import({SecurityConfig.class, MapsConfig.class, GlobalModelAdvice.class, GlobalExceptionHandler.class, ItemModel.class,
        Formats.class, Plurals.class, SocialMediaIcons.class,
        LostItemHistoryControllerTests.TestClients.class})
class LostItemHistoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LostItemHistoryAdminService service;

    @MockitoBean
    private ThingCategoryRepository categoryRepository;

    @Test
    void showsTheTableToAnAdminWithViewPermission() throws Exception {
        when(service.page(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/admin/lost-items-history").with(oidcLogin().authorities(authority(Scope.LOST_ITEM_HISTORY, Action.VIEW))))
                .andExpect(status().isOk())
                .andExpect(view().name("items/list"));
    }

    @Test
    void hidesTheArchiveFromAPlainAdmin() throws Exception {
        mockMvc.perform(get("/admin/lost-items-history").with(oidcLogin().authorities(
                        authority(Scope.LOST_ITEM, Action.VIEW),
                        authority(Scope.LOST_ITEM, Action.EDIT),
                        authority(Scope.FOUND_ITEM, Action.VIEW))))
                .andExpect(status().isForbidden());

        verify(service, never()).page(any(), any());
    }

    @Test
    void deletesOnlyWithDeletePermission() throws Exception {
        mockMvc.perform(post("/admin/lost-items-history/7/delete")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM_HISTORY, Action.EDIT)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verify(service, never()).delete(anyLong());

        when(service.scopeKey()).thenReturn("LOST_ITEM_HISTORY");

        mockMvc.perform(post("/admin/lost-items-history/7/delete")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM_HISTORY, Action.DELETE)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(service).delete(7L);
    }

    @Test
    void createsOnlyWithCreatePermission() throws Exception {
        when(service.create(org.mockito.ArgumentMatchers.any())).thenReturn(lostItem());

        mockMvc.perform(post("/admin/lost-items-history")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM_HISTORY, Action.VIEW)))
                        .with(csrf())
                        .param("title", "Рюкзак"))
                .andExpect(status().isForbidden());
    }

    private static LostItemHistory lostItem() {
        LostItemHistory item = new LostItemHistory();
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

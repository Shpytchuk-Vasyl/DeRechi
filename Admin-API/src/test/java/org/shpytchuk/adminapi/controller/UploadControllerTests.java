package org.shpytchuk.adminapi.controller;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.config.CountriesConfig;
import org.shpytchuk.adminapi.config.GlobalExceptionHandler;
import org.shpytchuk.adminapi.config.MapsConfig;
import org.shpytchuk.adminapi.config.SecurityConfig;
import org.shpytchuk.adminapi.exception.RejectedUploadException;
import org.shpytchuk.adminapi.model.GlobalModelAdvice;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.Permissions;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.ImageStorage;
import org.shpytchuk.adminapi.view.Formats;
import org.shpytchuk.adminapi.view.Plurals;
import org.shpytchuk.adminapi.view.detail.SocialMediaIcons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = UploadController.class,
        properties = {"derechi.admin.client-id=derechi-admin", "derechi.admin.page-size=20",
                "derechi.maps.api-key=",
                "derechi.countries.supported=UA,PL,DE,FR", "derechi.countries.fallback=UA"})
@Import({SecurityConfig.class, MapsConfig.class, CountriesConfig.class, GlobalModelAdvice.class,
        GlobalExceptionHandler.class, Formats.class, Plurals.class, SocialMediaIcons.class,
        UploadControllerTests.TestClients.class})
class UploadControllerTests {

    private static final MockMultipartFile PHOTO =
            new MockMultipartFile("file", "keys.png", "image/png", new byte[]{1, 2, 3});

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageStorage storage;

    @Test
    void storesThePhotoForAnAdminWhoMayEditAnyKindOfItem() throws Exception {
        when(storage.store(any())).thenReturn("items/2026/keys.png");

        mockMvc.perform(multipart(UploadController.BASE_PATH).file(PHOTO)
                        .with(oidcLogin().authorities(authority(Scope.FOUND_ITEM_HISTORY, Action.EDIT)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("items/2026/keys.png"));
    }

    @Test
    void refusesAnAdminWhoCanOnlyLook() throws Exception {
        mockMvc.perform(multipart(UploadController.BASE_PATH).file(PHOTO)
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.VIEW),
                                authority(Scope.LOST_ITEM, Action.DELETE)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(storage, never()).store(any());
    }

    @Test
    void answersARejectedFileWithTheReasonInTheAdminLanguage() throws Exception {
        when(storage.store(any())).thenThrow(new RejectedUploadException("upload.tooLarge"));

        mockMvc.perform(multipart(UploadController.BASE_PATH).file(PHOTO)
                        .cookie(new Cookie("DERECHI_LOCALE", "en"))
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.CREATE)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("The file is too big"));
    }

    @Test
    void deletesAnUploadTheFormNoLongerNeeds() throws Exception {
        mockMvc.perform(delete(UploadController.BASE_PATH).content("items/2026/keys.png")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.EDIT)))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(storage).delete("items/2026/keys.png");
    }

    @Test
    void refusesToDeleteForAnAdminWhoCanOnlyLook() throws Exception {
        mockMvc.perform(delete(UploadController.BASE_PATH).content("items/2026/keys.png")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.VIEW)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(storage, never()).delete(anyString());
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

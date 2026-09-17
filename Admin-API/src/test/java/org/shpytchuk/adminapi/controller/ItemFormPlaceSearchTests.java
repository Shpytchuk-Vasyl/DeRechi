package org.shpytchuk.adminapi.controller;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.config.GlobalExceptionHandler;
import org.shpytchuk.adminapi.config.MapsConfig;
import org.shpytchuk.adminapi.config.SecurityConfig;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.Permissions;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.LostItemAdminService;
import org.shpytchuk.adminapi.view.Formats;
import org.shpytchuk.adminapi.view.Plurals;
import org.shpytchuk.adminapi.view.SocialMediaIcons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(value = LostItemController.class,
        properties = {"derechi.admin.client-id=derechi-admin", "derechi.admin.page-size=20",
                "derechi.notifications.exchange=derechi.notifications",
                "derechi.notifications.routing-key=notification.match.found",
                "derechi.maps.api-key=test-key", "derechi.maps.region=UA"})
@Import({SecurityConfig.class, MapsConfig.class, GlobalModelAdvice.class, GlobalExceptionHandler.class,
        ItemModel.class, Formats.class, Plurals.class, SocialMediaIcons.class,
        LostItemControllerTests.TestClients.class})
class ItemFormPlaceSearchTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LostItemAdminService service;

    @MockitoBean
    private ThingCategoryRepository categoryRepository;

    @Test
    void mountsTheWidgetAndLoadsTheMapsScriptWithTheCurrentLanguage() throws Exception {
        mockMvc.perform(get("/admin/lost-items/new")
                        .param("lang", "pl")
                        .with(oidcLogin().authorities(authority(Scope.LOST_ITEM, Action.CREATE))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"place-search\""),
                        containsString("data-region=\"UA\""),
                        containsString("/js/place-autocomplete-"),
                        containsString("key=test-key"),
                        containsString("callback=initPlaceAutocomplete"),
                        containsString("language=pl"))));
    }

    private static GrantedAuthority authority(Scope scope, Action action) {
        return new SimpleGrantedAuthority(Permissions.authority(scope, action));
    }
}

package org.shpytchuk.clientapi.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SitePropertiesTest {

    @Test
    void buildsTheNoticeLinkWithoutALocaleOrADoubleSlash() {
        SiteProperties site = new SiteProperties(" https://derechi.example/ ");

        assertThat(site.url()).isEqualTo("https://derechi.example");
        assertThat(site.notice("found", 7L)).isEqualTo("https://derechi.example/found/7");
    }

    @Test
    void refusesToStartWithoutTheSite() {
        assertThatThrownBy(() -> new SiteProperties(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("derechi.site.url");
    }
}

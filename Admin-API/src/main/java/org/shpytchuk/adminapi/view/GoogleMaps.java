package org.shpytchuk.adminapi.view;

import org.shpytchuk.adminapi.config.property.MapsProperties;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

public class GoogleMaps {

    private static final String SCRIPT = "https://maps.googleapis.com/maps/api/js";

    private static final String CALLBACK = "initPlaceAutocomplete";

    private final MapsProperties properties;

    public GoogleMaps(MapsProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return StringUtils.hasText(properties.apiKey());
    }

    public String getRegion() {
        return properties.region();
    }

    public String getScriptUrl() {
        return UriComponentsBuilder.fromUriString(SCRIPT)
                .queryParam("key", properties.apiKey())
                .queryParam("libraries", "places")
                .queryParam("loading", "async")
                .queryParam("callback", CALLBACK)
                .queryParam("language", LocaleContextHolder.getLocale().getLanguage())
                .queryParam("region", properties.region())
                .encode()
                .toUriString();
    }
}

package org.shpytchuk.ingest.source.facebook;

import org.shpytchuk.ingest.exception.StageUnavailableException;

class FacebookActionRequiredException extends StageUnavailableException {

    static final String CHECKPOINT =
            "Facebook account needs verification (checkpoint): log in from a browser, then refresh FACEBOOK_COOKIES";
    static final String COOKIE_CONSENT = "Facebook wants a cookie choice for this account (privacy/consent): "
            + "make it in a browser that goes out through the server's network, then refresh FACEBOOK_COOKIES";

    FacebookActionRequiredException(String message) {
        super(message);
    }
}

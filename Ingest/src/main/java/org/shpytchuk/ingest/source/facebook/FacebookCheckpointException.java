package org.shpytchuk.ingest.source.facebook;

import org.shpytchuk.ingest.exception.StageUnavailableException;

class FacebookCheckpointException extends StageUnavailableException {

    FacebookCheckpointException() {
        super("Facebook account needs verification (checkpoint): log in from a browser, then refresh FACEBOOK_COOKIES");
    }
}

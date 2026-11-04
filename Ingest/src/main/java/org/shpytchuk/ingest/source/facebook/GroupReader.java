package org.shpytchuk.ingest.source.facebook;

import java.time.Instant;

interface GroupReader {

    GroupFeed read(String group, Instant since);
}

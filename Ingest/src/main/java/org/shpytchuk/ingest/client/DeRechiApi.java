package org.shpytchuk.ingest.client;

import java.util.Map;
import java.util.Set;

public interface DeRechiApi {

    Map<String, Long> categories();

    Set<String> countries();

    long create(NewItem item);
}

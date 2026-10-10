package org.shpytchuk.telegramingest.api;

import java.util.Map;
import java.util.Set;

public interface DeRechiApi {

    Map<String, Long> categories();

    Set<String> countries();

    long create(NewItem item);
}

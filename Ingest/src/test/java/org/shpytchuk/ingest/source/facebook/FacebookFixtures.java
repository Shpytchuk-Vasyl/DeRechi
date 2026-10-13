package org.shpytchuk.ingest.source.facebook;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

final class FacebookFixtures {

    static final URI FIRST_SCRIPT = URI.create("https://static.xx.fbcdn.net/rsrc.php/v4/yA/r/first.js?_nc_x=Ij3Wp8lg5Kz");
    static final URI SECOND_SCRIPT = URI.create("https://static.xx.fbcdn.net/rsrc.php/v4/yB/r/second.js?_nc_x=a&b=c");
    static final String DOC_ID = "40166931409560757";
    static final Instant NEWEST = Instant.ofEpochSecond(1791900000);
    static final Instant SECOND = Instant.ofEpochSecond(1791896400);
    static final Instant THIRD = Instant.ofEpochSecond(1791892800);
    static final Instant TWO_DAYS_OLD = Instant.ofEpochSecond(1791720000);

    private FacebookFixtures() {
    }

    static String page() {
        return read("group-page.html");
    }

    static String anonymousPage() {
        return page().replace("\"USER_ID\":\"100000000000001\"", "\"USER_ID\":\"0\"")
                .replace("[\"DTSGInitialData\",[],{\"token\":\"dtsg-token\"},258],", "");
    }

    static String feed() {
        return read("feed-page.txt");
    }

    static String lastPage() {
        return read("feed-last-page.txt");
    }

    static String feedQueryScript() {
        return read("feed-query-script.js");
    }

    private static String read(String name) {
        try (InputStream in = FacebookFixtures.class.getResourceAsStream("/facebook/" + name)) {
            if (in == null) {
                throw new IllegalStateException("No fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

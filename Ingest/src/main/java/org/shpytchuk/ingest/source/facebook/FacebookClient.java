package org.shpytchuk.ingest.source.facebook;

import org.shpytchuk.ingest.client.HttpCalls;
import org.shpytchuk.ingest.config.facebook.FacebookProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.function.Supplier;

public class FacebookClient {

    static final String ORIGIN = "https://www.facebook.com";

    private static final URI GRAPHQL = URI.create(ORIGIN + "/api/graphql/");

    private final RestClient restClient;

    public FacebookClient(FacebookProperties properties) {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        seed(cookies, properties.cookies());
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(cookies)
                .build());
        factory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "uk-UA,uk;q=0.9,en;q=0.8")
                .build();
    }

    public String page(URI uri) {
        return call("Facebook page", () -> restClient.get().uri(uri)
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .retrieve()
                .body(String.class));
    }

    public String script(URI uri) {
        return call("Facebook script", () -> restClient.get().uri(uri).retrieve().body(String.class));
    }

    public String graphQl(String query, Map<String, String> form, String lsd, URI referer) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        form.forEach(body::add);
        return call("Facebook " + query, () -> restClient.post().uri(GRAPHQL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header(HttpHeaders.ACCEPT, "*/*")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.REFERER, referer.toString())
                .header("X-FB-LSD", lsd)
                .header("X-FB-Friendly-Name", query)
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .body(body)
                .retrieve()
                .body(String.class));
    }

    public byte[] download(URI uri) {
        return call("Facebook photo", () -> restClient.get().uri(uri).retrieve().body(byte[].class));
    }

    private static void seed(CookieManager manager, String cookies) {
        for (String pair : cookies.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            HttpCookie cookie = new HttpCookie(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            cookie.setDomain(".facebook.com");
            cookie.setPath("/");
            cookie.setSecure(true);
            cookie.setVersion(0);
            manager.getCookieStore().add(URI.create(ORIGIN), cookie);
        }
    }

    private static <T> T call(String operation, Supplier<T> request) {
        T result;
        try {
            result = request.get();
        } catch (RestClientException e) {
            throw new IngestException(operation + " failed: " + HttpCalls.describe(e));
        }
        if (result == null) {
            throw new IngestException(operation + " returned an empty body");
        }
        return result;
    }
}

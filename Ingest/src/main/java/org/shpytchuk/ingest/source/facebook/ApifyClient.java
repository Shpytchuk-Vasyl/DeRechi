package org.shpytchuk.ingest.source.facebook;

import org.shpytchuk.ingest.client.HttpCalls;
import org.shpytchuk.ingest.config.facebook.ApifyProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

public class ApifyClient {

    private static final String RUN_SYNC = "/acts/{actor}/run-sync-get-dataset-items";
    private static final String CHRONOLOGICAL = "CHRONOLOGICAL";
    private static final DateTimeFormatter SINCE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final Duration RESPONSE_GRACE = Duration.ofMinutes(1);
    private static final Set<HttpStatus> REJECTED =
            Set.of(HttpStatus.UNAUTHORIZED, HttpStatus.PAYMENT_REQUIRED, HttpStatus.FORBIDDEN);
    private static final ParameterizedTypeReference<List<ApifyPost>> POSTS = new ParameterizedTypeReference<>() {
    };

    private final ApifyProperties properties;
    private final RestClient restClient;

    public ApifyClient(ApifyProperties properties) {
        this.properties = properties;
        this.restClient = HttpCalls.restClient(properties.timeout().plus(RESPONSE_GRACE))
                .baseUrl(properties.apiUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                .build();
    }

    public boolean configured() {
        return properties.configured();
    }

    List<ApifyPost> groupPosts(String group, Instant since) {
        ActorInput input = new ActorInput(List.of(new StartUrl(FacebookClient.ORIGIN + "/groups/" + group + "/")),
                properties.resultsLimit(), CHRONOLOGICAL, SINCE.format(since));
        List<ApifyPost> posts;
        try {
            posts = restClient.post()
                    .uri(uri -> uri.path(RUN_SYNC)
                            .queryParam("timeout", properties.timeout().toSeconds())
                            .queryParam("maxTotalChargeUsd", properties.maxChargeUsd().toPlainString())
                            .queryParam("clean", true)
                            .build(properties.facebookActor()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(input)
                    .retrieve()
                    .body(POSTS);
        } catch (RestClientResponseException e) {
            if (REJECTED.contains(e.getStatusCode())) {
                throw new StageUnavailableException("Apify rejected the run (token, plan or credit): "
                        + HttpCalls.describe(e));
            }
            throw failed(HttpCalls.describe(e));
        } catch (RestClientException e) {
            throw failed(HttpCalls.describe(e));
        }
        if (posts == null) {
            throw failed("an empty body");
        }
        return posts;
    }

    private IngestException failed(String reason) {
        return new IngestException("Apify run of " + properties.facebookActor() + " failed: " + reason);
    }

    private record ActorInput(List<StartUrl> startUrls, int resultsLimit, String viewOption, String onlyPostsNewerThan) {
    }

    private record StartUrl(String url) {
    }
}

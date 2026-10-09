package org.shpytchuk.clientapi.validator;

import com.sanctionco.jmail.JMail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Slf4j
public class DisposableEmailDomains {

    private final RestClient client;
    private final String url;
    private volatile Set<String> domains = Set.of();

    public DisposableEmailDomains(RestClient client, String url) {
        this.client = client;
        this.url = url;
    }

    @Scheduled(fixedDelayString = "${derechi.disposable-emails.refresh-interval}")
    public void refresh() {
        if (!StringUtils.hasText(url)) {
            return;
        }
        try {
            String body = client.get().uri(url).retrieve().body(String.class);
            Set<String> loaded = body == null ? Set.of() : body.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            if (loaded.isEmpty()) {
                log.warn("Disposable email list from {} is empty, keeping {} domains", url, domains.size());
                return;
            }
            domains = loaded;
            log.info("Loaded {} disposable email domains from {}", loaded.size(), url);
        } catch (RestClientException e) {
            log.warn("Could not load disposable email domains from {}, keeping {}: {}",
                    url, domains.size(), getMostSpecificCause(e).toString());
        }
    }

    public boolean isDisposable(String email) {
        return JMail.tryParse(email)
                .map(parsed -> isDisposableDomain(parsed.domain().toLowerCase(Locale.ROOT)))
                .orElse(false);
    }

    private boolean isDisposableDomain(String domain) {
        for (String candidate = domain; candidate.indexOf('.') > 0;
             candidate = candidate.substring(candidate.indexOf('.') + 1)) {
            if (domains.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}

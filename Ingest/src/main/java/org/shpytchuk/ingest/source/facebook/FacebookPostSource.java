package org.shpytchuk.ingest.source.facebook;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.ingest.config.facebook.ActiveHours;
import org.shpytchuk.ingest.config.facebook.FacebookProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;
import org.shpytchuk.ingest.source.PostKey;
import org.shpytchuk.ingest.source.PostSource;
import org.shpytchuk.ingest.source.SourceSettings;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

@Component
@Slf4j
public class FacebookPostSource implements PostSource {

    public static final String NAME = "facebook";

    private static final Duration FIRST_POLL = Duration.ofMinutes(1);
    private static final Duration OVERLAP = Duration.ofMinutes(30);

    private final FacebookProperties properties;
    private final FacebookClient client;
    private final FacebookGroupReader reader;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final MeterRegistry registry;
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final Map<String, Instant> since = new ConcurrentHashMap<>();
    private final AtomicInteger authorized = new AtomicInteger(1);
    private volatile Consumer<IncomingPost> sink = post -> {
    };

    public FacebookPostSource(FacebookProperties properties, FacebookClient client, JsonMapper json,
                              TaskScheduler scheduler, Clock clock, MeterRegistry registry) {
        this.properties = properties;
        this.client = client;
        this.scheduler = scheduler;
        this.clock = clock;
        this.registry = registry;
        this.reader = new FacebookGroupReader(client, json, properties.maxPages(), this::pause);
        Gauge.builder("derechi.ingest.facebook.authorized", authorized, AtomicInteger::get)
                .description("0 when the Facebook session was logged out on the last poll")
                .register(registry);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void start(Consumer<IncomingPost> sink) {
        if (!properties.configured()) {
            log.warn("Facebook is not configured (FACEBOOK_COOKIES, FACEBOOK_GROUPS), no groups will be read");
            return;
        }
        this.sink = sink;
        scheduleIn(FIRST_POLL);
        log.info("Facebook source started: {} groups every {} plus up to {}, active hours {}",
                properties.groupSettings().size(), properties.pollEvery(), properties.pollJitter(), properties.activeHours());
    }

    @Override
    public byte[] download(PhotoRef photo) {
        return client.download(URI.create(photo.id()));
    }

    @Override
    public Optional<String> authorPhone(IncomingPost post) {
        return Optional.empty();
    }

    void poll() {
        Instant next = clock.instant().plus(properties.pollEvery()).plus(randomUpTo(properties.pollJitter()));
        try {
            Map<String, SourceSettings> awake = awakeGroups();
            if (awake.isEmpty()) {
                next = nextOpening().plus(randomUpTo(properties.pollJitter()));
                log.debug("Facebook is not polled outside {}, next poll at {}", properties.activeHours(), next);
                return;
            }
            boolean first = true;
            for (Map.Entry<String, SourceSettings> group : awake.entrySet()) {
                if (!first) {
                    pause();
                }
                first = false;
                if (!read(group.getKey(), group.getValue())) {
                    break;
                }
            }
        } finally {
            scheduler.schedule(this::poll, next);
        }
    }

    private Map<String, SourceSettings> awakeGroups() {
        ActiveHours hours = properties.hours();
        Map<String, SourceSettings> awake = new LinkedHashMap<>();
        properties.groupSettings().forEach((group, settings) -> {
            if (hours.contains(LocalTime.ofInstant(clock.instant(), settings.zone()))) {
                awake.put(group, settings);
            }
        });
        return awake;
    }

    private Instant nextOpening() {
        ActiveHours hours = properties.hours();
        return properties.groupSettings().values().stream()
                .map(settings -> hours.nextOpening(clock.instant().atZone(settings.zone())).toInstant())
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    private boolean read(String group, SourceSettings settings) {
        Instant started = clock.instant();
        Instant from = since.getOrDefault(group, started.minus(properties.catchUpMaxAge()));
        try {
            FacebookGroupReader.GroupFeed feed = reader.read(group, from);
            authorized.set(1);
            List<FacebookStory> fresh = feed.stories().stream()
                    .filter(story -> !story.createdAt().isBefore(from))
                    .sorted(Comparator.comparing(FacebookStory::createdAt))
                    .toList();
            fresh.forEach(story -> sink.accept(post(group, feed.name(), settings, story)));
            since.put(group, started.minus(OVERLAP));
            polled(group, "ok");
            log.info("Facebook group {} read: {} posts since {}", group, fresh.size(), from);
        } catch (FacebookCheckpointException e) {
            authorized.set(0);
            polled(group, "checkpoint");
            log.warn("Facebook group {} was not read, the other groups wait for the next poll: {}", group,
                    e.getMessage());
            return false;
        } catch (StageUnavailableException e) {
            authorized.set(0);
            polled(group, "unauthorized");
            log.warn("Facebook group {} was not read: {}", group, e.getMessage());
        } catch (IngestException e) {
            polled(group, "failed");
            log.warn("Facebook group {} was not read: {}", group, e.getMessage());
        }
        return true;
    }

    private static IncomingPost post(String group, String name, SourceSettings settings, FacebookStory story) {
        String link = FacebookClient.ORIGIN + "/groups/" + group + "/permalink/" + story.postId() + "/";
        return new IncomingPost(new PostKey(NAME, group, story.postId()), name, link, story.createdAt(), story.text(),
                story.photos(), settings, null);
    }

    private void polled(String group, String outcome) {
        registry.counter("derechi.ingest.facebook.polls", "group", group, "outcome", outcome).increment();
    }

    private void scheduleIn(Duration delay) {
        scheduler.schedule(this::poll, clock.instant().plus(delay));
    }

    private void pause() {
        Duration base = properties.requestPause();
        Duration pause = base.dividedBy(2).plus(randomUpTo(base));
        try {
            Thread.sleep(pause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestException("Facebook polling was interrupted");
        }
    }

    private Duration randomUpTo(Duration bound) {
        return bound.isZero() ? Duration.ZERO : Duration.ofMillis(random.nextLong(bound.toMillis() + 1));
    }
}

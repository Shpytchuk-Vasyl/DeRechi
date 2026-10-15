package org.shpytchuk.ingest.config.events;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Duration;

@ConfigurationProperties(prefix = "derechi.events")
public record EventsProperties(String queue, Duration inFlightWait, Duration inFlightPoll) {

    public EventsProperties {
        Assert.hasText(queue, "derechi.events.queue must not be blank");
        inFlightWait = inFlightWait == null ? Duration.ofSeconds(90) : inFlightWait;
        inFlightPoll = inFlightPoll == null || !inFlightPoll.isPositive() ? Duration.ofSeconds(1) : inFlightPoll;
    }
}

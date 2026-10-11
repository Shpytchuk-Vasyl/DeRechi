package org.shpytchuk.telegramingest.llm;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
public class DailyCallCounter {

    private final Clock clock;
    private LocalDate day;
    private int count;

    public DailyCallCounter(Clock clock) {
        this.clock = clock;
        this.day = today();
    }

    public synchronized boolean tryAcquire(int limit) {
        LocalDate now = today();
        if (!now.equals(day)) {
            day = now;
            count = 0;
        }
        if (count >= limit) {
            return false;
        }
        count++;
        return true;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}

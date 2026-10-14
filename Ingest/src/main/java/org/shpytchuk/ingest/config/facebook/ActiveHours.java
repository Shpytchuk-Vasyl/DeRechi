package org.shpytchuk.ingest.config.facebook;

import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ActiveHours(LocalTime from, LocalTime to) {

    public static final ActiveHours ALWAYS = new ActiveHours(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT);

    private static final Pattern RANGE = Pattern.compile("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

    public static ActiveHours parse(String property, String spec) {
        if (!StringUtils.hasText(spec)) {
            return ALWAYS;
        }
        Matcher range = RANGE.matcher(spec.strip());
        if (!range.matches()) {
            throw new IllegalArgumentException(property + " must look like 07:00-22:00, got " + spec);
        }
        try {
            return new ActiveHours(LocalTime.parse(range.group(1), TIME), LocalTime.parse(range.group(2), TIME));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(property + " has a time that does not exist: " + spec, e);
        }
    }

    public boolean always() {
        return from.equals(to);
    }

    public boolean contains(LocalTime time) {
        if (always()) {
            return true;
        }
        if (from.isBefore(to)) {
            return !time.isBefore(from) && time.isBefore(to);
        }
        return !time.isBefore(from) || time.isBefore(to);
    }

    public ZonedDateTime nextOpening(ZonedDateTime now) {
        ZonedDateTime today = now.toLocalDate().atTime(from).atZone(now.getZone());
        return today.isAfter(now) ? today : today.plusDays(1);
    }
}

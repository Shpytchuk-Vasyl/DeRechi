package org.shpytchuk.ingest.pipeline;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.api.ai.model.Money;
import org.shpytchuk.ingest.api.ai.model.SocialMedia;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Notices {

    public static final int TITLE_MAX = 100;
    public static final int DESCRIPTION_MAX = 250;

    private Notices() {
    }

    public static @Nullable String cut(@Nullable String text, int max) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.strip().replaceAll("\\s+", " ");
        if (trimmed.length() <= max) {
            return trimmed;
        }
        int end = trimmed.lastIndexOf(' ', max - 1);
        if (end < max / 2) {
            end = max - 1;
        }
        return trimmed.substring(0, end).stripTrailing() + "…";
    }

    public static @Nullable LocalDate date(@Nullable LocalDate extracted, LocalDate postDate, LocalDate today, Period maxAge) {
        LocalDate date = extracted == null ? postDate : extracted;
        if (date.isAfter(today)) {
            date = today;
        }
        return date.isBefore(today.minus(maxAge)) ? null : date;
    }

    public static @Nullable Money compensation(@Nullable Money money) {
        if (money == null || money.amount() <= 0) {
            return null;
        }
        String currency = money.currency() == null || money.currency().isBlank() ? null : money.currency().trim().toUpperCase();
        return new Money(money.amount(), currency != null && currency.matches("[A-Z]{3}") ? currency : null);
    }

    public static List<SocialMedia> socialMedias(@Nullable List<SocialMedia> extracted, boolean phoneFromTelegram) {
        Set<SocialMedia> result = new LinkedHashSet<>(extracted == null ? List.of() : extracted);
        result.remove(null);
        if (phoneFromTelegram) {
            result.add(SocialMedia.TELEGRAM);
        }
        return new ArrayList<>(result);
    }
}

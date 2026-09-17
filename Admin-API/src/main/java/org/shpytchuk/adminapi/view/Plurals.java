package org.shpytchuk.adminapi.view;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Числові форми за правилами мови. Англійська й німецька розрізняють дві форми,
 * французька теж дві (але нуль у неї однина), українська й польська — три.
 *
 * <p>Ключ у бандлі доповнюється категорією: {@code list.records.one},
 * {@code list.records.few}, {@code list.records.many}, {@code list.records.other}.
 * Мовам із двома формами достатньо {@code .one} і {@code .other}.
 */
@Component("plural")
public class Plurals {

    /** Мови зі слов'янськими правилами: одна/кілька/багато. */
    private static final Set<String> THREE_FORM_LANGUAGES = Set.of("uk", "pl", "ru", "be");

    private final MessageSource messages;

    public Plurals(MessageSource messages) {
        this.messages = messages;
    }

    public String records(long count) {
        return format("list.records", count);
    }

    public String format(String key, long count) {
        Locale locale = LocaleContextHolder.getLocale();
        Object[] args = {count};

        return messages.getMessage(key + "." + category(locale, count), args,
                messages.getMessage(key + ".other", args, locale), locale);
    }

    private static String category(Locale locale, long count) {
        long absolute = Math.abs(count);

        if (THREE_FORM_LANGUAGES.contains(locale.getLanguage())) {
            long lastTwo = absolute % 100;
            long last = absolute % 10;

            if (lastTwo >= 11 && lastTwo <= 14) {
                return "many";
            }
            if (last == 1) {
                return "one";
            }
            return last >= 2 && last <= 4 ? "few" : "many";
        }

        // Французька рахує нуль як однину, решта — лише одиницю.
        if ("fr".equals(locale.getLanguage())) {
            return absolute <= 1 ? "one" : "other";
        }
        return absolute == 1 ? "one" : "other";
    }
}

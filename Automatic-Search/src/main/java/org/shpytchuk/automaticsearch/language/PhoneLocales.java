package org.shpytchuk.automaticsearch.language;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.util.Locale;

public final class PhoneLocales {

    private static final PhoneNumberUtil PHONE_NUMBERS = PhoneNumberUtil.getInstance();

    private static final Locale UKRAINIAN = Locale.of("uk");
    private static final Locale POLISH = Locale.of("pl");

    private PhoneLocales() {
    }

    public static Locale of(String phone) {
        if (phone == null || phone.isBlank()) {
            return Locale.ENGLISH;
        }
        try {
            String region = PHONE_NUMBERS.getRegionCodeForNumber(PHONE_NUMBERS.parse(phone, null));
            return region == null ? Locale.ENGLISH : byRegion(region);
        } catch (NumberParseException unparseable) {
            return Locale.ENGLISH;
        }
    }

    private static Locale byRegion(String region) {
        return switch (region) {
            case "UA" -> UKRAINIAN;
            case "PL" -> POLISH;
            case "DE", "AT", "CH" -> Locale.GERMAN;
            case "FR", "BE", "LU", "MC" -> Locale.FRENCH;
            default -> Locale.ENGLISH;
        };
    }
}

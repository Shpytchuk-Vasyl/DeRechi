package org.shpytchuk.adminapi.view;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.Locale;

@Component("fmt")
public class Formats {

    private final PhoneNumberUtil phoneNumbers = PhoneNumberUtil.getInstance();

    public String date(LocalDate date) {
        if (date == null) {
            return "";
        }
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale())
                .format(date);
    }

    public String dateTime(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale())
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    public String money(Number amount, String currency) {
        if (amount == null) {
            return "";
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(locale());
        if (StringUtils.hasText(currency)) {
            format.setCurrency(Currency.getInstance(currency.trim().toUpperCase(Locale.ROOT)));
        }
        format.setMaximumFractionDigits(0);
        return format.format(amount);
    }

    public String country(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        return Locale.of("", code.trim().toUpperCase(Locale.ROOT)).getDisplayCountry(locale());
    }

    public String phone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "";
        }
        try {
            return phoneNumbers.format(phoneNumbers.parse(phone, null), PhoneNumberFormat.INTERNATIONAL);
        } catch (NumberParseException unparseable) {
            return phone;
        }
    }

    private static Locale locale() {
        return LocaleContextHolder.getLocale();
    }
}

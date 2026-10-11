package org.shpytchuk.telegramingest.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.Leniency;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Component
public class PhoneExtractor {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    public List<FoundPhone> find(String text, String region) {
        List<FoundPhone> found = new ArrayList<>();
        for (PhoneNumberMatch match : UTIL.findNumbers(text, region, Leniency.VALID, Long.MAX_VALUE)) {
            found.add(new FoundPhone(match.rawString(), match.start(), match.end(), e164(match.number())));
        }
        return found;
    }

    public Optional<String> profilePhone(String digits) {
        try {
            PhoneNumber number = UTIL.parse("+" + digits, null);
            return UTIL.isValidNumber(number) ? Optional.of(e164(number)) : Optional.empty();
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }

    private static String e164(PhoneNumber number) {
        return UTIL.format(number, PhoneNumberFormat.E164);
    }
}

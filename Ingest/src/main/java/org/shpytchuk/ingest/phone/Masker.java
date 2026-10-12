package org.shpytchuk.ingest.phone;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class Masker {

    private static final Pattern EMAIL = Pattern.compile("[\\p{L}0-9._%+-]+@[\\p{L}0-9.-]+\\.[\\p{L}]{2,}");

    public MaskedText mask(String text, List<FoundPhone> phones) {
        Map<String, String> refByNumber = new HashMap<>();
        Map<String, String> numberByRef = new HashMap<>();
        StringBuilder masked = new StringBuilder(text.length());
        int position = 0;
        for (FoundPhone phone : phones) {
            String ref = refByNumber.computeIfAbsent(phone.e164(), number -> "PHONE_" + (refByNumber.size() + 1));
            numberByRef.put(ref, phone.e164());
            masked.append(text, position, phone.start()).append('[').append(ref).append(']');
            position = phone.end();
        }
        masked.append(text, position, text.length());
        return new MaskedText(maskEmails(masked.toString()), Map.copyOf(numberByRef));
    }

    private static String maskEmails(String text) {
        Map<String, String> refs = new HashMap<>();
        return EMAIL.matcher(text).replaceAll(match -> refs.computeIfAbsent(match.group().toLowerCase(Locale.ROOT),
                email -> "[EMAIL_" + (refs.size() + 1) + "]"));
    }
}

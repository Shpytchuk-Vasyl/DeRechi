package org.shpytchuk.clientapi.language;

import com.google.common.base.Optional;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObjectFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LanguageResolver {

    private final LanguageDetector detector;
    private final TextObjectFactory textObjectFactory;

    public LanguageResolver() {
        this.detector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(readProfiles())
                .build();
        this.textObjectFactory = CommonTextObjectFactories.forDetectingShortCleanText();
    }

    public SearchLanguage resolve(String... texts) {
        String text = Stream.of(texts)
                .filter(StringUtils::hasText)
                .map(String::strip)
                .collect(Collectors.joining(" "));
        if (text.isEmpty()) {
            return SearchLanguage.SIMPLE;
        }

        Optional<LdLocale> detected = detector.detect(textObjectFactory.forText(text));
        return detected.isPresent()
                ? SearchLanguage.fromIsoCode(detected.get().getLanguage())
                : SearchLanguage.SIMPLE;
    }

    private static List<LanguageProfile> readProfiles() {
        List<LdLocale> locales = SearchLanguage.detectable().stream()
                .map(language -> LdLocale.fromString(language.isoCode()))
                .toList();
        try {
            return new LanguageProfileReader().readBuiltIn(locales);
        } catch (IOException e) {
            throw new IllegalStateException("Can't read language profile", e);
        }
    }
}

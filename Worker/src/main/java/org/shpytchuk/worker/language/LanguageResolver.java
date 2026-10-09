package org.shpytchuk.worker.language;

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
import java.util.ArrayList;
import java.util.List;

@Component
public class LanguageResolver {

    private static final List<String> DETECT_ONLY_CODES = List.of();

    private final LanguageDetector detector;
    private final TextObjectFactory textObjectFactory;

    public LanguageResolver() {
        this.detector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(readProfiles())
                .build();
        this.textObjectFactory = CommonTextObjectFactories.forDetectingShortCleanText();
    }

    public SearchLanguage resolve(String text) {
        if (!StringUtils.hasText(text)) {
            return SearchLanguage.SIMPLE;
        }

        Optional<LdLocale> detected = detector.detect(textObjectFactory.forText(text));
        return detected.isPresent()
                ? SearchLanguage.fromIsoCode(detected.get().getLanguage())
                : SearchLanguage.SIMPLE;
    }

    private static List<LanguageProfile> readProfiles() {
        List<String> codes = new ArrayList<>(SearchLanguage.isoCodes());
        codes.addAll(DETECT_ONLY_CODES);

        try {
            return new LanguageProfileReader().readBuiltIn(codes.stream().map(LdLocale::fromString).toList());
        } catch (IOException e) {
            throw new IllegalStateException("Can't read language profile", e);
        }
    }
}

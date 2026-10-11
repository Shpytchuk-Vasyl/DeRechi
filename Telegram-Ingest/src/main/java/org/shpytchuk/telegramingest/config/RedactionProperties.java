package org.shpytchuk.telegramingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Photo redaction.
 *
 * @param tessdata         directory with {@code *.traineddata}; empty turns OCR off (development only:
 *                         without OCR nothing is blurred except documents named in the text)
 * @param languages        Tesseract languages, {@code ukr+pol+eng}
 * @param minWords         a document photo with fewer recognized words is blurred entirely
 * @param documentKeywords words that mark a document, in the post text or on the photo (case-insensitive)
 * @param faceModel        YuNet ONNX model, a Spring resource location
 * @param faceScore        minimum face score
 * @param documentMode     {@code PARTIAL}: blur text and faces of a document; {@code FULL}: blur every document photo entirely
 */
@ConfigurationProperties(prefix = "derechi.redaction")
public record RedactionProperties(
        String tessdata,
        String languages,
        int minWords,
        List<String> documentKeywords,
        String faceModel,
        double faceScore,
        DocumentMode documentMode) {

    public RedactionProperties {
        tessdata = tessdata == null ? "" : tessdata.trim();
        languages = StringUtils.hasText(languages) ? languages.trim() : "ukr+pol+eng";
        minWords = minWords <= 0 ? 6 : minWords;
        documentKeywords = documentKeywords == null ? List.of() : documentKeywords.stream()
                .filter(StringUtils::hasText).map(String::trim).toList();
        faceModel = StringUtils.hasText(faceModel) ? faceModel.trim() : "classpath:models/face_detection_yunet_2023mar.onnx";
        faceScore = faceScore <= 0 ? 0.6 : faceScore;
        documentMode = documentMode == null ? DocumentMode.PARTIAL : documentMode;
    }

    public boolean ocrEnabled() {
        return !tessdata.isEmpty();
    }

    public enum DocumentMode {
        PARTIAL,
        FULL
    }
}

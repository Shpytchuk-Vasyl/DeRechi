package org.shpytchuk.ingest.config.redaction;

import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.ingest.image.ocr.OcrEngine;
import org.shpytchuk.ingest.image.ocr.TesseractOcrEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** OCR for {@code DefaultImageRedactor}: Tesseract, or nothing when {@code TESSDATA_PREFIX} is empty. */
@Configuration
@Slf4j
public class RedactionConfig {

    @Bean
    public OcrEngine ocrEngine(RedactionProperties properties) {
        if (properties.ocrEnabled()) {
            return new TesseractOcrEngine(properties);
        }
        log.warn("OCR is off: TESSDATA_PREFIX is empty; photos are not checked for phone numbers");
        return image -> List.of();
    }
}

package org.shpytchuk.ingest.image.redaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.shpytchuk.ingest.config.redaction.RedactionConfig;
import org.shpytchuk.ingest.config.redaction.RedactionProperties;
import org.shpytchuk.ingest.config.telegram.TelegramProperties;
import org.shpytchuk.ingest.image.RedactedImage;
import org.shpytchuk.ingest.image.RedactionMode;
import org.shpytchuk.ingest.image.face.FaceDetector;
import org.shpytchuk.ingest.image.face.YuNetFaceDetector;
import org.shpytchuk.ingest.image.ocr.OcrEngine;
import org.shpytchuk.ingest.image.ocr.OcrLine;
import org.shpytchuk.ingest.phone.PhoneExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.util.HtmlUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@SpringJUnitConfig(classes = RedactionReportIT.Context.class, initializers = ConfigDataApplicationContextInitializer.class)
@EnabledIfEnvironmentVariable(named = "TESSDATA_PREFIX", matches = ".+")
class RedactionReportIT {

    private static final Color OCR_LINE = new Color(0x1E88E5);
    private static final Color CONTACT_LINE = new Color(0xE53935);
    private static final Color FACE = new Color(0xD81B60);

    @Autowired
    DefaultImageRedactor redactor;

    @Autowired
    OcrEngine ocr;

    @Autowired
    FaceDetector faceDetector;

    @Autowired
    DocumentDetector documentDetector;

    @Test
    void writesAReportAPersonCanJudge() throws IOException {
        Path tests = testsDirectory();
        Path input = tests.resolve("redaction").resolve("input");
        Path output = tests.resolve("reports").resolve("redaction");
        List<Path> images = images(input);
        assumeFalse(images.isEmpty(), "no images in " + input);

        FileSystemUtils.deleteRecursively(output);
        Files.createDirectories(output);
        List<Row> rows = new ArrayList<>();
        for (Path image : images) {
            rows.add(process(image, output));
        }
        Files.writeString(output.resolve("index.html"), html(rows), StandardCharsets.UTF_8);

        assertThat(rows).hasSize(images.size());
        System.out.println("Redaction report: " + output.resolve("index.html").toUri());
    }

    private Row process(Path image, Path output) throws IOException {
        byte[] bytes = Files.readAllBytes(image);
        String name = image.getFileName().toString();
        String base = name.substring(0, name.lastIndexOf('.'));

        long started = System.nanoTime();
        RedactedImage redacted = redactor.redact(bytes, false);
        long millis = (System.nanoTime() - started) / 1_000_000;

        BufferedImage picture = DefaultImageRedactor.decode(bytes);
        List<OcrLine> lines = ocr.read(picture).lines();
        List<Rectangle> faces = faceDetector.faces(picture);
        List<Boolean> contacts = lines.stream().map(redactor::hasContact).toList();
        boolean document = documentDetector.looksLikeDocument(lines)
                || documentDetector.looksLikePortraitDocument(faces, lines, picture.getWidth());

        List<String> documentText = PhotoText.of(lines);

        Files.write(output.resolve(base + ".redacted.jpg"), redacted.jpeg());
        ImageIO.write(debug(picture, lines, contacts, faces), "png", output.resolve(base + ".debug.png").toFile());
        return new Row(name, base, redacted.mode(), document, lines, contacts, faces.size(), picture.getWidth(),
                picture.getHeight(), millis, documentText);
    }

    private static BufferedImage debug(BufferedImage picture, List<OcrLine> lines, List<Boolean> contacts,
                                       List<Rectangle> faces) {
        BufferedImage copy = new BufferedImage(picture.getWidth(), picture.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(picture, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int stroke = Math.max(2, Math.max(picture.getWidth(), picture.getHeight()) / 400);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, stroke * 6)));
        for (int i = 0; i < lines.size(); i++) {
            OcrLine line = lines.get(i);
            Color color = contacts.get(i) ? CONTACT_LINE : OCR_LINE;
            label(g, line.box(), color, stroke, "%d%%".formatted(Math.round(line.confidence() * 100)));
        }
        faces.forEach(face -> label(g, face, FACE, stroke * 2, "face"));
        g.dispose();
        return copy;
    }

    private static void label(Graphics2D g, Rectangle box, Color color, int stroke, String text) {
        g.setColor(color);
        g.setStroke(new BasicStroke(stroke));
        g.draw(box);
        int width = g.getFontMetrics().stringWidth(text) + 6;
        int height = g.getFontMetrics().getHeight();
        int y = Math.max(0, box.y - height);
        g.fillRect(box.x, y, width, height);
        g.setColor(Color.WHITE);
        g.drawString(text, box.x + 3, y + g.getFontMetrics().getAscent());
    }

    private static String html(List<Row> rows) {
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><title>Redaction report</title>
                <style>
                body { font: 14px/1.4 system-ui, sans-serif; margin: 24px; color: #222; }
                table { border-collapse: collapse; margin-bottom: 24px; }
                td, th { border: 1px solid #ccc; padding: 4px 8px; text-align: left; vertical-align: top; }
                section { border-top: 2px solid #999; padding-top: 12px; margin-top: 24px; }
                .images { display: flex; gap: 8px; }
                .images figure { margin: 0; flex: 1; }
                .images img { width: 100%; border: 1px solid #ccc; }
                .legend span { padding: 0 6px; color: #fff; margin-right: 8px; }
                .mode { font-weight: bold; }
                </style></head><body>
                <h1>Redaction report</h1>
                <p class="legend"><span style="background:#1E88E5">OCR line + confidence</span>
                <span style="background:#E53935">line with a phone or email</span>
                <span style="background:#D81B60">face</span></p>
                <p>Every photo went through the real Tesseract and YuNet, the way the service redacts it
                (the post text is not used: a document is recognized by OCR or by a portrait on the left).</p>
                <table><tr><th>File</th><th>Mode</th><th>Document</th><th>OCR lines</th>
                <th>Contact lines</th><th>Faces</th><th>Size</th><th>Time</th></tr>
                """);
        for (Row row : rows) {
            html.append("<tr><td><a href=\"#").append(row.base()).append("\">").append(escape(row.name()))
                    .append("</a></td><td class=\"mode\">").append(row.mode())
                    .append("</td><td>").append(row.document() ? "yes" : "no")
                    .append("</td><td>").append(row.lines().size())
                    .append("</td><td>").append(row.contacts().stream().filter(Boolean::booleanValue).count())
                    .append("</td><td>").append(row.faces())
                    .append("</td><td>").append(row.width()).append("×").append(row.height())
                    .append("</td><td>").append(row.millis()).append(" ms</td></tr>\n");
        }
        html.append("</table>\n");
        for (Row row : rows) {
            html.append("<section id=\"").append(row.base()).append("\"><h2>").append(escape(row.name()))
                    .append(" <small class=\"mode\">").append(row.mode()).append("</small></h2>\n")
                    .append("<div class=\"images\">")
                    .append(figure("../../redaction/input/" + row.name(), "Original"))
                    .append(figure(row.base() + ".debug.png", "Detected"))
                    .append(figure(row.base() + ".redacted.jpg", "Redacted"))
                    .append("</div>\n<details><summary>OCR text (").append(row.lines().size())
                    .append(" lines)</summary><table><tr><th>Confidence</th><th>Contact</th><th>Text</th></tr>\n");
            for (int i = 0; i < row.lines().size(); i++) {
                OcrLine line = row.lines().get(i);
                html.append("<tr><td>").append(Math.round(line.confidence() * 100)).append("%</td><td>")
                        .append(row.contacts().get(i) ? "yes" : "").append("</td><td>")
                        .append(escape(line.text())).append("</td></tr>\n");
            }
            html.append("</table></details>\n<details><summary>Document text for the owner's name (")
                    .append(row.documentText().size()).append(" lines)</summary><ol>\n");
            for (String line : row.documentText()) {
                html.append("<li>").append(escape(line)).append("</li>\n");
            }
            html.append("</ol></details></section>\n");
        }
        return html.append("</body></html>\n").toString();
    }

    private static String figure(String src, String caption) {
        return "<figure><a href=\"%s\"><img src=\"%s\" alt=\"%s\"></a><figcaption>%s</figcaption></figure>"
                .formatted(src, src, caption, caption);
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
    }

    private static List<Path> images(Path input) throws IOException {
        if (!Files.isDirectory(input)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(input)) {
            return files.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(jpe?g|png)"))
                    .sorted()
                    .toList();
        }
    }

    private static Path testsDirectory() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path tests = directory.resolve("tests");
            if (Files.isDirectory(tests.resolve("redaction"))) {
                return tests;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("tests/redaction not found above " + Path.of("").toAbsolutePath());
    }

    private record Row(String name, String base, RedactionMode mode, boolean document, List<OcrLine> lines,
                       List<Boolean> contacts, int faces, int width, int height, long millis, List<String> documentText) {
    }

    @Configuration
    @EnableConfigurationProperties({RedactionProperties.class, TelegramProperties.class})
    @Import({RedactionConfig.class, DefaultImageRedactor.class, DocumentDetector.class, PhoneExtractor.class,
            YuNetFaceDetector.class})
    static class Context {
    }
}

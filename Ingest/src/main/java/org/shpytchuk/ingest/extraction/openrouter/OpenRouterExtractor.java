package org.shpytchuk.ingest.extraction.openrouter;

import lombok.AllArgsConstructor;
import org.shpytchuk.ingest.extraction.Extraction;
import org.shpytchuk.ingest.extraction.ExtractionRequest;
import org.shpytchuk.ingest.extraction.ExtractionResult;
import org.shpytchuk.ingest.extraction.Extractor;
import org.shpytchuk.ingest.extraction.PromptBuilder;
import org.shpytchuk.ingest.extraction.openrouter.CompletionRequest.ImagePart;
import org.shpytchuk.ingest.extraction.openrouter.CompletionRequest.Part;
import org.shpytchuk.ingest.extraction.openrouter.CompletionRequest.TextPart;
import org.shpytchuk.ingest.extraction.openrouter.OpenRouterClient.Answer;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class OpenRouterExtractor implements Extractor {

    private static final String SCHEMA_NAME = "notice";

    private final OpenRouterClient client;
    private final PromptBuilder prompts;

    @Override
    public ExtractionResult extract(ExtractionRequest request) {
        List<Part> content = new ArrayList<>();
        content.add(new TextPart(prompts.userText(request)));
        request.photos().forEach(photo -> content.add(ImagePart.jpeg(photo)));
        Answer<Extraction> answer = client.complete(prompts.systemPrompt(), content, SCHEMA_NAME,
                prompts.schema(request.categories()), Extraction.class);
        return new ExtractionResult(answer.content(), answer.model(), answer.cost(), answer.duration());
    }
}

package org.shpytchuk.ingest.extraction.openrouter;

import lombok.AllArgsConstructor;
import org.shpytchuk.ingest.extraction.OwnerName;
import org.shpytchuk.ingest.extraction.OwnerNameFinder;
import org.shpytchuk.ingest.extraction.OwnerNameRequest;
import org.shpytchuk.ingest.extraction.OwnerNameResult;
import org.shpytchuk.ingest.extraction.PromptBuilder;
import org.shpytchuk.ingest.extraction.openrouter.CompletionRequest.TextPart;
import org.shpytchuk.ingest.extraction.openrouter.OpenRouterClient.Answer;

import java.util.List;

@AllArgsConstructor
public class OpenRouterOwnerNameFinder implements OwnerNameFinder {

    private static final String SCHEMA_NAME = "owner_name";

    private final OpenRouterClient client;
    private final PromptBuilder prompts;

    @Override
    public OwnerNameResult find(OwnerNameRequest request) {
        Answer<OwnerName> answer = client.complete(prompts.ownerNameSystemPrompt(),
                List.of(new TextPart(prompts.ownerNameUserText(request))), SCHEMA_NAME, prompts.ownerNameSchema(),
                OwnerName.class);
        return new OwnerNameResult(answer.content(), answer.model(), answer.cost(), answer.duration());
    }
}

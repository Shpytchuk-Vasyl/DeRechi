package org.shpytchuk.telegramingest.llm;

import org.shpytchuk.telegramingest.exception.BudgetExceededException;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.exception.StageUnavailableException;

public interface Extractor {

    /**
     * Retries transient failures itself.
     *
     * @throws StageUnavailableException no API key configured
     * @throws BudgetExceededException   the daily limit of calls is used up
     * @throws IngestException           retries exhausted or an answer that never validates
     */
    ExtractionResult extract(ExtractionRequest request);
}

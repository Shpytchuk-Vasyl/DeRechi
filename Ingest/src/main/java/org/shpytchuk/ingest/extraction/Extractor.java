package org.shpytchuk.ingest.extraction;

import org.shpytchuk.ingest.exception.BudgetExceededException;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;

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

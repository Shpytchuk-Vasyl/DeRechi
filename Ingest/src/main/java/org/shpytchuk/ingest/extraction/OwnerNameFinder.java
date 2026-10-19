package org.shpytchuk.ingest.extraction;

import org.shpytchuk.ingest.exception.BudgetExceededException;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;

public interface OwnerNameFinder {

    /**
     * Asks the model whether the lines read on a document photo name its owner.
     *
     * @throws StageUnavailableException no API key configured, or retries of 429/502/503 or a refused connection exhausted
     * @throws BudgetExceededException   the daily limit of calls is used up
     * @throws IngestException           retries exhausted or an answer that never validates
     */
    OwnerNameResult find(OwnerNameRequest request);
}

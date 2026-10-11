package org.shpytchuk.telegramingest.channel;

import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.exception.StageUnavailableException;

public interface ChannelPublisher {

    /**
     *
     * @throws StageUnavailableException bot token or channel not configured
     * @throws IngestException           retries exhausted
     */
    long publish(ChannelPost post);

    void preview(Preview preview);
}

package org.shpytchuk.ingest.publish;

public interface Publisher {

    String name();

    boolean needsSiteItem();

    void publish(Publication publication);

    default void skipped(SkippedPost post) {
    }
}

package org.shpytchuk.ingest.publish;

public interface Publisher {

    String name();

    boolean needsSiteItem();

    void publish(Publication publication);
}

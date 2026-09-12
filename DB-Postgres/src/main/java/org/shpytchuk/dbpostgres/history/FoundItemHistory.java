package org.shpytchuk.dbpostgres.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.shpytchuk.dbpostgres.core.thing.Thing;

import java.time.Instant;

@Entity
public class FoundItemHistory extends Thing {

    @Column(nullable = false)
    private Instant archivedAt;
}

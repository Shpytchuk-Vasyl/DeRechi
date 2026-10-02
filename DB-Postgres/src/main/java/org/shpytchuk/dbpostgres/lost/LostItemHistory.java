package org.shpytchuk.dbpostgres.lost;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.shpytchuk.dbpostgres.thing.Thing;

import java.time.Instant;

@Entity
public class LostItemHistory extends Thing {

    @Column(nullable = false)
    private Instant archivedAt;
}

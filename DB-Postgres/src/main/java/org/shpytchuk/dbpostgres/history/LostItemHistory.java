package org.shpytchuk.dbpostgres.history;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.dbpostgres.core.thing.Thing;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class LostItemHistory extends Thing {

    @Size(max = 200)
    @Column(length = 200)
    private String image;

    @Column(nullable = false)
    private Instant archivedAt;
}

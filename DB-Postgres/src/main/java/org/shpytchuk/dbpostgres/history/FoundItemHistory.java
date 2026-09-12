package org.shpytchuk.dbpostgres.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotBlank;
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
public class FoundItemHistory extends Thing {

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String image;


    @Column(nullable = false)
    private Instant archivedAt;
}

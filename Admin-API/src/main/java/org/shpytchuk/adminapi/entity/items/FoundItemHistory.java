package org.shpytchuk.adminapi.entity.items;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.adminapi.entity.Thing;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class FoundItemHistory extends Thing {

    @Column(nullable = false)
    private Instant archivedAt;
}

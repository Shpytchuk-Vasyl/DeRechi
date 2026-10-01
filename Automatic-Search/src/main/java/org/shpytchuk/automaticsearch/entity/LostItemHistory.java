package org.shpytchuk.automaticsearch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class LostItemHistory extends Thing {

    @Column(nullable = false)
    private Instant archivedAt;
}

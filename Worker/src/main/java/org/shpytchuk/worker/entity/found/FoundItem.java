package org.shpytchuk.worker.entity.found;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.worker.entity.thing.Thing;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class FoundItem extends Thing {
}

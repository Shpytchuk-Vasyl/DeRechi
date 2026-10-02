package org.shpytchuk.clientapi.entity.found;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.clientapi.entity.thing.Thing;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class FoundItem extends Thing {
}

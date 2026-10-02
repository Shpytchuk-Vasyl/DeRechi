package org.shpytchuk.adminapi.entity.lost;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.adminapi.entity.thing.Thing;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class LostItem extends Thing {
}

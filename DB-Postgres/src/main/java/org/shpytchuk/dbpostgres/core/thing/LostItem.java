package org.shpytchuk.dbpostgres.core.thing;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class LostItem extends Thing {

    @Size(max = 200)
    @Column(length = 200)
    private String image;
}

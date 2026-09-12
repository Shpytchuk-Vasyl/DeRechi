package org.shpytchuk.dbpostgres.core.thing;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class FoundItem extends Thing {

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String image;
}

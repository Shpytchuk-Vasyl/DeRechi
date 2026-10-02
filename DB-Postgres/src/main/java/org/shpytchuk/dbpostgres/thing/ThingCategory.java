package org.shpytchuk.dbpostgres.thing;

import jakarta.persistence.*;

@Entity
public class ThingCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String key;
}

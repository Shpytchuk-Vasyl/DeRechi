package org.shpytchuk.dbpostgres.core.thing;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.shpytchuk.dbpostgres.core.ContactInfo;
import org.shpytchuk.dbpostgres.core.Place;

import java.time.LocalDate;

@MappedSuperclass
public class Thing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String title;

    @Size(max = 250)
    @Column(length = 250)
    private String description;

    @Size(max = 200)
    @Column(length = 200)
    private String image;

    @Column(nullable = false)
    private LocalDate date;

    @Column()
    private Integer compensation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private ContactInfo info;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private ThingCategory category;
}

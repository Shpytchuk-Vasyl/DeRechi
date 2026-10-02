package org.shpytchuk.dbpostgres.thing;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.shpytchuk.dbpostgres.detail.ContactInfo;
import org.shpytchuk.dbpostgres.detail.Place;

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

    @PastOrPresent
    @Column(nullable = false)
    private LocalDate date;

    @PositiveOrZero
    @Column()
    private Integer compensation;

    @NotBlank
    @Size(min = 3, max = 3)
    @Column(nullable = false, length = 3)
    private String currency;

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

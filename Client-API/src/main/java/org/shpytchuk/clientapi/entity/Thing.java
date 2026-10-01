package org.shpytchuk.clientapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class Thing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column()
    private String description;

    @Column()
    private String image;

    @Column(nullable = false)
    private LocalDate date;
    @Column
    private Integer compensation;

    @Column(nullable = false, length = 3)
    private String currency;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private ContactInfo info;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private Place place;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private ThingCategory category;
}

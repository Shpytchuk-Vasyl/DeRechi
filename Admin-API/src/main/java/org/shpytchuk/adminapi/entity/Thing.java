package org.shpytchuk.adminapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
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

    @Column
    private String description;

    @Column
    private String image;

    @Column(nullable = false)
    private LocalDate date;

    @Column
    private Integer compensation;

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

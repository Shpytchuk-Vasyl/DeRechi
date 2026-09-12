package org.shpytchuk.dbpostgres.core;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;


@Entity
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(columnDefinition = "geography(Point, 4326)", nullable = false)
    private Point coordinate;


}

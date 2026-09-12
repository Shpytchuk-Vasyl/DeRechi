package org.shpytchuk.clientapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;


@Entity
@Getter
@Setter
@NoArgsConstructor
public class Place {

    @Id
    @Column(name = "google_place_id", nullable = false)
    private String googlePlaceId;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(columnDefinition = "geography(Point, 4326)", nullable = false)
    private Point coordinate;
}

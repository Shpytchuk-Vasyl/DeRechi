package org.shpytchuk.clientapi.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class GeoPoints {

    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

    private GeoPoints() {
    }

    public static Point point(double lat, double lon) {
        return WGS84.createPoint(new Coordinate(lon, lat));
    }
}

package com.valxp.app.infiniteflightwatcher;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;
import com.valxp.app.infiniteflightwatcher.model.Flight;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ValXp on 7/27/14.
 */
public class StrokedPolyLine {
    private static final double MAX_ALTITUDE = 50000; // Max altitude in ft
    private Polyline mStroke;
    private Polyline mLine;


    public void update(double speed, double altitude) {
        float width = 1.0f + (float)(speed / 100.0);
        mStroke.setWidth(width * 1.5f);
        mLine.setColor(valueToColor(altitude / MAX_ALTITUDE));
        mLine.setWidth(width);
    }

    public List<LatLng> getPoints() {
        return mStroke.getPoints();
    }

    public void setPoints(LatLng first, LatLng second) {
        LatLng firstOffset = SphericalUtil.interpolate(first, second, .01);
        List<LatLng> list = mStroke.getPoints();
        if (list == null)
            list = new ArrayList<LatLng>();
        list.clear();
        list.add(first);
        list.add(second);
        mStroke.setPoints(list);
        list = mLine.getPoints();
        if (list == null)
            list = new ArrayList<LatLng>();
        list.clear();
        list.add(firstOffset);
        list.add(second);
        mLine.setPoints(list);
    }

    public void remove() {
        mStroke.remove();
        mLine.remove();
    }

    public StrokedPolyLine(GoogleMap map, double speed, double altitude, LatLng first, LatLng second) {
        construct(map, speed, altitude, first, second);
    }

    public StrokedPolyLine(GoogleMap map, Flight.FlightData first, Flight.FlightData second) {
        construct(map, first.speed, first.altitude, first.position, second.position);
    }

    private void construct(GoogleMap map, double speed, double altitude, LatLng first, LatLng second) {
        PolylineOptions path = new PolylineOptions();
        path.zIndex(0);
        // Should be cheaper to draw
        path.geodesic(false);

        mStroke = map.addPolyline(path);

        path = new PolylineOptions();
        path.zIndex(1);

        mLine = map.addPolyline(path);

        update(speed, altitude);
        setPoints(first, second);
    }


    private int valueToColor(double value) {
        value = Math.max(0, Math.min(1, value));
        int alpha = 0xFF;
        int red = 0;
        int green = 0;
        int blue = 0;
        double step = 1 / 5.0;

        if (value <= step * 1) { // blue 100%. Green increasing
            double inRatio = value / step;
            blue = 0xff;
            green = (int) (0xff * inRatio);
        } else if (value <= step * 2) { // Green 100%. blue decreasing
            double inRatio = (value - (1 * step)) / step;
            green = 0xff;
            blue = (int) (0xff * (1 - inRatio));
        } else if (value <= step * 3) { //Green 100%. red increasing
            double inRatio = (value - (2 * step)) / step;
            green = 0xff;
            red = (int) (0xff * inRatio);
        } else if (value <= step * 4) { // Red 100%. green decreasing
            double inRatio = (value - (3 * step)) / step;
            red = 0xff;
            green = (int) (0xff * (1 - inRatio));
        } else {
            red = 0xff;
        }
        return blue | (green << 8) | (red << 16) | (alpha << 24);
    }

}

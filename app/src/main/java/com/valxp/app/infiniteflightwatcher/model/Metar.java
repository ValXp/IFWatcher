package com.valxp.app.infiniteflightwatcher.model;


import com.google.android.gms.maps.model.LatLng;
import com.valxp.app.infiniteflightwatcher.APIConstants;
import com.valxp.app.infiniteflightwatcher.Webservices;

import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;

/**
 * Created by ValXp on 8/31/14.
 */
public class Metar {
    private String mRaw; // Raw METAR data
    private String mStationID;
    private LatLng mPosition;
    private Double mTemperature; // In celsius
    private Double mWindDir; // Degrees
    private Double mWindSpeed = 0.; // Knots
    private Double mWindGust = 0.; // Knots
    private Double mVisibility; // Miles

    public static HashMap<String, Metar> getMetarInBounds(LatLng topLeft, LatLng  topRight, LatLng  bottomRight, LatLng  bottomLeft) {
        Double minLat = bottomLeft.latitude;
        Double maxLat = topLeft.latitude;
        Double minLon = bottomLeft.longitude;
        Double maxLon = bottomRight.longitude;
        String get = "&minLat=" + minLat + "&maxLat=" + maxLat + "&minLon=" + minLon + "&maxLon=" + maxLon;

        XmlPullParser parser = Webservices.getXML(APIConstants.APICalls.METAR, get, null);

        if (parser == null)
            return null;

        try {
            HashMap<String, Metar> mMetarMap = new HashMap<String, Metar>();
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.getName().equals("METAR")) {
                    Metar metar = new Metar(parser);
                    if (metar.getStationID() != null) {
                        mMetarMap.put(metar.getStationID(), metar);
                    }
                }
                eventType = parser.next();
            }
            return mMetarMap;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Metar(XmlPullParser parser) throws Exception {
        Double lat = null, lng = null;
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.getName().equals("METAR")) {
                return;
            }
            if (eventType == XmlPullParser.START_TAG) {
                String text = null;
                try {
                    text = parser.nextText();
                } catch (Exception e) {
                    eventType = parser.next();
                    continue;
                }
                String name = parser.getName();
                if (name.equals("raw_text")) {
                    mRaw = text;
                } else if (name.equals("station_id")) {
                    mStationID = text;
                } else if (name.equals("latitude")) {
                    lat = parseOrNil(text);
                } else if (name.equals("longitude")) {
                    lng = parseOrNil(text);
                } else if (name.equals("temp_c")) {
                    mTemperature = parseOrNil(text);
                } else if (name.equals("wind_dir_degrees")) {
                    mWindDir = parseOrNil(text);
                } else if (name.equals("wind_speed_kt")) {
                    mWindSpeed = parseOrNil(text);
                } else if (name.equals("wind_gust_kt")) {
                    mWindGust = parseOrNil(text);
                } else if (name.equals("visibility_statute_mi")) {
                    mVisibility = parseOrNil(text);
                }
            }
            eventType = parser.next();
        }
        if (lat != null && lng != null)
            mPosition = new LatLng(lat, lng);
    }

    private Double parseOrNil(String text) {
        try {
            return Double.parseDouble(text);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getRaw() {
        return mRaw;
    }

    public String getStationID() {
        return mStationID;
    }

    public LatLng getPosition() {
        return mPosition;
    }

    public Double getTemperature() {
        return mTemperature;
    }

    public Double getWindDir() {
        return mWindDir;
    }

    public Double getWindSpeed() {
        return mWindSpeed;
    }

    public Double getWindGust() {
        return mWindGust;
    }

    public Double getVisibility() {
        return mVisibility;
    }
}

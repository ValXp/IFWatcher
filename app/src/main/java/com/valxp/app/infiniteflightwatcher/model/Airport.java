package com.valxp.app.infiniteflightwatcher.model;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ValXp on 5/30/15.
 */
public class Airport {
    public float elevation;
    public String ICAO;
    public String name;
    public List<Runway> runways = new ArrayList<>();
    public LatLng position; // Calculated position
    public boolean isMajor; // True for major airports

    public Airport(JSONObject object) throws JSONException {

        elevation = (float) object.getDouble("elevation");
        ICAO = object.getString("ICAO");
        name = object.getString("name");

        JSONArray array = object.getJSONArray("runways");
        double latSum = 0;
        double lonSum = 0;
        int maxLength = 0;
        for (int i = 0; i < array.length(); ++i) {
            runways.add(new Runway(array.getJSONObject(i)));
            latSum += runways.get(i).begin.latitude;
            latSum += runways.get(i).end.latitude;
            lonSum += runways.get(i).begin.longitude;
            lonSum += runways.get(i).end.longitude;
            maxLength = Math.max(maxLength, runways.get(i).length);
        }
        latSum /= array.length() * 2.0;
        lonSum /= array.length() * 2.0;
        position = new LatLng(latSum, lonSum);
        isMajor = maxLength > 7400; // 7400 to have TNCM
    }

    public class Runway {
        public Runway(JSONObject object) throws JSONException {
            width = (float) object.getDouble("width");
            surfaceType = object.getString("surfaceType");
            nameBegin = object.getString("name1");
            nameEnd = object.getString("name2");
            begin = new LatLng(object.getDouble("latitude1"), object.getDouble("longitude1"));
            end = new LatLng(object.getDouble("latitude2"), object.getDouble("longitude2"));
            length = (int) (SphericalUtil.computeDistanceBetween(begin, end) * 3.28);
        }

        public float width;
        public String surfaceType, nameBegin, nameEnd;
        public LatLng begin, end;
        public int length;
    }
}

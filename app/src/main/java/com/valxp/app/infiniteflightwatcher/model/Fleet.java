package com.valxp.app.infiniteflightwatcher.model;

import android.util.JsonReader;
import android.util.Log;

import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.valxp.app.infiniteflightwatcher.APIConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by ValXp on 5/20/14.
 */
public class Fleet {
    private Map<String, Flight> mFleet;
    private boolean mIsUpToDate;

    public Fleet() {
        mFleet = new HashMap<String, Flight>();
        mIsUpToDate = false;
    }

    public void updateFleet() {
        InputStream apiData = fetchJson();
        if (apiData == null) {
            return;
        }
        synchronized (this) {
            parseFlightList(apiData);
        }
        mIsUpToDate = true;
    }

    public Map<String, Flight> getFleet() {
        return mFleet;
    }

    private InputStream fetchJson() {
        URL url = null;
        try {
            url = new URL(APIConstants.APICalls.FLIGHTS.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        StringBuilder out = new StringBuilder();
        try {
            return url.openStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void parseFlightList(InputStream json) {
        JsonReader reader = new JsonReader(new InputStreamReader(json));
        try {
            // The root is an array of flights
            reader.beginArray();

            // Now looping through them
            while (reader.hasNext()) {
                parseFlight(mFleet, reader);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void parseFlight(Map<String, Flight> flights, JsonReader reader) throws IOException {
        Flight tempFlight = new Flight(reader);
        Flight found = flights.get(tempFlight.getDisplayName());
        if (found == null) {
            flights.put(tempFlight.getDisplayName(), tempFlight);
        } else {
            found.addFlightData(tempFlight.getFlightHistory());
        }
    }

    public int getActiveFleetSize() {
        int activeFlights = 0;
        for (Map.Entry<String, Flight> entry : mFleet.entrySet()) {
            if (entry.getValue().getFlightHistory().size() > 0)
                activeFlights++;
        }
        return activeFlights;
    }

    public void discardOldFlights(long thresholdInSeconds) {
        for (Iterator<Map.Entry<String, Flight>> it = mFleet.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Flight> entry = it.next();
            if (entry.getValue().getAgeMs() / 1000 > thresholdInSeconds) {
                Log.d("Fleet", "Removing old flight (" + (entry.getValue().getAgeMs() / 60000) + " minutes old): " + entry.getValue().toString());
                Marker mark = entry.getValue().getMarker();
                Polyline line = entry.getValue().getHistoryTrail();
                Polyline aproxLine = entry.getValue().getAproxTrail();
                if (mark != null)
                    mark.remove();
                if (line != null)
                    line.remove();
                if (aproxLine != null)
                    aproxLine.remove();
                it.remove();
            }
        }
    }

    public boolean isUpToDate() {
        return mIsUpToDate;
    }
}

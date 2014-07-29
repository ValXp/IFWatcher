package com.valxp.app.infiniteflightwatcher.model;

import android.util.JsonReader;
import android.util.Log;

import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.valxp.app.infiniteflightwatcher.APIConstants;
import com.valxp.app.infiniteflightwatcher.StrokedPolyLine;
import com.valxp.app.infiniteflightwatcher.Webservices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Created by ValXp on 5/20/14.
 */
public class Fleet {
    private Map<Users.User, Flight> mFleet;
    private Users mUsers;
    private boolean mIsUpToDate;
    private boolean mIsUpdating = false;
    private List<Flight> mFlightsToRemove;

    public Fleet() {
        mFleet = new HashMap<Users.User, Flight>();
        mFlightsToRemove = new ArrayList<Flight>();
        mIsUpToDate = false;
        mUsers = new Users();
    }

    public Runnable updateFleet(long thresholdInSeconds) {
        synchronized (this) {
            if (mIsUpdating)
                return null;
            mIsUpdating = true;
        }

        parseFlightList(Webservices.getJSON(APIConstants.APICalls.FLIGHTS));

        Runnable forUIThread = discardOldFlights(thresholdInSeconds);


        for (Map.Entry<Users.User, Flight> data : mFleet.entrySet()) {
            Flight value = data.getValue();
            if (value.getNeedsUpdate()) {
                value.pullFullFlight();
                value.setNeedsUpdate(false);
            }
        }

        mUsers.update(false);

        mIsUpToDate = true;
        mIsUpdating = false;
        return forUIThread;
    }

    public Map<Users.User, Flight> getFleet() {
        return mFleet;
    }


    synchronized private void parseFlightList(JSONArray array) {
        if (array == null)
            return;
        try {
            for (int i = 0; i < array.length(); ++i) {
                parseFlight(array.getJSONObject(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    private void parseFlight(JSONObject object) throws JSONException {
        Flight tempFlight = new Flight(mUsers, object);
        if (tempFlight.getAgeMs() / 1000 > 60 * 3) {
            return;
        }
        Flight found = mFleet.get(tempFlight.getUser());
        if (found == null) {
            mFleet.put(tempFlight.getUser(), tempFlight);
            tempFlight.getUser().markForUpdate();
        } else if (tempFlight.getFlightID().equals(found.getFlightID())) {
            found.addFlightData(tempFlight.getFlightHistory());
        } else {
            boolean keepFound = found.getLastReportUTC() > tempFlight.getLastReportUTC();
            Flight toRemove = keepFound ? tempFlight : found;
            Flight toKeep = keepFound ? found : tempFlight;
            removeFlight(toRemove);
            mFleet.put(toKeep.getUser(), toKeep);
        }
    }

    public int getActiveFleetSize() {
        int activeFlights = 0;
        for (Map.Entry<Users.User, Flight> entry : mFleet.entrySet()) {
            if (entry.getValue().getFlightHistory().size() > 0)
                activeFlights++;
        }
        return activeFlights;
    }

    synchronized private Runnable discardOldFlights(long thresholdInSeconds) {
        final List<StrokedPolyLine> linesToRemove = new ArrayList<StrokedPolyLine>();
        final List<Marker> markersToRemove = new ArrayList<Marker>();

        for (Iterator<Map.Entry<Users.User, Flight>> it = mFleet.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Users.User, Flight> entry = it.next();
            if (entry.getValue().getAgeMs() / 1000 > thresholdInSeconds) {
                removeFlight(entry.getValue(), linesToRemove, markersToRemove);
                it.remove();
            }
        }

        for (Iterator<Flight> it = mFlightsToRemove.iterator(); it.hasNext();) {
            removeFlight(it.next(), linesToRemove, markersToRemove);
            it.remove();
        }

        return new Runnable() {
            @Override
            public void run() {
                for (StrokedPolyLine line : linesToRemove)
                    line.remove();
                for (Marker mark : markersToRemove)
                    mark.remove();
            }
        };
    }

    private void removeFlight(Flight flight, List<StrokedPolyLine> linesToRemove, List<Marker> markersToRemove) {
        Log.d("Fleet", "Removing old flight (" + (flight.getAgeMs() / 60000) + " minutes old): " + flight.toString());
        Marker mark = flight.getMarker();
        List<StrokedPolyLine> lines = flight.getHistoryTrail();
        if (lines != null) {
            linesToRemove.addAll(lines);
        }
        StrokedPolyLine aproxLine = flight.getAproxTrail();
        if (aproxLine != null)
            linesToRemove.add(aproxLine);
        if (mark != null)
            markersToRemove.add(mark);
    }

    private void removeFlight(Flight flight) {
        mFlightsToRemove.add(flight);
    }

    public boolean isUpToDate() {
        return mIsUpToDate;
    }
}

package com.valxp.app.infiniteflightwatcher.model;

import android.util.Log;

import com.google.android.gms.maps.model.Marker;
import com.valxp.app.infiniteflightwatcher.APIConstants;
import com.valxp.app.infiniteflightwatcher.StrokedPolyLine;
import com.valxp.app.infiniteflightwatcher.Webservices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by ValXp on 5/20/14.
 */
public class Fleet {
    private Map<Users.User, Flight> mFleet;
    private Users mUsers;
    private boolean mIsUpToDate;
    private boolean mIsUpdating = false;
    private Server mSelectedServer;
    private List<Flight> mFlightsToRemove;
    private boolean mCleanup = false;

    public Fleet() {
        mFleet = new HashMap<Users.User, Flight>();
        mFlightsToRemove = new ArrayList<Flight>();
        mSelectedServer = null;
        mIsUpToDate = false;
        mUsers = new Users();
    }

    public Runnable updateFleet(long thresholdInSeconds) {
        synchronized (this) {
            if (mIsUpdating)
                return null;
            mIsUpdating = true;
        }
        final ArrayList<Runnable> runnables = new ArrayList<Runnable>();

        if (mSelectedServer == null) {
            mIsUpdating = false;
            return null;
        }
        if (mCleanup) {
            mCleanup = false;
            runnables.add(discardOldFlights(Long.MIN_VALUE));
        }
        parseFlightList(Webservices.getJSON(APIConstants.APICalls.FLIGHTS, "&sessionid=" + mSelectedServer.getId()));

        runnables.add(discardOldFlights(thresholdInSeconds));

        for (Map.Entry<Users.User, Flight> data : mFleet.entrySet()) {
            Flight value = data.getValue();
            if (value.getNeedsUpdate()) {
                value.pullFullFlight();
                value.setNeedsUpdate(false);
            }
        }

        mIsUpToDate = true;
        mIsUpdating = false;
        return new Runnable() {
            @Override
            public void run() {
                for (Runnable r : runnables)
                    r.run();
            }
        };
    }

    public Map<Users.User, Flight> getFleet() {
        return mFleet;
    }

    public Users getUsers() {
        return mUsers;
    }

    public void selectServer(Server server) {
        if (server != mSelectedServer) {
            mCleanup = true;
            mIsUpToDate = false;
        }
        mSelectedServer = server;
    }

    public Server getSelectedServer() {
        return mSelectedServer;
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


    public void parseFlight(JSONObject object) throws JSONException {
        Flight tempFlight = new Flight(mUsers, object);
        if (tempFlight.getAgeMs() / 1000 > 60 * 3) {
            return;
        }
        Flight found = mFleet.get(tempFlight.getUser());
        if (found == null) {
            tempFlight.getUser().setCurrentFlight(tempFlight);
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
            toKeep.getUser().setCurrentFlight(toKeep);
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
        if (flight.getUser().getCurrentFlight() == flight) {
            flight.getUser().setCurrentFlight(null);
        }
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
        return mIsUpToDate && !mIsUpdating;
    }
}

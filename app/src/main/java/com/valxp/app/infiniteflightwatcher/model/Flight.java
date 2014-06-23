package com.valxp.app.infiniteflightwatcher.model;

import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;
import android.support.v4.util.LongSparseArray;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.valxp.app.infiniteflightwatcher.APIConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

/**
 * Created by ValXp on 5/20/14.
 */
public class Flight {
    private String mAircraftName;
    private String mCallSign;
    private String mDisplayName;
    private String mFlightID;
    private String mUserID;
    private Long mLastReportUTC;

    private LongSparseArray<FlightData> mFlightHistory;
    private Marker mMarker;
    private Polyline mHistoryTrail;
    private Polyline mAproxTrail;
    private boolean mIsNewFlight = true;

    public Flight(JsonReader reader) throws IOException {
        reader.beginObject();
        mFlightHistory = new LongSparseArray<FlightData>();
        FlightData data = new FlightData();
        while (reader.hasNext()) {
            String name = reader.nextName();
            // Ignore null values
            if (reader.peek() == JsonToken.NULL) {
                reader.skipValue();
                continue;
            }
            if (name.equals("AircraftName"))
                mAircraftName = reader.nextString();
            else if (name.equals("CallSign"))
                mCallSign = reader.nextString();
            else if (name.equals("DisplayName"))
                mDisplayName = reader.nextString();
            else if (name.equals("FlightID"))
                mFlightID = reader.nextString();
            else if (name.equals("UserID"))
                mUserID = reader.nextString();
            else if (!data.parseJson(name, reader)){
                Log.w("FlightParsing", "Skipping value " + name);
                reader.skipValue();
            }
        }
        mLastReportUTC = data.reportTimestampUTC;
        mFlightHistory.append(data.reportTimestampUTC, data);
        reader.endObject();
        //Log.d("FlightPArsing", this.toString());
    }

    public void addFlightData(LongSparseArray<FlightData> data) {
        // We only check the last one, and compare it to see if we already have it and then add it to our list
        if (data.size() <= 0 || mFlightHistory.size() <= 0)
            return;
        for (int i = 0; i < data.size(); ++i) {
            mFlightHistory.append(data.keyAt(i), data.valueAt(i));
        }
    }

    private InputStream fetchFLightDetailsJson() {
        URL url = null;
        try {
            url = new URL(APIConstants.APICalls.FLIGHT_DETAILS.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        try {
            URLConnection connection = url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);

            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write("{\"FlightID\":\"" + mFlightID + "\"}");
            writer.flush();

            return connection.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    // Retrieves the full flight from the server
    public void pullFullFlight() {
        Log.d("Flight", "Pulling full flight...");
        int count = 0;
        JsonReader reader = new JsonReader(new InputStreamReader(fetchFLightDetailsJson()));
        synchronized (this) {
            try {
                // The root is an array of flights
                reader.beginArray();
                // Now looping through each flight data
                while (reader.hasNext()) {
                    reader.beginObject();
                    FlightData data = new FlightData();
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        // Ignore null values
                        if (reader.peek() == JsonToken.NULL) {
                            reader.skipValue();
                            continue;
                        }
                        if (!data.parseJson(name, reader)) {
                            Log.w("FlightDetailsParsing", "Skipping value " + name);
                            reader.skipValue();
                        }
                    }
                    ++count;
                    mFlightHistory.append(data.reportTimestampUTC, data);
                    reader.endObject();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d("Flight", "Pulled " + count + " flight points");
    }

    public String getAircraftName() {
        return mAircraftName;
    }

    public String getCallSign() {
        return mCallSign;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public Long getLastReportUTC() {
        return mLastReportUTC;
    }

    public String getUserID() {
        return mUserID;
    }

    public String getFlightID() {
        return mFlightID;
    }

    public LongSparseArray<FlightData> getFlightHistory() {
        return mFlightHistory;
    }

    public Marker getMarker() {
        return mMarker;
    }

    public void setMarker(Marker mMarker) {
        this.mMarker = mMarker;
    }

    public Polyline getHistoryTrail() {
        return mHistoryTrail;
    }

    public void setHistoryTrail(Polyline mPolyLine) {
        this.mHistoryTrail = mPolyLine;
    }

    public Polyline getAproxTrail() {
        return mAproxTrail;
    }

    public void setAproxTrail(Polyline mAproxTrail) {
        this.mAproxTrail = mAproxTrail;
    }

    public boolean isNewFlight() {
        return mIsNewFlight;
    }

    public void setIsNewFlight(boolean isNew) {
        mIsNewFlight = isNew;
    }

    public long getAgeMs() {
        if (mFlightHistory.size() > 0) {
            return mFlightHistory.valueAt(mFlightHistory.size() - 1).getAgeMs();
        } else
            return 0;
    }

    @Override
    public String toString() {
        return "Flight{" +
                "mFlightHistory=" + mFlightHistory +
                ", mAircraftName='" + mAircraftName + '\'' +
                ", mCallSign='" + mCallSign + '\'' +
                ", mDisplayName='" + mDisplayName + '\'' +
                ", mFlightID=" + mFlightID +
                ", mUserID=" + mUserID +
                ", mLastReportUTC=" + mLastReportUTC +
                '}';
    }

    public class FlightData {
        public LatLng position;
        public Double speed;
        public Double bearing;
        public Long reportTimestampUTC;
        public Double altitude;

        private Double lat = null;
        private Double lng = null;

        public boolean isOlderThan(FlightData other) {
            return reportTimestampUTC < other.reportTimestampUTC;
        }

        public boolean parseJson(String name, JsonReader reader) throws IOException {
            if (name.equals("Latitude"))
                lat = reader.nextDouble();
            else if (name.equals("Longitude"))
                lng = reader.nextDouble();
            else if (name.equals("Speed"))
                speed = reader.nextDouble();
            else if (name.equals("Track"))
                bearing = reader.nextDouble();
            else if (name.equals("Altitude"))
                altitude = reader.nextDouble();
            else if (name.equals("LastReportUTC") || name.equals("Time")) {
                reportTimestampUTC = ((reader.nextLong() / 10000000) - 11644473600l) * 1000; // Windows file time to unix time in MS
            } else {
                return false;
            }
            if (lat != null && lng != null) {
                position = new LatLng(lat, lng);
                lat = null;
                lng = null;
            }
            return true;
        }

        public long getAgeMs() {
            return new Date().getTime() - reportTimestampUTC;
        }

        @Override
        public String toString() {
            return "FlightData{" +
                    "position=" + position +
                    ", speed=" + speed +
                    ", bearing=" + bearing +
                    ", altitude=" + altitude +
                    '}';
        }
    }
}

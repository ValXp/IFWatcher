package com.valxp.app.infiniteflightwatcher.model;

import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

    private List<FlightData> mFlightHistory;
    private Marker mMarker;
    private Polyline mHistoryTrail;
    private Polyline mAproxTrail;

    public Flight(JsonReader reader) throws IOException {
        reader.beginObject();
        double lat = 0, lng = 0;
        mFlightHistory = new ArrayList<FlightData>();
        mFlightHistory.add(new FlightData());
        while (reader.hasNext()) {
            String name = reader.nextName();
            // Ignore null values
            if (reader.peek() == JsonToken.NULL) {
                reader.skipValue();
                continue;
            }
            if (name.equals("AircraftName"))
                mAircraftName = reader.nextString();
            else if (name.equals("Altitude"))
                mFlightHistory.get(0).altitude = reader.nextDouble();
            else if (name.equals("CallSign"))
                mCallSign = reader.nextString();
            else if (name.equals("DisplayName"))
                mDisplayName = reader.nextString();
            else if (name.equals("LastReport"))
                mFlightHistory.get(0).lastReport = reader.nextString();
            else if (name.equals("Latitude"))
                lat = reader.nextDouble();
            else if (name.equals("Longitude"))
                lng = reader.nextDouble();
            else if (name.equals("Speed"))
                mFlightHistory.get(0).speed = reader.nextDouble();
            else if (name.equals("Track"))
                mFlightHistory.get(0).bearing = reader.nextDouble();
            else if (name.equals("FlightID")) {
                mFlightID = reader.nextString();
            } else if (name.equals("UserID")) {
                mUserID = reader.nextString();
            } else if (name.equals("LastReportUTC")) {
                mLastReportUTC = ((reader.nextLong() / 10000000) - 11644473600l) * 1000; // Windows file time to unix time in MS
                mFlightHistory.get(0).reportTimestampUTC = mLastReportUTC;
            } else {
                Log.w("FlightParsing", "Skipping value " + name);
                reader.skipValue();
            }
        }
        mFlightHistory.get(0).position = new LatLng(lat, lng);
        // Ignoring old locations
        if (!mFlightHistory.get(0).lastReport.equals("less than a minute ago")) {
            //Log.d("Flight", "Ignoring " + mDisplayName + " because it was " + mFlightHistory.get(0).lastReport);
            //mFlightHistory.remove(0);
        }
        reader.endObject();
        //Log.d("FlightPArsing", this.toString());
    }

    public void addFlightData(List<FlightData> data) {
        // We only check the last one, and compare it to see if we already have it and then add it to our list
        if (data.size() <= 0 || mFlightHistory.size() <= 0)
            return;
        if (mFlightHistory.get(mFlightHistory.size() - 1).isOlderThan(data.get(0))) {
            mFlightHistory.add(data.get(0));
        }
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

    public List<FlightData> getFlightHistory() {
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

    public long getAgeMs() {
        if (mFlightHistory.size() > 0) {
            return mFlightHistory.get(mFlightHistory.size() - 1).getAgeMs();
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
        public String lastReport;
        public Long reportTimestampUTC;
        public Double altitude;

        public boolean isOlderThan(FlightData other) {
            return reportTimestampUTC > other.reportTimestampUTC;
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
                    ", lastReport='" + lastReport + '\'' +
                    ", altitude=" + altitude +
                    '}';
        }
    }
}

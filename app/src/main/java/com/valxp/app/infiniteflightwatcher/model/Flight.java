package com.valxp.app.infiniteflightwatcher.model;

import android.util.Log;
import android.support.v4.util.LongSparseArray;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.SphericalUtil;
import com.valxp.app.infiniteflightwatcher.APIConstants;
import com.valxp.app.infiniteflightwatcher.StrokedPolyLine;
import com.valxp.app.infiniteflightwatcher.TimeProvider;
import com.valxp.app.infiniteflightwatcher.Webservices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Created by ValXp on 5/20/14.
 */
public class Flight {
    public static double METERS_TO_NAUTICAL_MILES = 0.000539957;
    private String mAircraftName;
    private String mCallSign;
    private String mDisplayName;
    private String mFlightID;
    private String mUserID;
    private Long mLastReportUTC;
    private Users.User mUser;

    private LongSparseArray<FlightData> mFlightHistory;
    private Marker mMarker;
    private List<StrokedPolyLine> mHistoryTrail;
    private StrokedPolyLine mAproxTrail;
    private boolean mNeedsUpdate = false;

    public Flight(Users users, JSONObject object) throws JSONException {
        mFlightHistory = new LongSparseArray<FlightData>();
        FlightData data = new FlightData(object);
        mAircraftName = object.getString("AircraftName");
        mCallSign = object.getString("CallSign");
        mDisplayName = object.getString("DisplayName");
        mFlightID = object.getString("FlightID");
        mUserID = object.getString("UserID");

        mLastReportUTC = data.reportTimestampUTC;
        mFlightHistory.append(data.reportTimestampUTC, data);
        mUser = users.addUser(mUserID);
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


    // Retrieves the full flight from the server
    public void pullFullFlight() {
        Log.d("Flight", "Pulling full flight...");
        JSONArray array = Webservices.getJSON(APIConstants.APICalls.FLIGHT_DETAILS, "{\"FlightID\":\"" + mFlightID + "\"}");
        if (array == null)
            return;
        synchronized (this) {
            for (int i = 0; i < array.length(); ++i) {

                FlightData data = null;
                try {
                    data = new FlightData(array.getJSONObject(i));
                    mFlightHistory.append(data.reportTimestampUTC, data);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
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

    public LongSparseArray<FlightData> getFlightHistory() {
        return mFlightHistory;
    }

    public Marker getMarker() {
        return mMarker;
    }

    public void setMarker(Marker mMarker) {
        this.mMarker = mMarker;
    }

    public List<StrokedPolyLine> getHistoryTrail() {
        return mHistoryTrail;
    }

    public void setHistoryTrail(List<StrokedPolyLine> mPolyLine) {
        this.mHistoryTrail = mPolyLine;
    }

    public StrokedPolyLine getAproxTrail() {
        return mAproxTrail;
    }

    public void setAproxTrail(StrokedPolyLine mAproxTrail) {
        this.mAproxTrail = mAproxTrail;
    }

    public boolean getNeedsUpdate() {
        return mNeedsUpdate;
    }

    public void setNeedsUpdate(boolean isNew) {
        mNeedsUpdate = isNew;
    }

    public Users.User getUser() {
        return mUser;
    }

    public long getAgeMs() {
        if (mFlightHistory.size() > 0) {
            return mFlightHistory.valueAt(mFlightHistory.size() - 1).getAgeMs();
        } else
            return 0;
    }

    // Cumulative distance for the whole flight
    public double getFlightAbsoluteDistance() {
        if (mFlightHistory == null || mFlightHistory.size() < 2)
            return 0;
        double length = 0;
        FlightData data = mFlightHistory.valueAt(0);
        for (int i = 1; i < mFlightHistory.size(); ++i) {
            FlightData nextData = mFlightHistory.valueAt(i);
            length += SphericalUtil.computeDistanceBetween(data.position, nextData.position);

            data = nextData;
        }
        return length * METERS_TO_NAUTICAL_MILES;
    }

    // Distance between start and end point
    public double getEndToEndDistance() {
        if (mFlightHistory == null || mFlightHistory.size() < 2)
            return 0;
        return SphericalUtil.computeDistanceBetween(mFlightHistory.valueAt(0).position, mFlightHistory.valueAt(mFlightHistory.size() - 1).position) * METERS_TO_NAUTICAL_MILES;
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
        public Double verticalSpeed;

        public boolean isOlderThan(FlightData other) {
            return reportTimestampUTC < other.reportTimestampUTC;
        }

        public FlightData(JSONObject object) throws JSONException {
            position = new LatLng(object.getDouble("Latitude"), object.getDouble("Longitude"));
            speed = object.getDouble("Speed");
            bearing = object.getDouble("Track");
            altitude = object.getDouble("Altitude");
            reportTimestampUTC = object.optLong("LastReportUTC", object.optLong("Time", 0));
            reportTimestampUTC = ((reportTimestampUTC / 10000000) - 11644473600l) * 1000; // Windows file time to unix time in MS
            verticalSpeed = object.getDouble("VerticalSpeed");
        }

        public long getAgeMs() {
            return TimeProvider.getTime() - reportTimestampUTC;
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

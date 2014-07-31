package com.valxp.app.infiniteflightwatcher.model;

import android.util.Log;
import android.support.v4.util.LongSparseArray;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.geometry.Bounds;
import com.valxp.app.infiniteflightwatcher.APIConstants;
import com.valxp.app.infiniteflightwatcher.AirplaneBitmapProvider;
import com.valxp.app.infiniteflightwatcher.MapsActivity;
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
    public static long FULL_FLIGHT_MIN_DELAY = 2 * 3600; // MS
    private String mAircraftName;
    private String mCallSign;
    private String mDisplayName;
    private String mFlightID;
    private String mUserID;
    private Long mLastReportUTC;
    private Users.User mUser;
    private Bounds mBounds;
    private long mLastFullFlightPulledTime;

    private LongSparseArray<FlightData> mFlightHistory;
    private Marker mMarker;
    private List<StrokedPolyLine> mHistoryTrail;
    private StrokedPolyLine mAproxTrail;
    private boolean mNeedsUpdate = false;

    private static class Bounds {
        public double minLat;
        public double maxLat;
        public double minLon;
        public double maxLon;
        private LatLngBounds mBounds;

        public Bounds(LatLng pos) {
            mBounds = null;
            minLat = pos.latitude;
            maxLat = pos.latitude;
            minLon = pos.longitude;
            maxLon = pos.longitude;
        }

        public void update(LatLng pos) {
            mBounds = null;
            minLat = Math.min(minLat, pos.latitude);
            minLon = Math.min(minLon, pos.longitude);
            maxLat = Math.max(maxLat, pos.latitude);
            maxLon = Math.max(maxLon, pos.longitude);
        }

        public LatLngBounds getBounds() {
            if (mBounds == null) {
                mBounds = new LatLngBounds(new LatLng(minLat, minLon), new LatLng(maxLat, maxLon));
            }
            return mBounds;
        }
    }

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
        mBounds = new Bounds(data.position);
    }

    public void zoomIn(GoogleMap map, GoogleMap.CancelableCallback cb) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(getAproxLocation(), (float) (14 - (getCurrentData().speed / 150))), cb);
    }

    public void addFlightData(LongSparseArray<FlightData> data) {
        // We only check the last one, and compare it to see if we already have it and then add it to our list
        if (data.size() <= 0 || mFlightHistory.size() <= 0)
            return;
        for (int i = 0; i < data.size(); ++i) {
            mBounds.update(data.valueAt(i).position);
            mFlightHistory.append(data.keyAt(i), data.valueAt(i));
        }
    }


    // Retrieves the full flight from the server
    public void pullFullFlight() {
        Log.d("Flight", "Pulling full flight...");
        if (TimeProvider.getTime() - mLastFullFlightPulledTime < FULL_FLIGHT_MIN_DELAY)
            return;
        mLastFullFlightPulledTime = TimeProvider.getTime();
        JSONArray array = Webservices.getJSON(APIConstants.APICalls.FLIGHT_DETAILS, "{\"FlightID\":\"" + mFlightID + "\"}");
        if (array == null)
            return;
        synchronized (this) {
            for (int i = 0; i < array.length(); ++i) {

                FlightData data = null;
                try {
                    data = new FlightData(array.getJSONObject(i));
                    mBounds.update(data.position);
                    mFlightHistory.append(data.reportTimestampUTC, data);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public LatLng getAproxLocation() {
        FlightData current = getCurrentData();
        if (current == null)
            return null;
        long delta = Math.min(TimeProvider.getTime() - current.reportTimestampUTC, MapsActivity.MAX_INTERPOLATE_DURATION_MS);
        if (current.speed < MapsActivity.MINIMUM_INTERPOLATION_SPEED_KTS || delta <= 0)
            delta = 0;
        double distanceMeter = (current.speed * MapsActivity.KTS_TO_M_PER_S) * (delta / 1000.0);
        return SphericalUtil.computeOffset(current.position, distanceMeter, current.bearing);
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

    public void removeMarker() {
        if (this.mMarker != null) {
            this.mMarker.remove();
        }
        this.mMarker = null;
    }

    public void createMarker(GoogleMap map, AirplaneBitmapProvider provider) {
        if (mMarker != null)
            return;
        Marker marker = map.addMarker(new MarkerOptions()
                .position(getAproxLocation())
                .rotation(getCurrentData().bearing.floatValue())
                .icon(provider.getAsset(this))
                .anchor(.5f, .5f)
                .flat(true)); // Flat will keep the rotation based on the north
        setMarker(marker);
    }

    public List<StrokedPolyLine> getHistoryTrail() {
        return mHistoryTrail;
    }

    public void setHistoryTrail(List<StrokedPolyLine> mPolyLine) {
        this.mHistoryTrail = mPolyLine;
    }

    public void removeHistoryTrail() {
        if (this.mHistoryTrail != null) {
            for (StrokedPolyLine pl : mHistoryTrail) {
                pl.remove();
            }
        }
        this.mHistoryTrail = null;
    }

    public StrokedPolyLine getAproxTrail() {
        return mAproxTrail;
    }

    public void setAproxTrail(StrokedPolyLine mAproxTrail) {
        this.mAproxTrail = mAproxTrail;
    }

    public void removeAproxTrail() {
        if (this.mAproxTrail != null) {
            this.mAproxTrail.remove();
        }
        this.mAproxTrail = null;
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

    public FlightData getCurrentData() {
        return mFlightHistory.valueAt(mFlightHistory.size() - 1);
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

package com.valxp.app.infiniteflightwatcher.model;

import android.animation.Animator;
import android.app.Activity;
import android.util.Log;
import android.support.v4.util.LongSparseArray;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.SphericalUtil;
import com.valxp.app.infiniteflightwatcher.APIConstants;
import com.valxp.app.infiniteflightwatcher.AirplaneBitmapProvider;
import com.valxp.app.infiniteflightwatcher.MarkerAnimation;
import com.valxp.app.infiniteflightwatcher.Utils;
import com.valxp.app.infiniteflightwatcher.activities.MapsActivity;
import com.valxp.app.infiniteflightwatcher.StrokedPolyLine;
import com.valxp.app.infiniteflightwatcher.TimeProvider;
import com.valxp.app.infiniteflightwatcher.Webservices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ValXp on 5/20/14.
 */
public class Flight {
    public static Activity ctx;
    public static double METERS_TO_NAUTICAL_MILES = 0.000539957;
    public static long FULL_FLIGHT_MIN_DELAY = 2 * 60 * 1000; // MS
    private String mCallSign;
    private String mDisplayName;
    private String mFlightID;
    private String mUserID;
    private Liveries.Livery mLivery;
    private Long mLastReportUTC;
    private Users.User mUser;
    private Bounds mBounds;
    private long mLastFullFlightPulledTime;

    public Server getServer() {
        return mServer;
    }

    public void setServer(Server mServer) {
        this.mServer = mServer;
    }

    private Server mServer;

    private LongSparseArray<FlightData> mFlightHistory;
    private Marker mMarker;
    private Animator mMarkerAnimator;
    private Animator mLineAnimator;
    private List<StrokedPolyLine> mHistoryTrail;
    private StrokedPolyLine mAproxTrail;
    private boolean mNeedsUpdate = false;
    private boolean mIsSelected = true;

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
        mCallSign = object.getString("CallSign");
        mDisplayName = object.getString("DisplayName");
        mFlightID = object.getString("FlightID");
        mLivery = Liveries.getLivery(object.getString("LiveryID"));
        mUserID = object.getString("UserID");

        mLastReportUTC = data.reportTimestampUTC;
        mFlightHistory.append(data.reportTimestampUTC, data);
        mUser = users.addUser(mUserID, mDisplayName);
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
            _addFlightData(data.valueAt(i));
        }
        // Update marker for latest position
        updateAnim();
    }

    public void addFlightData(FlightData data) {
        _addFlightData(data);
        // Update marker for latest position
        updateAnim();
    }

    public void clearMap() {
        if (mMarkerAnimator != null) {
            mMarkerAnimator.cancel();
            mMarkerAnimator = null;
        }
        mMarker = null;
        if (mLineAnimator != null) {
            mLineAnimator.cancel();
            mLineAnimator = null;
        }
        mAproxTrail = null;
        mHistoryTrail = new ArrayList<>();
    }

    private void updateAnim() {
        ctx.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mMarker != null) {
                    mMarkerAnimator.cancel();
                    mMarker.setRotation(getCurrentData().bearing.floatValue());
                    mMarker.setPosition(getAproxLocation());
                    createMarkerAnimator();
                }
                if (mLineAnimator != null) {
                    mLineAnimator.cancel();
                    if (mAproxTrail != null)
                        createLineAnimator();
                }
                if (mIsSelected)
                    ((MapsActivity) ctx).updatePath(Flight.this);
            }
        });
    }

    private void _addFlightData(FlightData data) {
        // Avoid duplicate with different timestamp
        if (mFlightHistory.size() <= 0 || !data.equals(getCurrentData())) {
            // Find unnecessary intermediate value if we already have 2 previous values.
//            if (mFlightHistory.size() > 1) {
//                int counter = mFlightHistory.size() - 1;
//                long lastKey = -1;
//                do {
//                    lastKey = mFlightHistory.keyAt(counter);
//                    --counter;
//                } while (counter >= 1 && lastKey > data.reportTimestampUTC);
//
//                if (counter >= 0 && lastKey != -1 && lastKey <= data.reportTimestampUTC) {
//                    FlightData first = mFlightHistory.get(lastKey);
//                    FlightData middle = mFlightHistory.get(mFlightHistory.keyAt(counter));
//                    // We interpolate the value between the last and the first and see if the middle one
//                    if (middle != null && first != null && data != null) {
//                        double difference = middle.interpolationDifference(first, data);
//                        Log.d("Flight", "Difference: " + difference);
//                        if (difference > .1) {
//                            mFlightHistory.remove(middle.reportTimestampUTC);
//                        }
//                    }
//                }
//
//            }
            mBounds.update(data.position);
            mFlightHistory.append(data.reportTimestampUTC, data);
        }
    }

    // Retrieves the full flight from the server
    public void pullFullFlight() {
        if (TimeProvider.getTime() - mLastFullFlightPulledTime < FULL_FLIGHT_MIN_DELAY)
            return;
        Log.d("Flight", "Pulling full flight... " + mFlightID);
        mLastFullFlightPulledTime = TimeProvider.getTime();
        JSONArray array = Webservices.getJSON(APIConstants.APICalls.FLIGHT_DETAILS, "&flightid="+ mFlightID);
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

    // Interpolate position at timeMs from now.
    public LatLng getAproxLocation(long timeMs) {
        FlightData current = getCurrentData();
        if (current == null)
            return null;
        long delta = TimeProvider.getTime() - current.reportTimestampUTC + timeMs;
        if (current.speed < MapsActivity.MINIMUM_INTERPOLATION_SPEED_KTS || delta <= 0)
            delta = 0;
        double distanceMeter = (current.speed * MapsActivity.KTS_TO_M_PER_S) * (delta / 1000.0);
        return SphericalUtil.computeOffset(current.position, distanceMeter, current.bearing);
    }

    public LatLng getAproxLocation() {
        return getAproxLocation(0);
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

    public Liveries.Livery getLivery() {
        return mLivery;
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
        if (mMarkerAnimator != null) {
            MarkerAnimation.stopAnimator(mMarkerAnimator);
            mMarkerAnimator = null;
        }
        if (mMarker != null) {
            mMarker.remove();
            mMarker = null;
        }
    }

    public void createMarker(GoogleMap map, AirplaneBitmapProvider provider) {
        createMarker(map, provider, false);
    }

    private void createMarker(GoogleMap map, AirplaneBitmapProvider provider, boolean selected) {
        // Marker creation needs the user to be set
        FlightData data = getCurrentData();
        LatLng pos = getAproxLocation();
        if (mMarker != null && selected != mIsSelected) {
            try {
                mMarker.setIcon(provider.getAsset(this, selected));
            } catch (Exception e) {
                e.printStackTrace();
                mMarker = null;
            }
        }
        if (mMarker == null) {
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(pos)
                    .rotation(data.bearing.floatValue())
                    .icon(provider.getAsset(this, selected))
                    .anchor(.5f, .5f)
                    .flat(true)); // Flat will keep the rotation based on north
            setMarker(marker);
            createMarkerAnimator();
        }
        mIsSelected = selected;
        if (mIsSelected) {
            mMarker.showInfoWindow();
            StrokedPolyLine line = getAproxTrail();
            if (line != null) {
                line.update(data.speed, data.altitude);
                line.setPoints(data.position, pos);
                setAproxTrail(line);
            } else {
                setAproxTrail(new StrokedPolyLine(map, data.speed, data.altitude, data.position, pos));
            }
        }
    }

    private void createMarkerAnimator() {
        mMarkerAnimator = MarkerAnimation.animateMarker(mMarker, getAproxLocation(FULL_FLIGHT_MIN_DELAY), FULL_FLIGHT_MIN_DELAY);
    }

    private void createLineAnimator() {
        mAproxTrail.setPoints(getCurrentData().position, getAproxLocation());
        mLineAnimator = MarkerAnimation.animatePolyline(mAproxTrail, getAproxLocation(), getAproxLocation(FULL_FLIGHT_MIN_DELAY), FULL_FLIGHT_MIN_DELAY);
    }
    public void selectMarker(GoogleMap map, AirplaneBitmapProvider provider, boolean select) {
        createMarker(map, provider, select);
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

    public void setAproxTrail(StrokedPolyLine aproxTrail) {
        if (mLineAnimator != null)
            mLineAnimator.cancel();
        mAproxTrail = aproxTrail;
        createLineAnimator();
    }

    public void removeAproxTrail() {
        if (mAproxTrail != null) {
            mAproxTrail.remove();
        }
        MarkerAnimation.stopAnimator(mLineAnimator);
        mLineAnimator = null;
        mAproxTrail = null;
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
        return mFlightHistory.size() == 0 ? null : mFlightHistory.valueAt(mFlightHistory.size() - 1);
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
                ", mCallSign='" + mCallSign + '\'' +
                ", mDisplayName='" + mDisplayName + '\'' +
                ", mFlightID=" + mFlightID +
                ", mUserID=" + mUserID +
                ", mLiveryID=" + mLivery.getName() +
                ", mLastReportUTC=" + mLastReportUTC +
                '}';
    }

    public static class FlightData {
        public LatLng position;
        public Double speed;
        public Double bearing;
        public Long reportTimestampUTC;
        public Double altitude;
        public Double verticalSpeed;

        public boolean isOlderThan(FlightData other) {
            return reportTimestampUTC < other.reportTimestampUTC;
        }

        // Equals ignores timestamp!!!
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FlightData that = (FlightData) o;

            if (!position.equals(that.position)) return false;
            if (!speed.equals(that.speed)) return false;
            if (!bearing.equals(that.bearing)) return false;
            if (!altitude.equals(that.altitude)) return false;
            return verticalSpeed.equals(that.verticalSpeed);

        }

        // Returns a float corresponding to the error [0 - 1]. 0 for 0% error. 1 for 100%.
        public double interpolationDifference(FlightData before, FlightData after) {
            // TODO: Make sure before is _BEFORE_ and after is _AFTER_
            // First look at speed difference
            double speedError = (speed - ((before.speed + speed + after.speed) / 3.0)) / speed;

            // Then look at altitude difference
            double altitudeDifference = (altitude - ((before.altitude + altitude + after.altitude) / 3.0)) / altitude;

            // Bearing difference
            double bearingDifference = (bearing - ((before.bearing + bearing + after.bearing) / 3.0)) / bearing;

            return Math.abs(speedError) + Math.abs(altitudeDifference) + Math.abs(bearingDifference);
        }

        public FlightData(LatLng position, Double speed, Double bearing, Long reportTimestampUTC, Double altitude, Double verticalSpeed) {
            this.position = position;
            this.speed = speed;
            this.bearing = bearing;
            this.reportTimestampUTC = ((reportTimestampUTC / 10000000) - 11644473600l) * 1000;
            this.altitude = altitude;
            this.verticalSpeed = verticalSpeed;
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
                    ", reportTimestampUTC=" + reportTimestampUTC +
                    ", altitude=" + altitude +
                    ", verticalSpeed=" + verticalSpeed +
                    "} time delta=" + getAgeMs();
        }
    }
}

package com.valxp.app.infiniteflightwatcher;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.valxp.app.infiniteflightwatcher.model.Fleet;
import com.valxp.app.infiniteflightwatcher.model.Flight;
import com.valxp.app.infiniteflightwatcher.model.Regions;
import com.valxp.app.infiniteflightwatcher.model.Users;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener, GoogleMap.OnCameraChangeListener {

    public static final long FLIGHT_MAX_LIFETIME_SECONDS = 60 * 3;
    public static final long MAX_INTERPOLATE_DURATION_MS = FLIGHT_MAX_LIFETIME_SECONDS * 1000;
    public static final long MINIMUM_INTERPOLATION_SPEED_KTS = 40;
    public static final int REFRESH_UI_MS = 1000 / 15;
    public static final int REFRESH_API_MS = 8 * 1000;
    public static final int REFRESH_INFO_MS = 2 * 1000;
    public static final double KTS_TO_M_PER_S = .52;

    public static final double MAP_ZOOM_CLUSTER = 5.5; // below 5.5 zoom, cluster markers

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private UpdateThread mUpdateThread;
    private Fleet mFleet;
    private Handler mUIRefreshHandler;
    private long mLastTimeInfoUpdated;
    private TextView mNobodyPlayingText;
    private TextView mRefreshingText;
    private AirplaneBitmapProvider mBitmapProvider;
    private Marker mLastVisibleMarker = null;
    private InfoPane mInfoPane;
    private Regions mRegions;
    private float mLastZoom;
    private int mDrawRate = REFRESH_UI_MS;
    private Runnable mUpdateRunnable;
    private boolean mClusterMode = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();
        mFleet = new Fleet();
        mBitmapProvider = new AirplaneBitmapProvider();
        mInfoPane = (InfoPane) findViewById(R.id.info_pane);
        Button button = (Button) findViewById(R.id.toggleMapType);
        mNobodyPlayingText = (TextView) findViewById(R.id.nobody_is_playing);
        mRefreshingText = (TextView) findViewById(R.id.refreshing_data);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int newType = mMap.getMapType() + 1;
                if (newType > GoogleMap.MAP_TYPE_HYBRID)
                    newType = GoogleMap.MAP_TYPE_NORMAL;
                mMap.setMapType(newType);
            }
        });
        mRegions = new Regions(this);
        mMap.setOnMarkerClickListener(this);
        mMap.setOnMapClickListener(this);
        mMap.setOnCameraChangeListener(this);
        AppUpdater.checkUpdate(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TimeProvider.synchronizeWithInternet();
        setUpMapIfNeeded();
        // Update thread is a thread that will refresh the API data every 8 seconds
        if (mUpdateThread != null && mUpdateThread.isAlive())
            mUpdateThread.requestStop();
        mUpdateThread = new UpdateThread();
        mUpdateThread.start();

        // Refresh handler will call the map drawing on the UI thread every 15th of a second.
        if (mUIRefreshHandler != null)
            mUIRefreshHandler.removeCallbacks(null);
        mUIRefreshHandler = new Handler();
        mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (mFleet) {
                        updateMap();
                    }
                    if (!mClusterMode)
                        mUIRefreshHandler.postDelayed(this, mDrawRate);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        };
        refreshUINow();
    }

    private void refreshUINow() {
        if (mUIRefreshHandler != null && mUpdateRunnable != null)
            mUIRefreshHandler.post(mUpdateRunnable);
    }

    @Override
    protected void onPause() {
        mUpdateThread.requestStop();
        mUpdateThread = null;
        mUIRefreshHandler.removeCallbacks(null);
        mUIRefreshHandler = null;
        super.onPause();
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
        }
    }

    private void updateMap() {
        Flight selectedFlight = null;
        mNobodyPlayingText.setVisibility((mFleet.getActiveFleetSize() <= 0 && mFleet.isUpToDate()) ? View.VISIBLE : View.GONE);
        boolean updateInfo = false;
        long now = TimeProvider.getTime();
        if (now - mLastTimeInfoUpdated > REFRESH_INFO_MS) {
            mLastTimeInfoUpdated = now;
            updateInfo = true;
        }

        mRegions.draw(mMap, mFleet, mClusterMode);

        LatLngBounds boundaries = mMap.getProjection().getVisibleRegion().latLngBounds;

        for (Map.Entry<Users.User, Flight> flightEntry : mFleet.getFleet().entrySet()) {
            Flight flight = flightEntry.getValue();

            if (mClusterMode) {
                flight.removeMarker();
                flight.removeAproxTrail();
                flight.removeHistoryTrail();
                continue;
            }

            // Using this loop to locate the selected flight
            if (mLastVisibleMarker != null && flight.getMarker() != null && flight.getMarker().equals(mLastVisibleMarker)) {
                selectedFlight = flight;
            }
            // Block on this flight to make sure the webservice is not busy writing in it in the same time
            synchronized (flight) {
                LongSparseArray<Flight.FlightData> dataHistory = flight.getFlightHistory();
                if (dataHistory.size() <= 0)
                    continue;
                // We get the last location
                Flight.FlightData data = dataHistory.valueAt(dataHistory.size() - 1);
                Marker lastMarker = flight.getMarker();
                LatLng position = flight.getAproxLocation();
                // If we are outside the map, we don't need to show the marker
                if (!boundaries.contains(position)
                    && (lastMarker == null || !boundaries.contains(lastMarker.getPosition()))) {
                    flight.removeMarker();
                    flight.removeAproxTrail();
                    flight.removeHistoryTrail();
                    if (flight == selectedFlight)
                        selectedFlight = null;
                    continue;
                }
                if (position.equals(data.position)) {
                    flight.removeAproxTrail();
                }
                // Marker update/creation
                if (lastMarker != null) {
                    lastMarker.setPosition(position);
                    lastMarker.setRotation(data.bearing.floatValue());
                } else {
                    Marker marker = mMap.addMarker(new MarkerOptions()
                            .position(position)
                            .rotation(data.bearing.floatValue())
                            .icon(mBitmapProvider.getAsset(flight))
                            .anchor(.5f, .5f)
                            .flat(true)); // Flat will keep the rotation based on the north
                    flight.setMarker(marker);
                }
            }
        }

        // We still have a selected marker but the flight is not there. Hiding info.
        if (selectedFlight == null && mLastVisibleMarker != null) {
            mLastVisibleMarker = null;
            hideInfoPane();
        }
        // Updating selected marker
        if (selectedFlight != null && mLastVisibleMarker != null) {
            synchronized (selectedFlight) {
                LongSparseArray<Flight.FlightData> dataHistory = selectedFlight.getFlightHistory();
                Flight.FlightData data = dataHistory.valueAt(dataHistory.size() - 1);
                // Only update that stuff once in a while
                if (updateInfo) {
                    if (!showInfoPane(selectedFlight))
                        mLastTimeInfoUpdated += REFRESH_INFO_MS;
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(mLastVisibleMarker.getPosition()));
                }
                long delta = Math.min(now - data.reportTimestampUTC, MAX_INTERPOLATE_DURATION_MS);
                if (delta > REFRESH_UI_MS) {
                    StrokedPolyLine line = selectedFlight.getAproxTrail();
                    LatLng newPos = mLastVisibleMarker.getPosition();
                    if (line == null) {
                        selectedFlight.setAproxTrail(new StrokedPolyLine(mMap, data.speed, data.altitude, data.position, newPos));
                    } else {
                        line.update(data.speed, data.altitude);
                        line.setPoints(data.position, newPos);
                    }
                }
                updatePath(selectedFlight);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }



    private Flight flightForMarker(Marker marker) {
        if (marker == null)
            return null;
            for (Map.Entry<Users.User, Flight> flightEntry : mFleet.getFleet().entrySet()) {
                if (marker.equals(flightEntry.getValue().getMarker())) {
                    return flightEntry.getValue();
                }
            }
        return null;
    }

    private void setPathVisible(Marker marker) {
        // first hide the last path
        if (mLastVisibleMarker != null && mLastVisibleMarker != marker) {
            Flight oldFlight = flightForMarker(mLastVisibleMarker);
            if (oldFlight != null) {
                oldFlight.removeHistoryTrail();
                oldFlight.removeAproxTrail();
            }
            mLastVisibleMarker = null;
        }
        if (marker != null) {
            mLastVisibleMarker = marker;
            Flight flight = flightForMarker(marker);
            if (flight != null) {
                synchronized (flight) {
                    requestPathUpdate(flight);
                    updatePath(flight);
                }
            }
        }
    }


    private void updatePath(Flight flight) {
            ArrayList<StrokedPolyLine> trail = (ArrayList<StrokedPolyLine>) flight.getHistoryTrail();
            if (trail == null)
                trail = new ArrayList<StrokedPolyLine>();
            LongSparseArray<Flight.FlightData> history = flight.getFlightHistory();
            if (history == null || history.size() < 2) {
                flight.removeHistoryTrail();
            }
            if (trail.size() == 0 || history.size() < 2 ||
                    trail.size() != history.size() - 1 ||
                    !history.valueAt(history.size() - 2).position.equals(trail.get(trail.size() - 1).getPoints().get(0)) ||
                    !history.valueAt(history.size() - 1).position.equals(trail.get(trail.size() - 1).getPoints().get(1))) {
                for (int i = 0; i < history.size() - 1; ++i) {
                    StrokedPolyLine oldLine = null;
                    if (trail.size() > i)
                        oldLine = trail.get(i);
                    if (oldLine == null || !oldLine.getPoints().get(0).equals(history.valueAt(i).position) || !oldLine.getPoints().get(1).equals(history.valueAt(i+1).position)) {
                        if (oldLine != null)
                            oldLine.remove();
                        StrokedPolyLine line = new StrokedPolyLine(mMap, history.valueAt(i), history.valueAt(i + 1));
                        if (oldLine == null)
                            trail.add(line);
                        else
                            trail.set(i, line);
                    }
                }
            }
            flight.setHistoryTrail(trail);
    }


    @Override
    public boolean onMarkerClick(Marker marker) {
        synchronized (mFleet) {
            setPathVisible(marker);
        }

        showInfoPane(marker);
        mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
        mRegions.onMarkerClick(mMap, marker);
        return true;
    }

    @Override
    public void onMapClick(LatLng latLng) {
        synchronized (mFleet) {
            setPathVisible(null);
        }
        hideInfoPane();
        if (mClusterMode) {
            mRegions.onMapClick(mMap, latLng);
        }
    }

    private void requestPathUpdate(Flight flight) {
        flight.setNeedsUpdate(true);
        if (mUpdateThread != null)
            mUpdateThread.requestWake();
    }

    private void hideInfoPane() {
        mInfoPane.hide();
    }

    private boolean showInfoPane(Flight flight) {
        return mInfoPane.show(flight);
    }

    private void showInfoPane(Marker marker) {
        Flight found = flightForMarker(marker);
        if (found != null) {
            synchronized (found) {
                showInfoPane(found);
            }
        }
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        if ((cameraPosition.zoom - MAP_ZOOM_CLUSTER) * (mLastZoom - MAP_ZOOM_CLUSTER) < 0) {
            mLastZoom = cameraPosition.zoom;
            mClusterMode = cameraPosition.zoom <= MAP_ZOOM_CLUSTER;
            Log.d("MapsActivity", "Camera zoom " + cameraPosition.zoom + " Cluster mode " + (mClusterMode ? "enabled" : "disabled"));
            refreshUINow();
        }
    }

    private class UpdateThread extends Thread {
        private boolean mStop = false;

        @Override
        public void run() {
            while (!mStop) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mRefreshingText.setVisibility(View.VISIBLE);
                        mRefreshingText.setText(R.string.refreshing_data);
                    }
                });
                final Runnable toRunOnUI = mFleet.updateFleet(FLIGHT_MAX_LIFETIME_SECONDS);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (toRunOnUI != null)
                            toRunOnUI.run();
                        int fleetSize = mFleet.getActiveFleetSize();
                        mRefreshingText.setVisibility(fleetSize <= 0 ? View.GONE : View.VISIBLE);
                        mRefreshingText.setText(getResources().getQuantityString(R.plurals.N_users_online, fleetSize, fleetSize));
                        if (mClusterMode)
                            refreshUINow();
                    }
                });
                try {
                    Thread.sleep(REFRESH_API_MS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void requestStop() {
            mStop = true;
            super.interrupt();
        }

        public void requestWake() {
            super.interrupt();
        }

        @Override
        public void interrupt() {
            requestStop();
        }
    }

}
package com.valxp.app.infiniteflightwatcher;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.util.LongSparseArray;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.valxp.app.infiniteflightwatcher.adapters.MainListAdapter;
import com.valxp.app.infiniteflightwatcher.model.Fleet;
import com.valxp.app.infiniteflightwatcher.model.Flight;
import com.valxp.app.infiniteflightwatcher.model.Regions;
import com.valxp.app.infiniteflightwatcher.model.Users;

import java.util.ArrayList;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener, GoogleMap.OnCameraChangeListener, ExpandableListView.OnChildClickListener, TouchableMapFragment.TouchableWrapper.UpdateMapAfterUserInteraction{

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
    private ProgressBar mProgress;
    private ExpandableListView mExpandableList;
    private DrawerLayout mDrawer;
    private View mDrawerHandle;
    private MainListAdapter mListAdapter;
    private AirplaneBitmapProvider mBitmapProvider;
    private Marker mLastVisibleMarker = null;
    private InfoPane mInfoPane;
    private Regions mRegions;
    private float mLastZoom;
    private int mDrawRate = REFRESH_UI_MS;
    private Runnable mUpdateRunnable;
    private boolean mClusterMode = true;

    private float dpFromPx(float px) {
        return px / getResources().getDisplayMetrics().density;
    }

    private float pxFromDp(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

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
        mProgress = (ProgressBar) findViewById(R.id.progress);
        mExpandableList = (ExpandableListView) findViewById(R.id.expandableList);
        mDrawer = (DrawerLayout) findViewById(R.id.drawer);
        mDrawerHandle = findViewById(R.id.drawer_handle);
        TextView version = (TextView) findViewById(R.id.version);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version.setText("Version "+pInfo.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        mDrawerHandle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDrawer.openDrawer(Gravity.LEFT);
            }
        });
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

        mListAdapter = new MainListAdapter(this, mFleet, mRegions);
        mExpandableList.setOnChildClickListener(this);
        mExpandableList.setAdapter(mListAdapter);
        mDrawer.setDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mDrawerHandle.getLayoutParams();
                params.setMargins((int) pxFromDp(-6 - slideOffset * 20), (int)pxFromDp(5), 0, 0);
                mDrawerHandle.setLayoutParams(params);
            }

            @Override
            public void onDrawerOpened(View drawerView) {
            }

            @Override
            public void onDrawerClosed(View drawerView) {
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });
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
        if (mMap == null) {
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
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

        mRegions.draw(mMap, mClusterMode);

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
                if (position.equals(data.position)) {
                    flight.removeAproxTrail();
                }
                // Marker update/creation
                if (lastMarker != null) {
                    lastMarker.setPosition(position);
                    lastMarker.setRotation(data.bearing.floatValue());
                } else {
                    flight.createMarker(mMap, mBitmapProvider);
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
                    if (mInfoPane.isFollowing())
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

    private void selectMarker(Marker marker) {
        mInfoPane.setFollow(false);
        synchronized (mFleet) {
            setPathVisible(marker);
        }

        showInfoPane(marker);
        Flight flight = flightForMarker(marker);
        if (flight != null) {
            flight.zoomIn(mMap, new GoogleMap.CancelableCallback() {
                @Override
                public void onFinish() {
                    mInfoPane.setFollow(true);
                }
                @Override
                public void onCancel() {
                }
            });
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        mInfoPane.setFollow(false);
        synchronized (mFleet) {
            setPathVisible(marker);
        }

        showInfoPane(marker);
        Flight flight = flightForMarker(marker);
        if (flight != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLng(flight.getAproxLocation()), new GoogleMap.CancelableCallback() {
                @Override
                public void onFinish() {
                    mInfoPane.setFollow(true);
                }
                @Override
                public void onCancel() {
                    mInfoPane.setFollow(true);

                }
            });
        }
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

    private void refreshList() {
        Log.d("MapsActivity", "Refreshing list");
        boolean regionsExpanded = mExpandableList.isGroupExpanded(0);
        boolean usersExpanded = mExpandableList.isGroupExpanded(1);
        int pos = mExpandableList.getFirstVisiblePosition();
        mListAdapter = new MainListAdapter(this, mFleet, mRegions);
        mExpandableList.setAdapter(mListAdapter);
        if (regionsExpanded)
            mExpandableList.expandGroup(0);
        if (usersExpanded)
            mExpandableList.expandGroup(1);
        mExpandableList.setSelection(pos);
        mExpandableList.setOnChildClickListener(this);
    }

    @Override
    public boolean onChildClick(ExpandableListView expandableListView, View view, int i, int i2, long l) {
        Object tag = view.getTag();
        if (tag == null)
            return false;
        if (i == MainListAdapter.REGIONS_INDEX) {
            Regions.Region region = (Regions.Region) tag;
            mDrawer.closeDrawers();
            region.zoomOnRegion(mMap);
            synchronized (mFleet) {
                setPathVisible(null);
            }
            hideInfoPane();
        } else if (i == MainListAdapter.USERS_INDEX) {
            final Flight flight = (Flight) tag;
            mDrawer.closeDrawers();
            Regions.Region region = mRegions.regionContainingPoint(flight.getAproxLocation());
            if (region != null && mClusterMode) {
                region.showInside();
                region.zoomOnRegion(mMap, new GoogleMap.CancelableCallback() {
                    @Override
                    public void onFinish() {
                        flight.createMarker(mMap, mBitmapProvider);
                        selectMarker(flight.getMarker());
                    }

                    @Override
                    public void onCancel() {
                    }
                });

            } else {
                flight.createMarker(mMap, mBitmapProvider);
                selectMarker(flight.getMarker());
            }
        }
        return true;
    }

    @Override
    public void onUpdateMapAfterUserInteraction() {
        mInfoPane.setFollow(false);
    }

    private class UpdateThread extends Thread {
        private boolean mStop = false;

        @Override
        public void run() {
            while (!mStop) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgress.setVisibility(View.VISIBLE);
                    }
                });
                final Runnable toRunOnUI = mFleet.updateFleet(FLIGHT_MAX_LIFETIME_SECONDS);
                mRegions.updateCount(mFleet);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (toRunOnUI != null)
                            toRunOnUI.run();
                        mProgress.setVisibility(View.GONE);
                        if (mClusterMode)
                            refreshUINow();
                        refreshList();
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
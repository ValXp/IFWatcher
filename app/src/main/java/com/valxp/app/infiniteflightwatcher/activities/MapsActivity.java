package com.valxp.app.infiniteflightwatcher.activities;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v4.util.LongSparseArray;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ExpandableListView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.maps.android.geometry.Bounds;
import com.valxp.app.infiniteflightwatcher.AirplaneBitmapProvider;
import com.valxp.app.infiniteflightwatcher.ForeFlightClient;
import com.valxp.app.infiniteflightwatcher.InfoPane;
import com.valxp.app.infiniteflightwatcher.R;
import com.valxp.app.infiniteflightwatcher.StrokedPolyLine;
import com.valxp.app.infiniteflightwatcher.TimeProvider;
import com.valxp.app.infiniteflightwatcher.TouchableMapFragment;
import com.valxp.app.infiniteflightwatcher.adapters.MainListAdapter;
import com.valxp.app.infiniteflightwatcher.heatmap.HeatMapTileProvider;
import com.valxp.app.infiniteflightwatcher.model.Fleet;
import com.valxp.app.infiniteflightwatcher.model.Flight;
import com.valxp.app.infiniteflightwatcher.model.Regions;
import com.valxp.app.infiniteflightwatcher.model.Server;
import com.valxp.app.infiniteflightwatcher.model.Users;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener, GoogleMap.OnCameraChangeListener, ExpandableListView.OnChildClickListener, TouchableMapFragment.TouchableWrapper.UpdateMapAfterUserInteraction, GoogleMap.InfoWindowAdapter, CompoundButton.OnCheckedChangeListener, ForeFlightClient.GPSListener {

    public static final long FLIGHT_MAX_LIFETIME_SECONDS = 60 * 3;
    public static final long MAX_INTERPOLATE_DURATION_MS = FLIGHT_MAX_LIFETIME_SECONDS * 1000;
    public static final long MINIMUM_INTERPOLATION_SPEED_KTS = 40;
    public static final int REFRESH_UI_MS = 1000 / 20;
    public static final int REFRESH_API_MS = 8 * 1000;
    public static final int REFRESH_INFO_MS = 2 * 1000;
    public static final double KTS_TO_M_PER_S = .52;

    public static final double MAP_ZOOM_CLUSTER = 5.5; // below 5.5 zoom, cluster markers

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private UpdateThread mUpdateThread;
    private Fleet mFleet;
    private HashMap<String, Server> mServers;
    private Handler mUIRefreshHandler;
    private long mLastTimeInfoUpdated;
    private TextView mNobodyPlayingText;
    private ProgressBar mProgress;
    private ExpandableListView mExpandableList;
    private DrawerLayout mDrawer;
    private View mDrawerHandle;
    private CheckBox mEnableOverlay;
    private MainListAdapter mListAdapter;
    private AirplaneBitmapProvider mBitmapProvider;
    private Marker mLastVisibleMarker = null;
    private InfoPane mInfoPane;
    private Regions mRegions;
    private float mLastZoom;
    private int mDrawRate = REFRESH_UI_MS;
    private Runnable mUpdateRunnable;
    private boolean mClusterMode = true;
    private HeatMapTileProvider mHeatMapTileProvider;
    private TileOverlay mTileOverlay;
    private String mServerId;
    private ForeFlightClient mForeFlightClient;


    private float pxFromDp(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null) {
            mServerId = intent.getStringExtra(ServerChooserActivity.INTENT_SERVER_ID);
        }

        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();
        mFleet = new Fleet();
        mBitmapProvider = new AirplaneBitmapProvider(this);
        mInfoPane = (InfoPane) findViewById(R.id.info_pane);
        Button button = (Button) findViewById(R.id.toggleMapType);
        mNobodyPlayingText = (TextView) findViewById(R.id.nobody_is_playing);
        mProgress = (ProgressBar) findViewById(R.id.progress);
        mExpandableList = (ExpandableListView) findViewById(R.id.expandableList);
        mDrawer = (DrawerLayout) findViewById(R.id.drawer);
        mDrawerHandle = findViewById(R.id.drawer_handle);
        mEnableOverlay = (CheckBox) findViewById(R.id.enable_overlay);
        mEnableOverlay.setOnCheckedChangeListener(this);
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

        mListAdapter = new MainListAdapter(this, mFleet, mRegions, mServers);
        mExpandableList.setOnChildClickListener(this);
        mExpandableList.setAdapter(mListAdapter);
        mDrawer.setDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mDrawerHandle.getLayoutParams();
                params.setMargins((int) pxFromDp(-6 - slideOffset * 20), (int) pxFromDp(5), 0, 0);
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
        mMap.setInfoWindowAdapter(this);
        mForeFlightClient = new ForeFlightClient(this, this);
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

        mForeFlightClient.start();

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
                    mRegions.updateMETAR();
                    if (!mClusterMode && mUIRefreshHandler != null)
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
        mForeFlightClient.stopClient();
        super.onPause();
    }

    private void setUpMapIfNeeded() {
        if (mMap == null) {
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
        }
        if (mHeatMapTileProvider == null) {
            mHeatMapTileProvider = new HeatMapTileProvider();
        }
        if (mTileOverlay == null)
            mTileOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mHeatMapTileProvider));
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

        LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;

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

                if (bounds.contains(position) || flight == selectedFlight) {
                    // Marker update/creation
                    if (lastMarker != null) {
                        lastMarker.setPosition(position);
                        lastMarker.setRotation(data.bearing.floatValue());
                    } else {
                        flight.createMarker(mMap, mBitmapProvider);
                    }
                } else if (lastMarker != null) {
                    flight.removeMarker();
                }
            }
        }

        // We still have a selected marker but the flight is not there. Hiding info.
        if (selectedFlight == null && mLastVisibleMarker != null) {
            mLastVisibleMarker = null;
            unselectFlights();
        }
        // Updating selected marker
        if (selectedFlight != null && mLastVisibleMarker != null) {
            synchronized (selectedFlight) {
                LongSparseArray<Flight.FlightData> dataHistory = selectedFlight.getFlightHistory();
                Flight.FlightData data = dataHistory.valueAt(dataHistory.size() - 1);
                // Only update that stuff once in a while
                if (updateInfo) {
                    selectedFlight.setNeedsUpdate(true);
                    selectedFlight.getUser().markForUpdate();
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

    private void unselectFlights() {
        synchronized (mFleet) {
            setPathVisible(null);
            for (Map.Entry<Users.User, Flight> entry : mFleet.getFleet().entrySet()) {
                Flight f = entry.getValue();
                f.selectMarker(mMap, mBitmapProvider, false);
            }
        }
        mInfoPane.hide();
    }

    private void selectFlight(Flight flight) {
        mInfoPane.setFollow(false);
        synchronized (mFleet) {
            setPathVisible(null);
            for (Map.Entry<Users.User, Flight> entry : mFleet.getFleet().entrySet()) {
                Flight f = entry.getValue();
                f.selectMarker(mMap, mBitmapProvider, false);
            }
            flight.selectMarker(mMap, mBitmapProvider, true);
            setPathVisible(flight.getMarker());
        }
        showInfoPane(flight);
    }

    private void selectMarkerFromUI(Flight flight) {
        selectFlight(flight);
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

    @Override
    public boolean onMarkerClick(Marker marker) {
        Flight flight = flightForMarker(marker);
        if (flight == null) {
            mRegions.onMarkerClick(mMap, marker);
            return true;
        }
        selectFlight(flight);
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
        return true;
    }

    @Override
    public void onMapClick(LatLng latLng) {
        unselectFlights();
        if (mClusterMode) {
            mRegions.onMapClick(mMap, latLng);
        }
    }

    private void requestPathUpdate(Flight flight) {
        flight.setNeedsUpdate(true);
        if (mUpdateThread != null)
            mUpdateThread.requestWake();
    }

    private boolean showInfoPane(Flight flight) {
        return mInfoPane.show(flight);
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        if ((cameraPosition.zoom - MAP_ZOOM_CLUSTER) * (mLastZoom - MAP_ZOOM_CLUSTER) < 0) {
            mLastZoom = cameraPosition.zoom;
            mClusterMode = cameraPosition.zoom <= MAP_ZOOM_CLUSTER;
            Log.d("MapsActivity", "Camera zoom " + cameraPosition.zoom + " Cluster mode " + (mClusterMode ? "enabled" : "disabled"));
            refreshUINow();
        }
        if ((!mEnableOverlay.isChecked() || mClusterMode) && mTileOverlay != null) {
            mTileOverlay.remove();
            mTileOverlay = null;
            mHeatMapTileProvider.removeAllHeatMaps();
        } else if (mEnableOverlay.isChecked() && !mClusterMode) {
            boolean hasChanged = false;
            VisibleRegion vg = mMap.getProjection().getVisibleRegion();
            Bounds camBounds = new Bounds(vg.farLeft.longitude, vg.farRight.longitude, vg.nearLeft.latitude, vg.farLeft.latitude);
            for (Regions.Region region : mRegions) {
                if (region.isContainedIn(camBounds)) {
                    if (!mHeatMapTileProvider.containsHeatMap(region.getHeatmap())) {
                        mHeatMapTileProvider.addHeatMap(region.getHeatmap());
                        hasChanged = true;
                    }
                } else if (region.hasHeatmap() && mHeatMapTileProvider.containsHeatMap(region.getHeatmap())) {
                    mHeatMapTileProvider.removeHeatMap(region.getHeatmap());
                    hasChanged = true;
                }
            }
            if (hasChanged && mTileOverlay != null) {
                mTileOverlay.remove();
                mTileOverlay = null;
            }
            if (mTileOverlay == null)
                mTileOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mHeatMapTileProvider));
        }
    }

    private void refreshList() {
        Log.d("MapsActivity", "Refreshing list");
        int groupCount = mListAdapter.getGroupCount();
        ArrayList<Boolean> expandedGroups = new ArrayList<Boolean>();
        for (int i = 0; i < groupCount; ++i) {
            expandedGroups.add(mExpandableList.isGroupExpanded(i));
        }
        int pos = mExpandableList.getFirstVisiblePosition();
        mListAdapter = new MainListAdapter(this, mFleet, mRegions, mServers);
        mExpandableList.setAdapter(mListAdapter);
        for (int i = 0; i < groupCount; ++i) {
            if (expandedGroups.get(i))
                mExpandableList.expandGroup(i);
        }
        mExpandableList.setSelection(pos);
        mExpandableList.setOnChildClickListener(this);
    }

    @Override
    public boolean onChildClick(ExpandableListView expandableListView, View view, int i, int i2, long l) {
        Object tag = view.getTag();
        if (tag == null)
            return false;
        Regions.Region region;
        switch (i) {
            case MainListAdapter.REGIONS_INDEX:
                region = (Regions.Region) tag;
                mDrawer.closeDrawers();
                region.zoomOnRegion(mMap);
                unselectFlights();
                break;
            case MainListAdapter.USERS_INDEX:
                final Flight flight = (Flight) tag;
                mDrawer.closeDrawers();
                region = mRegions.regionContainingPoint(flight.getAproxLocation());
                if (region != null && mClusterMode) {
                    region.showInside();
                    region.zoomOnRegion(mMap, new GoogleMap.CancelableCallback() {
                        @Override
                        public void onFinish() {
                            flight.createMarker(mMap, mBitmapProvider);
                            selectMarkerFromUI(flight);
                        }

                        @Override
                        public void onCancel() {
                        }
                    });

                } else {
                    flight.createMarker(mMap, mBitmapProvider);
                    selectMarkerFromUI(flight);
                }
                break;
            case MainListAdapter.SERVERS_INDEX:
                mDrawer.closeDrawers();
                mFleet.selectServer((Server)tag);
                if (mUpdateThread != null)
                    mUpdateThread.requestWake();
                refreshList();
                break;
        }
        return true;
    }

    @Override
    public void onUpdateMapAfterUserInteraction() {
        mInfoPane.setFollow(false);
    }

    @Override
    public View getInfoWindow(Marker marker) {
        return getLayoutInflater().inflate(R.layout.empty, null);
    }

    @Override
    public View getInfoContents(Marker marker) {
        return null;
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (mTileOverlay != null) {
            mTileOverlay.remove();
            mTileOverlay = null;
        }
        if (!b) {
            mHeatMapTileProvider.removeAllHeatMaps();
        }
        onCameraChange(mMap.getCameraPosition());
    }

    @Override
    public void OnGPSFixReceived(ForeFlightClient.GPSData data) {
        synchronized (mFleet) {
            mFleet.getUsers().addUser(data.ip, "You @ " + data.ip).dontupdate();
            JSONObject flightData = new JSONObject();
            try {
                flightData.put("UserID", data.ip);
                flightData.put("Latitude", data.lon);
                flightData.put("Longitude", data.lat);
                flightData.put("Speed", data.groundSpeed);
                flightData.put("Track", data.heading);
                flightData.put("Altitude", data.altitude);
                flightData.put("LastReportUTC", data.timestamp);
                flightData.put("FlightID", data.ip);
                flightData.put("VerticalSpeed", "0");
                flightData.put("AircraftName", "Unknown");
                flightData.put("CallSign", "Unknown");
                flightData.put("DisplayName", "You");
                mFleet.parseFlight(flightData);
            } catch (JSONException e) {
                e.printStackTrace();
            }
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
                        mProgress.setVisibility(View.VISIBLE);
                    }
                });

                mServers = Server.getServers(mServers);
                if (mFleet.getSelectedServer() == null && mServers != null && mServers.size() > 0) {
                    if (mServerId == null || !mServers.containsKey(mServerId))
                        mFleet.selectServer(mServers.entrySet().iterator().next().getValue());
                    else if (mServerId != null)
                        mFleet.selectServer(mServers.get(mServerId));
                }

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
                while (mFleet.getUsers().doesNeedUpdate() > 0) {
                    mFleet.getUsers().update(false);
                }
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
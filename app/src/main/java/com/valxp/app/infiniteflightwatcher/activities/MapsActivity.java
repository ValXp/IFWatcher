package com.valxp.app.infiniteflightwatcher.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
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
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
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
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.geometry.Bounds;
import com.valxp.app.infiniteflightwatcher.AirplaneBitmapProvider;
import com.valxp.app.infiniteflightwatcher.ForeFlightClient;
import com.valxp.app.infiniteflightwatcher.InfoPane;
import com.valxp.app.infiniteflightwatcher.MarkerAnimation;
import com.valxp.app.infiniteflightwatcher.R;
import com.valxp.app.infiniteflightwatcher.StrokedPolyLine;
import com.valxp.app.infiniteflightwatcher.TimeProvider;
import com.valxp.app.infiniteflightwatcher.TouchableMapFragment;
import com.valxp.app.infiniteflightwatcher.Utils;
import com.valxp.app.infiniteflightwatcher.adapters.MainListAdapter;
import com.valxp.app.infiniteflightwatcher.heatmap.HeatMapTileProvider;
import com.valxp.app.infiniteflightwatcher.model.ATC;
import com.valxp.app.infiniteflightwatcher.model.Fleet;
import com.valxp.app.infiniteflightwatcher.model.Flight;
import com.valxp.app.infiniteflightwatcher.model.Regions;
import com.valxp.app.infiniteflightwatcher.model.Server;
import com.valxp.app.infiniteflightwatcher.model.Users;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class MapsActivity extends FragmentActivity implements GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener, GoogleMap.OnCameraChangeListener, ExpandableListView.OnChildClickListener, TouchableMapFragment.TouchableWrapper.UpdateMapAfterUserInteraction, CompoundButton.OnCheckedChangeListener, ForeFlightClient.GPSListener {

    public static final long FLIGHT_MAX_LIFETIME_SECONDS = 60 * 3;
    public static final long MINIMUM_INTERPOLATION_SPEED_KTS = 40;
    public static final long MAX_INTERPOLATE_DURATION_MS = FLIGHT_MAX_LIFETIME_SECONDS * 1000;
    public static final int REFRESH_UI_MS = 1000;
    public static final int REFRESH_API_MS = 8 * 1000; // Refresh flights every..
    public static final int REFRESH_INFO_MS = 60 * 1000; // Refresh User info every..
    public static final double KTS_TO_M_PER_S = .52;
    public static final long UI_TIMEOUT_MS = 1000 * 60 * 5; // API requires asking for user interaction every 5min

    public static final double MAP_ZOOM_CLUSTER = 5.5; // below 5.5 zoom, cluster markers

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private UpdateThread mUpdateThread;
    private Fleet mFleet;
    private HashMap<String, Server> mServers;
    private Handler mUIHandler;
    private long mLastTimeInfoUpdated;
    private TextView mNobodyPlayingText;
    private ProgressBar mProgress;
    private ExpandableListView mExpandableList;
    private DrawerLayout mDrawer;
    private View mDrawerHandle;
    private MainListAdapter mListAdapter;
    private AirplaneBitmapProvider mBitmapProvider;
    private InfoPane mInfoPane;
    //private Regions mRegions;
    private float mLastZoom;
    private Runnable mUpdateRunnable;
    private HeatMapTileProvider mHeatMapTileProvider;
    private TileOverlay mTileOverlay;
    private String mServerId;
    private ForeFlightClient mForeFlightClient;
    private Long mLastInteractionTime = TimeProvider.getTime();
    private Flight mSelectedFlight = null;
    private CameraChangeRunnable mCameraChangeRunnable = new CameraChangeRunnable();
    private float mMeterPerDpZoom = 0;
    private Map<String, List<ATC>> mAtcs = null;



    @Override
    public void onUserInteraction(){
        Log.d("UITimeout", "User interaction!");
        mLastInteractionTime = TimeProvider.getTime();
    }

    private void initViews() {
        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();
        mInfoPane = (InfoPane) findViewById(R.id.info_pane);
        Button button = (Button) findViewById(R.id.toggleMapType);
        mNobodyPlayingText = (TextView) findViewById(R.id.nobody_is_playing);
        mNobodyPlayingText.setText(R.string.connecting_to_if);
        mNobodyPlayingText.setVisibility(View.VISIBLE);
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

        mListAdapter = new MainListAdapter(this, mFleet, mAtcs, mServers);
        mExpandableList.setOnChildClickListener(this);
        mExpandableList.setAdapter(mListAdapter);
        mDrawer.setDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mDrawerHandle.getLayoutParams();
                params.setMargins((int) Utils.dpToPx(-6 - slideOffset * 20), (int) Utils.dpToPx(5), 0, 0);
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
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("MapsActivityy", "onCreate");
        Utils.Benchmark.start("onCreate");
        super.onCreate(savedInstanceState);

        Utils.initContext(this);
        MarkerAnimation.initContext(this);

        Flight.ctx = this;

        handleOpenedFromURL();


        mFleet = new Fleet();
        mBitmapProvider = new AirplaneBitmapProvider(this);


        initViews();

        Utils.Benchmark.stopAndLog("onCreate");
    }

    private void handleOpenedFromURL() {
        Intent intent = getIntent();
        if (intent != null) {
            mServerId = intent.getStringExtra(ServerChooserActivity.INTENT_SERVER_ID);
            if (mServerId == null && Intent.ACTION_VIEW.equals(intent.getAction())) {
                Uri uri = intent.getData();
                if (uri != null) {
                    mServerId = uri.getQueryParameter("s");
                    final String flightId = uri.getQueryParameter("f");
                    Log.d("MapsActivity", "FlightId + " + flightId);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (true) {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                if (mFleet != null) {
                                    synchronized (mFleet) {
                                        if (mFleet.isUpToDate()) {
                                            for (final Map.Entry<Users.User, Flight> entry : mFleet.getFleet().entrySet()) {
                                                if (entry.getValue().getFlightID().equalsIgnoreCase(flightId)) {
                                                    Log.d("MapsActivity", "Found flight! User is " + entry.getKey().getName());
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            showFlight(entry.getValue());
                                                        }
                                                    });
                                                    return;
                                                }
                                            }
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MapsActivity.this, "Couldn't find this flight :(", Toast.LENGTH_LONG).show();
                                                }
                                            });
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }).start();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        Log.d("MapsActivityy", "onResume");
        Utils.Benchmark.start("onResume");
        super.onResume();
        TimeProvider.synchronizeWithInternet();
        unselectFlight();
        setUpMapIfNeeded();
        if (mFleet != null)
            mFleet.clearMap();

        // Update thread is a thread that will refresh the API data every 8 seconds
        if (mUpdateThread != null && mUpdateThread.isAlive())
            mUpdateThread.requestStop();
        mUpdateThread = new UpdateThread();
        mUpdateThread.start();

        mForeFlightClient = new ForeFlightClient(this, this);
        mForeFlightClient.start();

        // Refresh handler will call the map drawing on the UI thread every 15th of a second.
        if (mUIHandler != null)
            mUIHandler.removeCallbacks(null);
        mUIHandler = new Handler();
        mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    updateMap();
                    //mRegions.updateMETAR();
                    if (mUIHandler != null) {
                        mUIHandler.removeCallbacks(this); // Making sure we don't call ourselves multiple times
                        mUIHandler.postDelayed(this, REFRESH_UI_MS);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        };
        mMap.setInfoWindowAdapter(new CustomInfoWindowAdapter());
        refreshUINow();
        Utils.Benchmark.stopAndLog("onResume");
    }

    private void refreshUINow() {
        if (mUIHandler != null && mUpdateRunnable != null)
            mUIHandler.post(mUpdateRunnable);
    }

    @Override
    protected void onPause() {
        mUpdateThread.requestStop();
        mUpdateThread = null;
        mUIHandler.removeCallbacks(null);
        mUIHandler = null;
        mForeFlightClient.stopClient();
        mForeFlightClient = null;
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    private void setUpMapIfNeeded() {
        if (mMap == null) {
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(41.0354252,-99.8141516)));
        }
        mMap.clear();
        if (mHeatMapTileProvider == null) {
            mHeatMapTileProvider = new HeatMapTileProvider();
        }
        if (mTileOverlay == null)
            mTileOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mHeatMapTileProvider));
    }

    private void updateMap() {
        Utils.Benchmark.start("updateMap");

        // If the fleet is done loading. And that there are no flights, show a message.
        if (mFleet.isUpToDate()) {
            if (mFleet.getActiveFleetSize() <= 0) {
                mNobodyPlayingText.setText(R.string.nobody_is_playing);
                mNobodyPlayingText.setVisibility(View.VISIBLE);
            } else {
                mNobodyPlayingText.setVisibility(View.GONE);

            }
        }

        boolean updateInfo = false;
        long now = TimeProvider.getTime();

        // Calculate if we should update the info window.
        if (now - mLastTimeInfoUpdated > REFRESH_INFO_MS) {
            mLastTimeInfoUpdated = now;
            updateInfo = true;
        }


        // Updating selected marker
        if (mSelectedFlight != null) {
            synchronized (mSelectedFlight) {
                // Unselect old flight
                if (mSelectedFlight.getAgeMs() / 1000 > FLIGHT_MAX_LIFETIME_SECONDS) {
                    unselectFlight();
                    return;
                }

                // Only update that stuff once in a while
                if (updateInfo) {
                    mSelectedFlight.setNeedsUpdate(true);
                    mSelectedFlight.getUser().markForUpdate();
                }
                if (!showInfoPane(mSelectedFlight))
                    mLastTimeInfoUpdated += REFRESH_INFO_MS;
                if (mInfoPane.isFollowing())
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(mSelectedFlight.getAproxLocation(REFRESH_UI_MS)), REFRESH_UI_MS / 2, null);
            }
        }
        drawPlanes(mMap.getProjection().getVisibleRegion().latLngBounds);
        Utils.Benchmark.stopAndLog("updateMap");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private Flight flightForMarker(Marker marker) {
        if (marker == null)
            return null;
        for (Flight flight : mFleet.getFleet().values()) {
            if (marker.equals(flight.getMarker())) {
                return flight;
            }
        }
        return null;
    }

    private void setPathVisible(Flight flight) {
        if (flight != null) {
            synchronized (flight) {
                requestPathUpdate(flight);
                updatePath(flight);
            }
        } else if (mSelectedFlight != null) {
            mSelectedFlight.removeHistoryTrail();
            mSelectedFlight.removeAproxTrail();
        }
    }


    public void updatePath(Flight flight) {
        Utils.Benchmark.start("updatePath");
        if (flight == null || mSelectedFlight != flight)
            return;
        ArrayList<StrokedPolyLine> trail = (ArrayList<StrokedPolyLine>) flight.getHistoryTrail();
        if (trail == null)
            trail = new ArrayList<>();
        LongSparseArray<Flight.FlightData> history = flight.getFlightHistory();
        if (history == null || history.size() < 2) {
            flight.removeHistoryTrail();
        }
        LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
        if (history != null && history.size() > 1) {
            int historyCursor = 0;
            int trailCursor = 0;
            float minDistance = mMeterPerDpZoom * 50;
            Flight.FlightData pos1, pos2 = null;
            do {
                pos2 = history.valueAt(historyCursor);
                ++historyCursor;
            } while (historyCursor < history.size() - 1 && !bounds.contains(pos2.position));
            // But we still want to have one line that crosses the edge
            if (historyCursor < history.size() - 1 && historyCursor > 1) {
                historyCursor -= 2;
                pos2 = history.valueAt(historyCursor);
            }
            while (historyCursor < history.size()) {
                pos1 = pos2;
                do {
                    pos2 = history.valueAt(historyCursor);
                    historyCursor++;
                } while (historyCursor < history.size() && ((!bounds.contains(pos1.position) && !bounds.contains(pos2.position)) ||
                        SphericalUtil.computeDistanceBetween(pos1.position, pos2.position) < minDistance));

                if (pos1 != null && pos2 != null) {
                    StrokedPolyLine oldLine = null;
                    if (trail.size() > trailCursor)
                        oldLine = trail.get(trailCursor);
                    if (oldLine == null || !oldLine.getPoints().get(0).equals(pos1.position) || !oldLine.getPoints().get(1).equals(pos2.position)) {
                        if (oldLine != null) {
                            oldLine.update(pos1.speed, pos1.altitude);
                            oldLine.setPoints(pos1.position, pos2.position);
                        } else {
                            trail.add(new StrokedPolyLine(mMap, pos1, pos2));
                        }
                    }
                    trailCursor++;
                    if (!bounds.contains(pos2.position) && historyCursor < history.size() - 1) {
                        do {
                            pos2 = history.valueAt(historyCursor);
                            ++historyCursor;
                        } while (historyCursor < history.size() - 1 && !bounds.contains(pos2.position));
                        // But we still want to have one line that crosses the edge
                        if (historyCursor < history.size() - 1 && historyCursor > 1) {
                            historyCursor -= 2;
                            pos2 = history.valueAt(historyCursor);
                        }
                    }
                }
            }
            if (trailCursor < trail.size() - 1) {
                List<StrokedPolyLine> toRemove = trail.subList(trailCursor, trail.size());
                Iterator<StrokedPolyLine> it = toRemove.iterator();
                while (it.hasNext()) {
                    it.next().remove();
                    it.remove();
                }
            }
            flight.setHistoryTrail(trail);
        }
        Utils.Benchmark.stopAndLog("updatePath");
    }

    private void unselectFlight() {
        selectFlight(null);
    }

    private void selectFlight(Flight flight) {
        if (flight == mSelectedFlight)
            return;
        if (mSelectedFlight != null) {

            mSelectedFlight.selectMarker(mMap, mBitmapProvider, false);
            setPathVisible(null);
            mSelectedFlight = null;
        }
        if (flight == null) {
            mInfoPane.hide();
            return;
        }
        mInfoPane.setFollow(false);
        synchronized (mFleet) {
            flight.selectMarker(mMap, mBitmapProvider, true);
            setPathVisible(flight);
            mSelectedFlight = flight;
            mSelectedFlight.setNeedsUpdate(true);
            mSelectedFlight.getUser().markForUpdate();
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
            //mRegions.onMarkerClick(this, mMap, marker);
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
        unselectFlight();
        /*if (mClusterMode) {
            mRegions.onMapClick(this, mMap, latLng);
        }*/
    }

    private void requestPathUpdate(Flight flight) {
        flight.setNeedsUpdate(true);
        if (mUpdateThread != null)
            mUpdateThread.requestWake();
    }

    private boolean showInfoPane(Flight flight) {
        return mInfoPane.show(flight);
    }


    private class CameraChangeRunnable implements Runnable {

        @Override
        public void run() {
            Utils.Benchmark.start("CameraChangeRunnable");
            CameraPosition cameraPosition = mMap.getCameraPosition();
            mMeterPerDpZoom = Utils.meterPerDp(cameraPosition.zoom);
            VisibleRegion vg = mMap.getProjection().getVisibleRegion();
            Bounds camBounds = new Bounds(vg.farLeft.longitude, vg.farRight.longitude, vg.nearLeft.latitude, vg.farLeft.latitude);
            if ((cameraPosition.zoom - MAP_ZOOM_CLUSTER) * (mLastZoom - MAP_ZOOM_CLUSTER) < 0) {
                mLastZoom = cameraPosition.zoom;
                //mClusterMode = cameraPosition.zoom <= MAP_ZOOM_CLUSTER;
                Log.d("MapsActivity", "Camera zoom " + cameraPosition.zoom /*+ " Cluster mode " + (mClusterMode ? "enabled" : "disabled")*/);
            }
            /*if (mClusterMode && mSelectedFlight != null && mRegions.regionContainingPoint(mSelectedFlight.getAproxLocation()) != null) {
                unselectFlight();
            }*/
            /*if ((!mEnableOverlay.isChecked()) && mTileOverlay != null) {
                mTileOverlay.remove();
                mTileOverlay = null;
                mHeatMapTileProvider.removeAllHeatMaps();
            } else if (mEnableOverlay.isChecked()) {
                boolean hasChanged = false;
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
            }*/
            // Do the regions drawing.
            //mRegions.draw(MapsActivity.this, mMap, mAtcs, mClusterMode, camBounds);

            // Update the plane markers
            drawPlanes(vg.latLngBounds);
            Utils.Benchmark.stopAndLog("CameraChangeRunnable");

            if (mSelectedFlight != null)
                updatePath(mSelectedFlight);
        }
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        // Delaying by onCameraChangeEvent to avoid receiving too many successive calls.
        if (mUIHandler != null && mCameraChangeRunnable != null) {
            mUIHandler.removeCallbacks(mCameraChangeRunnable);
            mUIHandler.postDelayed(mCameraChangeRunnable, 500);
        }
    }

    private void drawPlanes(LatLngBounds camBounds) {
        // Loop over the flights to see if they fit in the current viewing region
        if (mFleet == null)
            return;
        synchronized (mFleet) {
            for (Flight flight : mFleet.getFleet().values()) {
                if (flight.getCurrentData() != null) {
                    LatLng aproxLocation = flight.getAproxLocation();
                    Marker marker = flight.getMarker();
                    boolean shouldDraw = camBounds.contains(aproxLocation);// && (!mClusterMode || mRegions.regionContainingPoint(aproxLocation) == null);
                    if (shouldDraw && marker == null) {
                        flight.createMarker(mMap, mBitmapProvider);
                    } else if (marker != null && !shouldDraw) {
                        flight.removeMarker();
                    }
                }
            }
        }
    }

    private void refreshList() {
        Log.d("MapsActivity", "Refreshing list");
        int groupCount = mListAdapter.getGroupCount();
        ArrayList<Boolean> expandedGroups = new ArrayList<>();
        for (int i = 0; i < groupCount; ++i) {
            expandedGroups.add(mExpandableList.isGroupExpanded(i));
        }
        int pos = mExpandableList.getFirstVisiblePosition();
        mListAdapter = new MainListAdapter(this, mFleet, mAtcs, mServers);
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
            case MainListAdapter.USERS_INDEX:
                final Flight flight = (Flight) tag;
                mDrawer.closeDrawers();
                showFlight(flight);
                break;
            case MainListAdapter.ATC_INDEX:
                mDrawer.closeDrawers();
                unselectFlight();
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(((ATC) tag).position, 12);
                mMap.animateCamera(cameraUpdate);
                break;
            case MainListAdapter.SERVERS_INDEX:
                mDrawer.closeDrawers();
                unselectFlight();
                mFleet.selectServer((Server)tag);
                mNobodyPlayingText.setText(R.string.connecting_to_if);
                mNobodyPlayingText.setVisibility(View.VISIBLE);
                if (mUpdateThread != null)
                    mUpdateThread.requestWake();
                refreshList();
                break;
        }
        return true;
    }

    public void showFlight(final Flight flight) {

        /*Regions.Region region = mRegions.regionContainingPoint(flight.getAproxLocation());
        if (region != null && mClusterMode) {
            region.showInside();
            region.zoomOnRegion(this, mMap, new GoogleMap.CancelableCallback() {
                @Override
                public void onFinish() {
                    flight.createMarker(mMap, mBitmapProvider);
                    selectMarkerFromUI(flight);
                }

                @Override
                public void onCancel() {
                }
            });

        } else*/ {
            flight.createMarker(mMap, mBitmapProvider);
            selectMarkerFromUI(flight);
        }
    }

    @Override
    public void onUpdateMapAfterUserInteraction() {
        mInfoPane.setFollow(false);
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
            if (data instanceof ForeFlightClient.TrafficGPSData) {
                ForeFlightClient.TrafficGPSData traffic = (ForeFlightClient.TrafficGPSData) data;
                Flight trafficFlight = null;
                for (Map.Entry<Users.User, Flight> entry : mFleet.getFleet().entrySet()) {
                    if (entry.getValue().getCallSign().equals(traffic.callsign)) {
                        trafficFlight = entry.getValue();
                        break;
                    }
                }
                if (trafficFlight == null) {
                    return;
                }
                Flight.FlightData fdata = new Flight.FlightData(new LatLng(traffic.lat, traffic.lon), traffic.groundSpeed, traffic.heading, traffic.timestamp, traffic.altitude, new Double(traffic.verticalSpeed));
                trafficFlight.addFlightData(fdata);
            } else {
                mFleet.getUsers().addUser(data.ip, "You @ " + data.ip).dontupdate();
                JSONObject flightData = new JSONObject();
                try {
                    flightData.put("UserID", "ForeFlight");
                    flightData.put("Latitude", data.lon);
                    flightData.put("Longitude", data.lat);
                    flightData.put("Speed", data.groundSpeed);
                    flightData.put("Track", data.heading);
                    flightData.put("Altitude", data.altitude);
                    flightData.put("LastReportUTC", data.timestamp);
                    flightData.put("FlightID", "ForeFlight");
                    flightData.put("VerticalSpeed", "0");
                    flightData.put("AircraftName", "Unknown");
                    flightData.put("CallSign", "ForeFlight");
                    flightData.put("DisplayName", "You");
                    flightData.put("LiveryID", "0");
                    mFleet.parseFlight(flightData);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
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
                if (TimeProvider.getTime() -  mLastInteractionTime > UI_TIMEOUT_MS && !Utils.isMyDevice(MapsActivity.this)) {
                    Log.d("UITimeout", "Interaction timeout: " +  (TimeProvider.getTime() -  mLastInteractionTime) + " > " + UI_TIMEOUT_MS);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
                            builder.setTitle(R.string.activity_timeout_title)
                                    .setMessage(R.string.activity_timeout_message)
                                    .setPositiveButton(R.string.activity_timeout_button, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            requestWake();
                                            onUserInteraction();
                                        }
                                    });
                            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialogInterface) {
                                    requestWake();
                                    onUserInteraction();
                                }
                            });
                            builder.create().show();
                        }
                    });
                    sleepwait();
                }

                mServers = Server.getServers(mServers);
                if (mFleet.getSelectedServer() == null && mServers != null && mServers.size() > 0) {
                    if (mServerId == null || !mServers.containsKey(mServerId))
                        mFleet.selectServer(mServers.entrySet().iterator().next().getValue());
                    else if (mServerId != null)
                        mFleet.selectServer(mServers.get(mServerId));
                }

                mAtcs = ATC.getATC(mFleet.getSelectedServer());

                final Runnable toRunOnUI = mFleet.updateFleet(FLIGHT_MAX_LIFETIME_SECONDS);
                //mRegions.updateCount(mFleet);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (toRunOnUI != null)
                            toRunOnUI.run();
                        mProgress.setVisibility(View.GONE);
                        onCameraChange(null); // Faking a camera change to refresh the UI
//                        if (mClusterMode)
//                            refreshUINow();
                        refreshList();
                    }
                });
                if (mFleet.getUsers().doesNeedUpdate() > 0) {
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

        private synchronized void sleepwait() {
            try {
                wait();
            } catch (InterruptedException e) {
                Log.d("UITimeout", "Wait dismissed");
            }
        }

        public synchronized void requestWake() {
            super.interrupt();
        }

        @Override
        public void interrupt() {
            requestStop();
        }

    }

    private class CustomInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {
        @Override
        public View getInfoWindow(Marker marker) {
            return null;
        }

        @Override
        public View getInfoContents(Marker marker) {
            View v = null;
            /*if (mRegions != null && marker != null) {
                v = mRegions.getInfoWindow(MapsActivity.this, mAtcs, marker);
            }*/
            return v;
        }
    }
}
package com.valxp.app.infiniteflightwatcher;

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
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.util.LongSparseArray;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.maps.android.SphericalUtil;
import com.valxp.app.infiniteflightwatcher.model.Fleet;
import com.valxp.app.infiniteflightwatcher.model.Flight;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener {

    private static final long FLIGHT_MAX_LIFETIME_SECONDS = 60 * 3;
    private static final long MAX_INTERPOLATE_DURATION_MS = FLIGHT_MAX_LIFETIME_SECONDS * 1000;
    private static final long MINIMUM_INTERPOLATION_SPEED_KTS = 40;
    private static final int REFRESH_UI_MS = 1000 / 15;
    private static final int REFRESH_API_MS = 8 * 1000;
    private static final int REFRESH_INFO_MS = 2 * 1000;
    //private static final int TRAIL_LENGTH = 50;
    private static final double KTS_TO_M_PER_S = .52;
    private static final double MAX_ALTITUDE = 50000; // Max altitude in ft
    private static final float TRAIL_THICKNESS = 5.0f;

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


    private class InfoPane {
        private TextView mCallSign;
        private TextView mPlane;
        private TextView mSpeed;
        private TextView mAltitude;
        private TextView mLastUpdate;
        private List<View> mSeparators;

        public InfoPane() {
            mCallSign = (TextView) findViewById(R.id.callSign);
            mPlane = (TextView) findViewById(R.id.plane);
            mSpeed = (TextView) findViewById(R.id.speed);
            mAltitude = (TextView) findViewById(R.id.altitude);
            mLastUpdate = (TextView) findViewById(R.id.lastUpdate);
            mSeparators = new ArrayList<View>();
            mSeparators.add(findViewById(R.id.sep1));
            mSeparators.add(findViewById(R.id.sep2));
            mSeparators.add(findViewById(R.id.sep3));
            mSeparators.add(findViewById(R.id.sep4));
            mSeparators.add(findViewById(R.id.sep5));
            hide();
        }

        private void setVisibility(int visibility) {
            mCallSign.setVisibility(visibility);
            mPlane.setVisibility(visibility);
            mSpeed.setVisibility(visibility);
            mAltitude.setVisibility(visibility);
            mLastUpdate.setVisibility(visibility);
            for (View v : mSeparators) {
                v.setVisibility(visibility);
            }
        }

        public void show(String callSign, String plane, Long speed, Long altitude, Long lastUpdateSeconds) {
            setVisibility(View.VISIBLE);
            mCallSign.setText(callSign);
            mPlane.setText(plane);
            mSpeed.setText(speed + " kts");
            mAltitude.setText(altitude + " feet");
            String lastUpdate = "";
            if (lastUpdateSeconds > 0)
                if (lastUpdateSeconds > 60)
                    lastUpdate = (lastUpdateSeconds / 60) + " minute" + (lastUpdateSeconds / 60 > 1 ? "s" : "") + " ago";
                else
                    lastUpdate = lastUpdateSeconds + " second" + (lastUpdateSeconds > 1 ? "s" : "") + " ago";
            mLastUpdate.setText(lastUpdate);
        }

        public void hide() {
            setVisibility(View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();
        mFleet = new Fleet();
        mBitmapProvider = new AirplaneBitmapProvider();
        mInfoPane = new InfoPane();
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

        mMap.setOnMarkerClickListener(this);
        mMap.setOnMapClickListener(this);
        checkUpdate();
    }


    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        if (mUpdateThread != null && mUpdateThread.isAlive())
            mUpdateThread.requestStop();
        mUpdateThread = new UpdateThread();
        mUpdateThread.start();
        if (mUIRefreshHandler != null)
            mUIRefreshHandler.removeCallbacks(null);
        mUIRefreshHandler = new Handler();
        mUIRefreshHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (mFleet) {
                        updateMap();
                    }
                    mUIRefreshHandler.postDelayed(this, REFRESH_UI_MS);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        });
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

    private int valueToColor(double value) {
         value = Math.max(0, Math.min(1, value));
         int alpha = 0xFF;
         int red = 0;
         int green = 0;
         int blue = 0;
         double step = 1 / 5.0;

         if (value <= step * 1) { // blue 100%. Green increasing
             double inRatio = value / step;
             blue = 0xff;
             green = (int) (0xff * inRatio);
         } else if (value <= step * 2) { // Green 100%. blue decreasing
             double inRatio = (value - (1 * step)) / step;
             green = 0xff;
             blue = (int) (0xff * (1 - inRatio));
         } else if (value <= step * 3) { //Green 100%. red increasing
             double inRatio = (value - (2 * step)) / step;
             green = 0xff;
             red = (int) (0xff * inRatio);
         } else if (value <= step * 4) { // Red 100%. green decreasing
             double inRatio = (value - (3 * step)) / step;
             red = 0xff;
             green = (int) (0xff * (1 - inRatio));
         } else {
             red = 0xff;
         }
         return blue | (green << 8) | (red << 16) | (alpha << 24);
     }

    private void updateMap() {
        Flight selectedFlight = null;
        mNobodyPlayingText.setVisibility((mFleet.getActiveFleetSize() <= 0 && mFleet.isUpToDate()) ? View.VISIBLE : View.GONE);
        boolean updateInfo = false;
        long now = new Date().getTime();
        if (now - mLastTimeInfoUpdated > REFRESH_INFO_MS) {
            mLastTimeInfoUpdated = now;
            updateInfo = true;
        }

        LatLngBounds boundaries = mMap.getProjection().getVisibleRegion().latLngBounds;

        for (Map.Entry<String, Flight> flightEntry : mFleet.getFleet().entrySet()) {
            Flight flight = flightEntry.getValue();

            // Using this loop to locate the selected flight
            if (mLastVisibleMarker != null && flight.getMarker() != null && flight.getMarker().equals(mLastVisibleMarker)) {
                selectedFlight = flight;
            }
            synchronized (flight) {
                LongSparseArray<Flight.FlightData> dataHistory = flight.getFlightHistory();
                if (dataHistory.size() <= 0)
                    continue;
                // We get the last location
                Flight.FlightData data = dataHistory.valueAt(dataHistory.size() - 1);
                Marker lastMarker = flight.getMarker();
                // If we are outside the map, we don't need to show the marker
                if (!boundaries.contains(data.position)
                    && (lastMarker == null || !boundaries.contains(lastMarker.getPosition()))) {
                    if (lastMarker != null)
                        lastMarker.remove();
                    Polyline line = flight.getAproxTrail();
                    if (line != null)
                        line.remove();
                    flight.setAproxTrail(null);
                    if (lastMarker == mLastVisibleMarker) {
                        mLastVisibleMarker = null;
                        selectedFlight = null;
                        hideInfoPane();
                        removePath(flight);
                    }
                    flight.setMarker(null);
                    continue;
                }
                // Marker update/creation
                if (lastMarker != null) {
                    // compute estimated new position based on speed
                    // Stop interpolating if data is too old
                    long delta = Math.min(now - data.reportTimestampUTC, MAX_INTERPOLATE_DURATION_MS);
                    // Disable interpolation if going below 40kts (probably taxiing)
                    if (data.speed < MINIMUM_INTERPOLATION_SPEED_KTS) {
                        delta = 0;
                        Polyline line = flight.getAproxTrail();
                        if (line != null) {
                            line.remove();
                        }
                        flight.setAproxTrail(null);
                    }
                    double distanceMeter = (data.speed * KTS_TO_M_PER_S) * (delta / 1000.0);
                    LatLng newPos = SphericalUtil.computeOffset(data.position, distanceMeter, data.bearing);
                    lastMarker.setPosition(newPos);
                    lastMarker.setRotation(data.bearing.floatValue());
                } else {
                    Marker marker = mMap.addMarker(new MarkerOptions().position(data.position)
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
                // Only update that stuff when once in a while
                if (updateInfo) {
                    showInfoPane(selectedFlight);
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(mLastVisibleMarker.getPosition()));
                }
                long delta = Math.min(now - data.reportTimestampUTC, MAX_INTERPOLATE_DURATION_MS);
                if (delta > REFRESH_UI_MS) {
                    Polyline line = selectedFlight.getAproxTrail();
                    LatLng newPos = mLastVisibleMarker.getPosition();
                    if (line == null) {
                        selectedFlight.setAproxTrail(coloredPolyline(data.speed, data.altitude, data.position, newPos));
                    } else {
                        updateLine(line, data.speed, data.altitude);
                        List<LatLng> points = line.getPoints();
                        points.clear();
                        points.add(data.position);
                        points.add(newPos);
                        line.setPoints(points);
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

    private void checkUpdate() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                URL url = null;
                try {
                    url = new URL("http://valxp.net/IFWatcher/version.txt");
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
                try {
                    InputStream stream = url.openStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                    List<String> lines = new ArrayList<String>();
                    String line = null;
                    do {
                        line = reader.readLine();
                        if (line != null)
                            lines.add(line);
                    } while (line != null);
                    if (lines.size() == 0)
                        return;
                    final int newVersion = Integer.decode(lines.get(0));
                    String description = "";
                    Iterator<String> it = lines.iterator();
                    if (it.hasNext())
                        it.next();
                    while (it.hasNext()) {
                        description += it.next() + "\n";
                    }
                    final String changelog = description;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            PackageInfo pInfo = null;
                            try {
                                pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                            } catch (PackageManager.NameNotFoundException e) {
                                e.printStackTrace();
                            }
                            if (newVersion > pInfo.versionCode) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);

                                builder.setTitle("New update available!\nCurrent : v" + pInfo.versionCode + " Latest : v" + newVersion);
                                String message = "Do you want to download the new version ?\n\n";
                                if (changelog.length() > 0)
                                    message += "Changelog :\n" + changelog;
                                builder.setMessage(message);
                                builder.setPositiveButton("Okay!", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://valxp.net/IFWatcher/IFWatcher.apk"));
                                        startActivity(intent);
                                    }
                                });
                                builder.setNegativeButton("Leave me alone", null);
                                builder.create().show();
                            } else {
                                Toast.makeText(MapsActivity.this, "You are up to date ! (v" + pInfo.versionCode + ")", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private Flight flightForMarker(Marker marker) {
        if (marker == null)
            return null;
            for (Map.Entry<String, Flight> flightEntry : mFleet.getFleet().entrySet()) {
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
                synchronized (oldFlight) {
                    removePath(oldFlight);
                }
                Polyline line = oldFlight.getAproxTrail();
                if (line != null) {
                    line.remove();
                    oldFlight.setAproxTrail(null);
                }
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

    private void removePath(Flight flight) {
            List<Polyline> lines = flight.getHistoryTrail();
            if (lines != null) {
                for (Polyline line : lines) {
                    line.remove();
                }
            }
            flight.setHistoryTrail(null);
    }

    private void updatePath(Flight flight) {
            ArrayList<Polyline> trail = (ArrayList<Polyline>) flight.getHistoryTrail();
            if (trail == null)
                trail = new ArrayList<Polyline>();
            LongSparseArray<Flight.FlightData> history = flight.getFlightHistory();
            if (history == null || history.size() < 2)
                removePath(flight);
            if (trail.size() == 0 || history.size() < 2 ||
                    trail.size() != history.size() - 1 ||
                    !history.valueAt(history.size() - 2).position.equals(trail.get(trail.size() - 1).getPoints().get(0)) ||
                    !history.valueAt(history.size() - 1).position.equals(trail.get(trail.size() - 1).getPoints().get(1))) {
                for (int i = 0; i < history.size() - 1; ++i) {
                    Polyline oldLine = null;
                    if (trail.size() > i)
                        oldLine = trail.get(i);
                    if (oldLine == null || !oldLine.getPoints().get(0).equals(history.valueAt(i).position) || !oldLine.getPoints().get(1).equals(history.valueAt(i+1).position)) {
                        if (oldLine != null)
                            oldLine.remove();
                        Polyline line = polylineFromFlightData(history.valueAt(i), history.valueAt(i + 1));
                        if (oldLine == null)
                            trail.add(line);
                        else
                            trail.set(i, line);
                    }
                }
            }
            flight.setHistoryTrail(trail);
    }

    private void updateLine(Polyline line, double speed, double altitude) {
        line.setColor(valueToColor(altitude / MAX_ALTITUDE));
        line.setWidth(1.0f + (float)(speed / 100.0));
    }

    private Polyline coloredPolyline(double speed, double altitude, LatLng first, LatLng second) {
        PolylineOptions path = new PolylineOptions();
        path.width(1.0f + (float)(speed / 100.0));
        path.color(valueToColor(altitude / MAX_ALTITUDE));
        path.add(first);
        path.add(second);
        return mMap.addPolyline(path);
    }

    private Polyline polylineFromFlightData(Flight.FlightData first, Flight.FlightData second) {
        return coloredPolyline(first.speed, first.altitude, first.position, second.position);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        synchronized (mFleet) {
            setPathVisible(marker);
        }

        showInfoPane(marker);
        mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
        return true;
    }

    @Override
    public void onMapClick(LatLng latLng) {
        synchronized (mFleet) {
            setPathVisible(null);
        }
        hideInfoPane();
    }

    private void requestPathUpdate(Flight flight) {
        flight.setNeedsUpdate(true);
        mUpdateThread.requestWake();
    }

    private void hideInfoPane() {
        mInfoPane.hide();
    }

    private void showInfoPane(Flight flight) {
        LongSparseArray<Flight.FlightData> history = flight.getFlightHistory();
        Flight.FlightData lastData = history.valueAt(history.size() - 1);
        mInfoPane.show(flight.getCallSign(), flight.getAircraftName(), lastData.speed.longValue(), lastData.altitude.longValue(), lastData.getAgeMs() / 1000);
    }

    private void showInfoPane(Marker marker) {
        Flight found = flightForMarker(marker);
        if (found != null) {
            synchronized (found) {
                showInfoPane(found);
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
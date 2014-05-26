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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;
import com.valxp.app.infiniteflightwatcher.model.Fleet;
import com.valxp.app.infiniteflightwatcher.model.Flight;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapsActivity extends FragmentActivity {

    private static final long FLIGHT_MAX_LIFETIME_SECONDS = 60 * 2;
    private static final int REFRESH_UI_MS = 1000 / 15;
    private static final int REFRESH_API_MS = 8 * 1000;
    private static final int REFRESH_INFO_MS = 2 * 1000;
    private static final long MAX_INTERPOLATE_DURATION_MS = 50 * 1000;
    private static final int TRAIL_LENGTH = 50;
    private static final double KTS_TO_M_PER_S = .52;
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private Thread mUpdateThread;
    private Fleet mFleet;
    private Handler mUIRefreshHandler;
    private long mLastTimeInfoUpdated;
    private TextView mNobodyPlayingText;
    private TextView mRefreshingText;
    private AirplaneBitmapProvider mBitmapProvider;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();
        mFleet = new Fleet();
        mBitmapProvider = new AirplaneBitmapProvider();

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

        checkUpdate();
    }


    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        if (mUpdateThread != null && mUpdateThread.isAlive())
            mUpdateThread.interrupt();
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
                    return;
                }
            }
        });
    }

    @Override
    protected void onPause() {
        mUpdateThread.interrupt();
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
        mFleet.discardOldFlights(FLIGHT_MAX_LIFETIME_SECONDS);
        mNobodyPlayingText.setVisibility((mFleet.getActiveFleetSize() <= 0 && mFleet.isUpToDate()) ? View.VISIBLE : View.GONE);
        boolean updateInfo = false;
        long now = System.currentTimeMillis();
        if (now - mLastTimeInfoUpdated > REFRESH_INFO_MS) {
            mLastTimeInfoUpdated = now;
            updateInfo = true;
        }
        for (Map.Entry<String, Flight> flightEntry : mFleet.getFleet().entrySet()) {
            Flight flight = flightEntry.getValue();
            List<Flight.FlightData> dataHistory = flight.getFlightHistory();
            if (dataHistory.size() <= 0)
                continue;
            // We get the last location
            Flight.FlightData data = dataHistory.get(dataHistory.size() - 1);
            Marker lastMarker = flight.getMarker();

            // Marker update/creation
            if (lastMarker != null) {
                // compute estimated new position based on speed
                // Stop interpolating if data is too old
                long delta = Math.min(now - data.reportTimestamp, MAX_INTERPOLATE_DURATION_MS);
                double distanceMeter = (data.speed * KTS_TO_M_PER_S) * (delta / 1000.0);
                LatLng newPos = SphericalUtil.computeOffset(data.position, distanceMeter, data.bearing);
                lastMarker.setPosition(newPos);
                if (delta > REFRESH_UI_MS) {
                    Polyline line = flight.getAproxTrail();
                    if (line == null) {
                        PolylineOptions path = new PolylineOptions();
                        path.width(3.0f);
                        path.color(0xFFA0A000);
                        line = mMap.addPolyline(path);
                        flight.setAproxTrail(line);
                    }
                    List<LatLng> points = line.getPoints();
                    points.clear();
                    points.add(data.position);
                    points.add(newPos);
                    line.setPoints(points);
                }
                // Only update that stuff when once in a while
                if (updateInfo) {
                    lastMarker.setSnippet(toSnippet(data));
                    lastMarker.setRotation(data.bearing.floatValue());
                    // Refreshing the info window
                    if (lastMarker.isInfoWindowShown()) {
                        lastMarker.hideInfoWindow();
                        lastMarker.showInfoWindow();
                        mMap.animateCamera(CameraUpdateFactory.newLatLng(lastMarker.getPosition()));
                    }
                }
            } else {
                Marker marker = mMap.addMarker(new MarkerOptions().position(data.position).title(toTitle(flight))
                        .rotation(data.bearing.floatValue()).snippet(toSnippet(data))
                        .icon(mBitmapProvider.getAsset(flight))
                        .anchor(.5f, .5f)
                        .infoWindowAnchor(.5f, .5f)
                        .flat(true)); // Flat will keep the rotation based on the north
                flight.setMarker(marker);
            }

            // Updating the trail. Ignore if there is no actual update
            if (dataHistory.size() > 1) {
                Polyline lastLine = flight.getHistoryTrail();
                if (lastLine != null) {
                    List<LatLng> points = lastLine.getPoints();
                    if (points.size() <= 0 ||
                            !points.get(points.size() - 1).equals(dataHistory.get(dataHistory.size() - 1).position)) {
                        points.clear();
                        // Removing old history
                        while (dataHistory.size() - TRAIL_LENGTH > 0) {
                            dataHistory.remove(0);
                        }
                        for (int cur = 0; cur < dataHistory.size(); ++cur) {
                            points.add(dataHistory.get(cur).position);
                        }
                        lastLine.setPoints(points);
                    }
                } else {
                    PolylineOptions path = new PolylineOptions();
                    path.width(3.0f);
                    path.color(0xFF00A000);
                    for (Flight.FlightData pathData : dataHistory) {
                        path.add(pathData.position);
                    }
                    Polyline line = mMap.addPolyline(path);
                    flight.setHistoryTrail(line);
                }
            }

        }
    }

    private String toTitle(Flight flight) {
        return flight.getAircraftName() + " | " + flight.getDisplayName() + (flight.getCallSign() == null ? "" : " | " + flight.getCallSign());
    }

    private String toSnippet(Flight.FlightData data) {
        int seconds = (int) (System.currentTimeMillis() - data.reportTimestamp) / 1000;
        String out = data.speed.intValue() + " kts | " + data.altitude.intValue() + " ft";
        if (seconds > 0)
            out += " | " + seconds + " seconds ago";
        return out;
    }

    private class UpdateThread extends Thread {
        @Override
        public void run() {
            while (!isInterrupted()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mRefreshingText.setVisibility(View.VISIBLE);
                        mRefreshingText.setText(R.string.refreshing_data);
                    }
                });
                mFleet.updateFleet();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mFleet.discardOldFlights(FLIGHT_MAX_LIFETIME_SECONDS);
                        int fleetSize = mFleet.getActiveFleetSize();
                        mRefreshingText.setVisibility(fleetSize <= 0 ? View.GONE : View.VISIBLE);
                        mRefreshingText.setText(getResources().getQuantityString(R.plurals.N_users_online, fleetSize, fleetSize));
                    }
                });
                try {
                    Thread.sleep(REFRESH_API_MS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
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
                    String line = reader.readLine();
                    final int newVersion = Integer.decode(line);
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

                                builder.setTitle("New update available! Current : v" + pInfo.versionCode + " latest : v" + newVersion);
                                builder.setMessage("Do you want to download the new version ?");
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

}
package com.valxp.app.infiniteflightwatcher.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.geometry.Bounds;
import com.valxp.app.infiniteflightwatcher.AirplaneBitmapProvider;
import com.valxp.app.infiniteflightwatcher.AirportBitmapProvider;
import com.valxp.app.infiniteflightwatcher.R;
import com.valxp.app.infiniteflightwatcher.TimeProvider;
import com.valxp.app.infiniteflightwatcher.Utils;
import com.valxp.app.infiniteflightwatcher.heatmap.Heatmap;
import com.valxp.app.infiniteflightwatcher.heatmap.SphericalMercator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Created by ValXp on 6/26/14.
 */
public class Regions extends ArrayList<Regions.Region> {
    public static long METAR_UPDATE_TRESHOLD_MS = 1000 * 60 * 15; // Refresh every 15 minutes
    private Long mLastMETARUpdate = null;
    private static Regions mInstance;
    private static Object mMutex = new Object();

    public static Regions getInstance(Context mCtx) {
        synchronized (mMutex) {
            if (mInstance == null) {
                mInstance = new Regions(mCtx);
            }
            return mInstance;
        }
    }

    private Regions(Context ctx) {
        try {
            Utils.Benchmark.start("regions.json parsing");
            InputStream file = ctx.getAssets().open("regions.json");
            BufferedReader streamReader = new BufferedReader(new InputStreamReader(file, "UTF-8"));
            StringBuilder strBuilder = new StringBuilder();
            String inputStr;
            while ((inputStr = streamReader.readLine()) != null)
                strBuilder.append(inputStr);

            JSONArray array = new JSONArray(strBuilder.toString());
            for (int i = 0; i < array.length(); ++i) {
                add(new Region(ctx, array.getJSONObject(i)));
            }
            Utils.Benchmark.stopAndLog("regions.json parsing");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void updateCount(Fleet fleet) {
        for (Region region : this) {
            region.updateCount(fleet);
        }
    }

    public void draw(Context ctx, GoogleMap map, Map<String, List<ATC>> atcs, boolean cluster, Bounds camBounds) {
        for (Region region : this) {
            region.draw(ctx, map, atcs, cluster, camBounds);
        }
    }

    public void onMapClick(Context ctx, GoogleMap map, LatLng loc) {
        for (Region region : this) {
            if (region.onMapClick(ctx, map, loc))
                return;
        }
    }

    public void onMarkerClick(Context ctx, GoogleMap map, Marker marker) {
        for (Region region : this) {
            if (region.onMarkerClick(ctx, map, marker))
                return;
        }
    }

    public View getInfoWindow(Context ctx, Map<String, List<ATC>> atcs, Marker marker) {
        for (Region region : this) {
            View v = region.getInfoWindow(ctx, atcs, marker);
            if (v != null)
                return v;
        }

        return null;
    }

    public Region regionContainingPoint(LatLng position) {
        for (Region region : this) {
            if (region.contains(position))
                return region;
        }
        return null;
    }

    public void updateMETAR() {
        if (mLastMETARUpdate != null &&
            TimeProvider.getTime() - mLastMETARUpdate < METAR_UPDATE_TRESHOLD_MS)
            return;
        mLastMETARUpdate = TimeProvider.getTime();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (Region region : Regions.this) {
                        region.updateMetar();
                    }
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public class Region {
        private HashMap<String, Metar> mMetar = null;
        private LatLng mTopLeft, mTopRight, mBottomRight, mBottomLeft;
        private LatLngBounds bounds;
        private String mName;
        private Polygon mLine;
        private Marker mMarker;
        private int mCount;
        private int mLastCount;
        private boolean mMetarDrawn = true;
        private Bounds mXYBounds;
        // This should allow us to have everything cleaned-up
        private WeakReference<Heatmap> mHeatMapRef;
        private Map<String, Airport> mAirports;
        private Map<Airport, Marker> mAirportMarkers = new HashMap<>();


        public Region(Context ctx, JSONObject object) throws JSONException {
            mBottomLeft = new LatLng(object.getDouble("LatMin"), object.getDouble("LonMin"));
            mBottomRight = new LatLng(object.getDouble("LatMin"), object.getDouble("LonMax"));
            mTopRight = new LatLng(object.getDouble("LatMax"), object.getDouble("LonMax"));
            mTopLeft = new LatLng(object.getDouble("LatMax"), object.getDouble("LonMin"));
            bounds = new LatLngBounds(mBottomLeft, mTopRight);
            mName = object.getString("Name");
            mLastCount = -1;
            mXYBounds = new Bounds(mTopLeft.longitude, mTopRight.longitude, mBottomLeft.latitude, mTopLeft.latitude);
            mAirports = new HashMap<>();
            try {
                String filename = object.getString("Airports");
                Utils.Benchmark.start(filename + " parsing");
                InputStream file = ctx.getAssets().open(filename);
                BufferedReader streamReader = new BufferedReader(new InputStreamReader(file, "UTF-8"));
                StringBuilder strBuilder = new StringBuilder();
                String inputStr;
                while ((inputStr = streamReader.readLine()) != null)
                    strBuilder.append(inputStr);

                JSONArray array = new JSONArray(strBuilder.toString());
                for (int i = 0; i < array.length(); ++i) {
                    Airport ap = new Airport(array.getJSONObject(i));
                    mAirports.put(ap.ICAO, ap);
                }
                Utils.Benchmark.stopAndLog(filename + " parsing");

            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Log.d("Region", mAirports.size() + " airports found in " + mName);
        }

        private void updateMetar() {
            Log.d("Region", "Retrieving METAR for " + getName() + " ...");
            while (true) {
                mMetar = Metar.getMetarInBounds(mTopLeft, mTopRight, mBottomRight, mBottomLeft);
                if (mMetar != null)
                    break;
                try {
                    Log.w("METAR", "Failed retrieving METAR for " + getName() + " retrying later");
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (mHeatMapRef != null && mHeatMapRef.get() != null)
                addHeatmapData();
            Log.d("Region", "Done Retrieving METAR for " + getName() + "! Found " + (mMetar == null ? "(null)" : mMetar.size()) + " entries!");
        }

        public void updateCount(Fleet fleet) {
            mCount = 0;
            for (Map.Entry<Users.User, Flight> entry : fleet.getFleet().entrySet()) {
                Flight flight = entry.getValue();
                synchronized (flight) {
                    Flight.FlightData current = flight.getCurrentData();
                    if (current != null && bounds.contains(current.position)) {
                        ++mCount;
                    }
                }
            }
        }

        public void showInside() {
            if (mLine != null) {
                mLine.setFillColor(0x05000000);
            }

        }

        private void drawAirports(Context ctx, GoogleMap map, Map<String, List<ATC>> atcs, Boolean cluster) {
            Utils.Benchmark.start("DrawAirports");
            if (cluster) {
                Iterator<Map.Entry<Airport, Marker>> it = mAirportMarkers.entrySet().iterator();
                while (it.hasNext()) {
                    it.next().getValue().remove();
                    it.remove();
                }
                return;
            }
            LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
            for (Airport ap : mAirports.values()) {
                Marker old = mAirportMarkers.get(ap);
                List<ATC> atcList = atcs.get(ap.ICAO);
                boolean hasAtc = atcList != null && !atcList.isEmpty();
                boolean shouldDraw = (hasAtc || ap.isMajor || map.getCameraPosition().zoom > 9) && bounds.contains(ap.position);
                if (old == null && shouldDraw) {
                    mAirportMarkers.put(ap, map.addMarker(new MarkerOptions().position(ap.position).title(ap.name).icon(AirportBitmapProvider.getAsset(ctx, ap, hasAtc))));
                } else if (old != null && !shouldDraw) {
                    mAirportMarkers.remove(ap);
                    old.remove();
                } else if (old != null && shouldDraw) {
                    boolean isInfoWindowShown = old.isInfoWindowShown();
                    old.setIcon(AirportBitmapProvider.getAsset(ctx, ap, hasAtc));
                    if (isInfoWindowShown)
                        old.showInfoWindow();
                }
            }
            Utils.Benchmark.stopAndLog("DrawAirports");
        }

        private void drawMetar(Context ctx, GoogleMap map) {
            for (Map.Entry<String, Metar> entry : mMetar.entrySet()) {
                Metar metar = entry.getValue();
                if (metar.getPosition() == null || metar.getWindDir() == null || metar.getRaw() == null)
                    continue;
                map.addMarker(new MarkerOptions()
                                .position(metar.getPosition())
                                .rotation(metar.getWindDir().floatValue())
                                .title(metar.getRaw())
                );
            }
            mMetarDrawn = true;
        }

        public void draw(Context ctx, GoogleMap map, Map<String, List<ATC>> atcs, boolean cluster, Bounds camBounds) {
            if (!mMetarDrawn && mMetar != null) {
                drawMetar(ctx, map);
            }

            if (isContainedIn(camBounds))
                drawAirports(ctx, map, atcs, cluster);

            if (mLine == null) {
                mLine = map.addPolygon(new PolygonOptions()
                        .add(getTopLeft())
                        .add(getTopRight())
                        .add(getBottomRight())
                        .add(getBottomLeft())
                        .strokeColor(0xFF555555)
                        .strokeWidth(4));
            }
            if (cluster) {
                mLine.setFillColor(0x0F000000);
                if (mLastCount != mCount || mMarker == null) {
                    mLastCount = mCount;
                    if (mMarker != null)
                        mMarker.remove();
                    String text = Integer.toString(mCount);
                    TextView t = (TextView) LayoutInflater.from(ctx).inflate(R.layout.region_counter, null);
                    t.setText(text);
                    t.measure(View.MeasureSpec.getSize(t.getMeasuredWidth()), View.MeasureSpec.getSize(t.getMeasuredHeight()));
                    Bitmap bmp = Bitmap.createBitmap(t.getMeasuredWidth(), t.getMeasuredHeight(), Bitmap.Config.ARGB_4444);//TEXT_SIZE * text.length(), 44, Bitmap.Config.ARGB_4444);
                    t.layout(0, 0, bmp.getWidth(), bmp.getHeight());
                    Canvas canvas = new Canvas(bmp);
                    t.draw(canvas);
                    MarkerOptions options = new MarkerOptions()
                            .position(SphericalUtil.interpolate(mTopLeft, mTopRight, .5))
                            .anchor(.5f, .5f)
                            .icon(BitmapDescriptorFactory.fromBitmap(bmp));
                    mMarker = map.addMarker(options);
                }
            } else if (mMarker != null) {
                mMarker.remove();
                mMarker = null;
            }
            if (!cluster) {
                mLine.setFillColor(0x05000000);
            }
        }

        public Metar getWindiestAirport() {
            if (mMetar == null)
                return null;
            Metar windiest = null;
            Iterator<Map.Entry<String, Metar>> it = mMetar.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Metar> entry = it.next();
                // Ignoring METAR stations that are not airports
                if (mAirports.containsKey(entry.getKey())) {
                    Metar metar = entry.getValue();
                    if (windiest == null)
                        windiest = metar;
                    else if (!(metar.getWindGust() < windiest.getWindGust()) && metar.getWindSpeed() > windiest.getWindSpeed()) {
                        windiest = metar;
                    }
                }
            }
            return windiest;
        }

        public Heatmap getHeatmap() {
            if (mHeatMapRef != null && mHeatMapRef.get() != null) {
                return mHeatMapRef.get();
            }
            double maxX = SphericalMercator.xFromLongitude(getTopRight().longitude);
            double maxY = SphericalMercator.yFromLatitude(getTopRight().latitude);
            double minX = SphericalMercator.xFromLongitude(getBottomLeft().longitude);
            double minY = SphericalMercator.yFromLatitude(getBottomLeft().latitude);

            double xDelta = maxX - minX;
            double yDelta = maxY - minY;
            maxX += xDelta / 5;
            maxY += yDelta / 5;
            minX -= xDelta / 5;
            minY -= yDelta / 5;

            Heatmap hm = new Heatmap(400, 400, 20, minX, minY, maxX - minX, maxY - minY);
            mHeatMapRef = new WeakReference(hm);

            addHeatmapData();

            return hm;
        }

        public boolean hasHeatmap() {
            return mHeatMapRef != null && mHeatMapRef.get() != null;
        }

        private void addHeatmapData() {
            if (mMetar == null || mHeatMapRef == null)
                return;
            Heatmap hm = mHeatMapRef.get();
            if (hm == null)
                return;
            for (Map.Entry<String, Metar> entry : mMetar.entrySet()) {
                Metar m = entry.getValue();
                LatLng pos = m.getPosition();
                hm.addMercatorPoint(SphericalMercator.xFromLongitude(pos.longitude), SphericalMercator.yFromLatitude(pos.latitude), (float) (m.getWindGust() + m.getWindSpeed()));
            }
        }

        public boolean contains(LatLng position) {
            return bounds.contains(position);
        }

        public boolean isContainedIn(Bounds bounds) {
            return bounds.contains(mXYBounds) || bounds.intersects(mXYBounds) || mXYBounds.contains(bounds);
        }

        // Returns true if event consumed
        public boolean onMapClick(Context ctx, GoogleMap map, LatLng loc) {
            if (bounds.contains(loc)) {
                zoomOnRegion(ctx, map);
                return true;
            }
            return false;
        }

        // Returns true if event consumed
        public boolean onMarkerClick(Context ctx, GoogleMap map, Marker marker) {
            if (marker != null) {
                if (mMarker != null && mMarker.equals(marker)) {
                    zoomOnRegion(ctx, map);
                    return true;
                }
                for (Marker ap : mAirportMarkers.values()) {
                    if (ap.equals(marker)) {
                        Log.d("Regions", "Airport marker click!");
                        if (ap.isInfoWindowShown()) {
                            ap.hideInfoWindow();
                            Log.d("Regions", "Hide infoWindow");
                        }
                        else {
                            ap.showInfoWindow();
                            Log.d("Regions", "Show infoWindow");
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        public View getInfoWindow(Context ctx, Map<String, List<ATC>> atcs, Marker marker) {
            for (Map.Entry<Airport, Marker> entry : mAirportMarkers.entrySet()) {
                Marker apMarker = entry.getValue();
                Airport ap = entry.getKey();
                if (apMarker.equals(marker)) {
                    Log.d("Regions", "Building infowindow!");
                    LinearLayout mainLayout = Utils.createLinearLayout(ctx);
                    TextView title = Utils.createTextView(ctx, ap.name + " (" + ap.ICAO + ")");
                    title.setTextSize(15);
                    mainLayout.addView(title);
                    mainLayout.addView(Utils.createTextView(ctx, "Elev: " + (int)ap.elevation + "ft"));
                    // Try to find ATC
                    if (atcs != null) {
                        List<ATC> myAtcs = atcs.get(ap.ICAO);
                        if (myAtcs != null && myAtcs.size() > 0) {
                            TextView ATCTitle = Utils.createTextView(ctx, myAtcs.size() + " ATC:");
                            mainLayout.addView(ATCTitle);
                            LinearLayout ATCLayout = Utils.createLinearLayout(ctx);
                            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) ATCLayout.getLayoutParams();
                            lp.setMargins(Utils.dpToPx(10), 0, 0, 0);
                            ATCLayout.setLayoutParams(lp);
                            for (ATC atc : myAtcs) {
                                TextView text = Utils.createTextView(ctx, atc.type.name() + ": " + atc.user.getName());
                                ATCLayout.addView(text);
                            }
                            mainLayout.addView(ATCLayout);
                        }
                        if (ap.runways.size() > 0) {
                            mainLayout.addView(Utils.createTextView(ctx, ap.runways.size() + " Runways:"));
                            LinearLayout runwayLayout = Utils.createLinearLayout(ctx);
                            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) runwayLayout.getLayoutParams();
                            lp.setMargins(Utils.dpToPx(10), 0, 0, 0);
                            runwayLayout.setLayoutParams(lp);
                            for (Airport.Runway runway : ap.runways) {
                                TextView text = Utils.createTextView(ctx, runway.nameBegin + "/" + runway.nameEnd + ": Length: " + runway.length + "ft");
                                runwayLayout.addView(text);
                            }
                            mainLayout.addView(runwayLayout);
                        }
                        Metar metar = mMetar.get(ap.ICAO);
                        if (metar != null) {
                            TextView text = Utils.createTextView(ctx, "METAR: " + metar.getRaw());
                            text.setMaxWidth(Utils.dpToPx(300));
                            mainLayout.addView(text);
                        }
                    }

                    return mainLayout;
                }
            }
            return null;
        }

        public void zoomOnRegion(Context ctx, GoogleMap map, GoogleMap.CancelableCallback callback) {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0), callback);
            Toast.makeText(ctx, "Welcome to " + mName + "!", Toast.LENGTH_SHORT).show();
        }

        public void zoomOnRegion(Context ctx, GoogleMap map) {
            zoomOnRegion(ctx, map, null);
        }

        public String getName() {
            return mName;
        }

        public LatLng getTopLeft() {
            return mTopLeft;
        }

        public LatLng getTopRight() {
            return mTopRight;
        }

        public LatLng getBottomRight() {
            return mBottomRight;
        }

        public LatLng getBottomLeft() {
            return mBottomLeft;
        }

        public int getPlayerCount() {
            return mCount;
        }

    }
}

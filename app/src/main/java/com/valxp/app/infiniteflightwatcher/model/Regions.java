package com.valxp.app.infiniteflightwatcher.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
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
import com.google.maps.android.SphericalUtil;
import com.valxp.app.infiniteflightwatcher.R;
import com.valxp.app.infiniteflightwatcher.TimeProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ValXp on 6/26/14.
 */
public class Regions extends ArrayList<Regions.Region> {
    public static int TEXT_SIZE = 30;
    public static long METAR_UPDATE_TRESHOLD_MS = 1000 * 1800; // Refresh every 30 minutes
    private Context mContext;
    private Long mLastMETARUpdate = null;

    public Regions(Context ctx) {
        mContext = ctx;
        try {
            InputStream file = ctx.getAssets().open("regions.json");
            BufferedReader streamReader = new BufferedReader(new InputStreamReader(file, "UTF-8"));
            StringBuilder strBuilder = new StringBuilder();
            String inputStr;
            while ((inputStr = streamReader.readLine()) != null)
                strBuilder.append(inputStr);

            JSONArray array = new JSONArray(strBuilder.toString());
            for (int i = 0; i < array.length(); ++i) {
                add(new Region(array.getJSONObject(i)));
            }

            updateMETAR();
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

    public void draw(GoogleMap map, boolean cluster) {
        for (Region region : this) {
            region.draw(map, cluster);
        }
    }

    public void onMapClick(GoogleMap map, LatLng loc) {
        for (Region region : this) {
            region.onMapClick(map, loc);
        }
    }

    public void onMarkerClick(GoogleMap map, Marker marker) {
        for (Region region : this) {
            region.onMarkerClick(map, marker);
        }
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
                for (Region region : Regions.this) {
                    region.updateMetar();
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

        public Region(JSONObject object) throws JSONException {
            mBottomLeft = new LatLng(object.getDouble("LatMin"), object.getDouble("LonMin"));
            mBottomRight = new LatLng(object.getDouble("LatMin"), object.getDouble("LonMax"));
            mTopRight = new LatLng(object.getDouble("LatMax"), object.getDouble("LonMax"));
            mTopLeft = new LatLng(object.getDouble("LatMax"), object.getDouble("LonMin"));
            bounds = new LatLngBounds(mBottomLeft, mTopRight);
            mName = object.getString("Name");
            mLastCount = -1;
        }

        private void updateMetar() {
            Log.d("Region", "Retrieving METAR for " + getName() + " ...");
            mMetar = Metar.getMetarInBounds(mTopLeft, mTopRight, mBottomRight, mBottomLeft);
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

        public void draw(GoogleMap map, boolean cluster) {
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
                mLine.setFillColor(mCount > 0 ? 0xFFE0FFE0 : 0xFFFFFFFF);
                if (mLastCount != mCount || mMarker == null) {
                    mLastCount = mCount;
                    if (mMarker != null)
                        mMarker.remove();
                    String text = Integer.toString(mCount);
                    TextView t = (TextView) LayoutInflater.from(mContext).inflate(R.layout.region_counter, null);
                    t.setText(text);
                    t.measure(View.MeasureSpec.getSize(t.getMeasuredWidth()), View.MeasureSpec.getSize(t.getMeasuredHeight()));
                    Bitmap bmp = Bitmap.createBitmap(t.getMeasuredWidth(), t.getMeasuredHeight(), Bitmap.Config.ARGB_4444);//TEXT_SIZE * text.length(), 44, Bitmap.Config.ARGB_4444);
                    t.layout(0, 0, bmp.getWidth(), bmp.getHeight());
                    Canvas canvas = new Canvas(bmp);
                    t.draw(canvas);
//                    canvas.drawARGB(0x00, 0xff, 0xff, 0xff);
//                    Paint p = new Paint();
//                    p.setColor(Color.BLACK);
//                    p.setStyle(Paint.Style.FILL);
//                    p.setLinearText(true);
//                    p.setTextSize(50);
//                    canvas.drawText(text, 0, 36, p);
                    MarkerOptions options = new MarkerOptions()
                            .position(SphericalUtil.interpolate(mBottomLeft, mTopRight, .5))
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

        public Metar getWindiest() {
            if (mMetar == null)
                return null;
            Metar windiest = null;
            for (Map.Entry<String, Metar> entry : mMetar.entrySet()) {
                Metar metar = entry.getValue();
                if (windiest == null)
                    windiest = metar;
                else if (!(metar.getWindGust() < windiest.getWindGust()) && metar.getWindSpeed() > windiest.getWindSpeed()) {
                    windiest = metar;
                }
            }
            return windiest;
        }

        public boolean contains(LatLng position) {
            return bounds.contains(position);
        }

        public void onMapClick(GoogleMap map, LatLng loc) {
            if (bounds.contains(loc))
                zoomOnRegion(map);
        }

        public void onMarkerClick(GoogleMap map, Marker marker) {
            if (marker != null && mMarker != null && mMarker.equals(marker))
                zoomOnRegion(map);
        }

        public void zoomOnRegion(GoogleMap map, GoogleMap.CancelableCallback callback) {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0), callback);
            Toast.makeText(mContext, "Welcome to " + mName + "!", Toast.LENGTH_SHORT).show();
        }

        public void zoomOnRegion(GoogleMap map) {
            zoomOnRegion(map, null);
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

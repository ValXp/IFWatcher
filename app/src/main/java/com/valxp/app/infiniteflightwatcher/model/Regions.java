package com.valxp.app.infiniteflightwatcher.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.maps.android.SphericalUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by ValXp on 6/26/14.
 */
public class Regions extends ArrayList<Regions.Region> {
    public static int TEXT_SIZE = 30;
    private Context mContext;

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
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void draw(GoogleMap map, Fleet fleet, boolean cluster) {
        for (Regions.Region region : this) {
            region.draw(map, fleet, cluster);
        }
    }

    public void onMapClick(GoogleMap map, LatLng loc) {
        for (Regions.Region region : this) {
            region.onMapClick(map, loc);
        }
    }

    public void onMarkerClick(GoogleMap map, Marker marker) {
        for (Regions.Region region : this) {
            region.onMarkerClick(map, marker);
        }
    }

    public class Region {
        private LatLng mTopLeft, mTopRight, mBottomRight, mBottomLeft;
        private LatLngBounds bounds;
        private String mName;
        private Polygon mLine;
        private Marker mMarker;
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

        public void draw(GoogleMap map, Fleet fleet, boolean cluster) {
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
                int count = 0;
                for (Map.Entry<Users.User, Flight> entry : fleet.getFleet().entrySet()) {
                    Flight flight = entry.getValue();
                    synchronized (flight) {
                        Flight.FlightData current = flight.getCurrentData();
                        if (current != null && bounds.contains(current.position)) {
                            ++count;
                        }
                    }
                }
                mLine.setFillColor(count > 0 ? 0xFFE0FFE0 : 0xFFFFFFFF);
                if (mLastCount != count || mMarker == null) {
                    if (mMarker != null)
                        mMarker.remove();
                    String text = Integer.toString(count);
                    Bitmap bmp = Bitmap.createBitmap(TEXT_SIZE * text.length(), 40, Bitmap.Config.ARGB_4444);
                    Canvas canvas = new Canvas(bmp);
                    canvas.drawARGB(0x00, 0xff, 0xff, 0xff);
                    Paint p = new Paint();
                    p.setColor(Color.BLACK);
                    p.setStyle(Paint.Style.FILL);
                    p.setLinearText(true);
                    p.setTextSize(50);
                    canvas.drawText(text, 0, 38, p);
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

        public void onMapClick(GoogleMap map, LatLng loc) {
            if (bounds.contains(loc))
                zoomOnRegion(map);
        }

        public void onMarkerClick(GoogleMap map, Marker marker) {
            if (marker != null && mMarker != null && mMarker.equals(marker))
                zoomOnRegion(map);
        }

        private void zoomOnRegion(GoogleMap map) {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
            Toast.makeText(mContext, "Welcome to " + mName + "!", Toast.LENGTH_SHORT).show();
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
    }
}

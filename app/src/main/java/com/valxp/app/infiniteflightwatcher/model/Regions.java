package com.valxp.app.infiniteflightwatcher.model;

import android.content.Context;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ValXp on 6/26/14.
 */
public class Regions {

    private List<Region> mRegions;

    public Regions(Context ctx) {
        try {
            InputStream file = null;
            file = ctx.getAssets().open("regions.json");
            BufferedReader streamReader = null;
            streamReader = new BufferedReader(new InputStreamReader(file, "UTF-8"));
            StringBuilder strBuilder = new StringBuilder();
            String inputStr;
            while ((inputStr = streamReader.readLine()) != null)
                strBuilder.append(inputStr);

            JSONArray array = new JSONArray(strBuilder.toString());
            mRegions = new ArrayList<Region>();
            for (int i = 0; i < array.length(); ++i) {
                mRegions.add(new Region(array.getJSONObject(i)));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public List<Region> getRegions() {
        return mRegions;
    }

    public class Region {
        LatLng mTopLeft, mTopRight, mBottomRight, mBottomLeft;
        String mName;
        public Region(JSONObject object) throws JSONException {
            mBottomLeft = new LatLng(object.getDouble("LatMin"), object.getDouble("LonMin"));
            mBottomRight = new LatLng(object.getDouble("LatMin"), object.getDouble("LonMax"));
            mTopRight = new LatLng(object.getDouble("LatMax"), object.getDouble("LonMax"));
            mTopLeft = new LatLng(object.getDouble("LatMax"), object.getDouble("LonMin"));
            mName = object.getString("Name");
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

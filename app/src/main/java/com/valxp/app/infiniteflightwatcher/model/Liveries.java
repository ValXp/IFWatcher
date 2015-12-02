package com.valxp.app.infiniteflightwatcher.model;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ValXp on 11/13/15.
 */
public class Liveries {
    private static Map<String, Livery> mLiveries;

    public static void initLiveries(Context ctx, String path) {
        if (mLiveries != null)
            return;
        mLiveries = new HashMap<>();
        try {
            File file = new File(path);
            InputStream stream;
            if (file.exists()) {
                stream = new FileInputStream(path);
            } else {
                stream = ctx.getAssets().open("airplanes.txt");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String line;
            do {
                if ((line = reader.readLine()) != null) {
                    String[] values = line.split(":");
                    mLiveries.put(values[1], new Livery(values[1], values[3], values[0], values[2]));
                }
            } while (line != null);
            stream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Livery> getLiveries() {
        return Collections.unmodifiableMap(mLiveries);
    }

    public static Livery getLivery(String liveryId) {
        Livery livery = mLiveries.get(liveryId);
        return livery == null ? new Livery("0", "Unknown Livery", "0", "Unknown Plane") : livery;
    }

    private Liveries() {}

    public static class Livery {
        private String mId;
        private String mName;
        private String mPlaneId;
        private String mPlaneName;

        private Livery(String id, String name, String planeId, String planeName) {
            mId = id;
            mName = name;
            mPlaneId = planeId;
            mPlaneName = planeName;
        }

        public String getId() {
            return mId;
        }

        public String getName() {
            return mName;
        }

        public String getPlaneId() {
            return mPlaneId;
        }

        public String getPlaneName() {
            return mPlaneName;
        }
    }
}

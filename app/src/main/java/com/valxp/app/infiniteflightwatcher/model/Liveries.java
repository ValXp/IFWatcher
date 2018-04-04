package com.valxp.app.infiniteflightwatcher.model;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
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
                stream = ctx.getAssets().open("AirplanesManifest.json");
            }
            Gson gson = new Gson();
            JsonReader reader = gson.newJsonReader(new InputStreamReader(stream));
            ArrayList<Livery> liveries = new ArrayList<>();
            reader.beginArray(); // Start of airplane array.
            while (reader.hasNext()){
                reader.beginObject(); // Start of airplane object.
                String aircraftId = "";
                String aircraftName = "";
                liveries.clear();
                while (reader.hasNext()) {
                    switch (reader.nextName()) {
                        case "ID":
                            aircraftId = reader.nextString();
                            break;
                        case "Name":
                            aircraftName = reader.nextString();
                            break;
                        case "Liveries":
                            reader.beginArray();
                            while (reader.hasNext()){
                                String liveryId = "";
                                String liveryName = "";
                                reader.beginObject();
                                while (reader.hasNext()) {
                                    switch (reader.nextName()) {
                                        case "ID":
                                            liveryId = reader.nextString();
                                            break;
                                        case "Name":
                                            liveryName = reader.nextString();
                                            break;
                                        default:
                                            reader.skipValue();
                                            break;
                                    }
                                }
                                liveries.add(new Livery(liveryId, liveryName, aircraftId, aircraftName));

                                reader.endObject();
                            }
                            reader.endArray();
                            break;
                        default:
                            reader.skipValue();
                            break;
                    }
                    for (Livery livery : liveries) {
                        livery.mPlaneId = aircraftId;
                        livery.mPlaneName = aircraftName;
                        mLiveries.put(livery.getId(), livery);
                    }
                }
                reader.endObject();
            }
            reader.endArray();
            reader.close();

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

package com.valxp.app.infiniteflightwatcher;

import android.util.Log;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.valxp.app.infiniteflightwatcher.model.Flight;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ValXp on 5/25/14.
 */
public class AirplaneBitmapProvider {
    private static final String DRAWABLE_PREFIX = "airplanes_";

    private Map<String, BitmapDescriptor> mIcons;
    private Map<String, String> mAirplaneNameToAssetName;

    public AirplaneBitmapProvider() {
        loadNameMapping();
        try {
            loadAssets(R.drawable.class);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void loadNameMapping() {
        mAirplaneNameToAssetName = new HashMap<String, String>();
        mAirplaneNameToAssetName.put("Boeing 717", "717");
        mAirplaneNameToAssetName.put("Boeing 737-7", "737_7");
        mAirplaneNameToAssetName.put("Boeing 737-8", "737_8");
        mAirplaneNameToAssetName.put("Boeing 737-9", "737_9");
        mAirplaneNameToAssetName.put("Boeing 747", "747");
        mAirplaneNameToAssetName.put("Boeing 757", "757");
        mAirplaneNameToAssetName.put("Boeing 767", "767");
        mAirplaneNameToAssetName.put("Boeing 777", "777");
        mAirplaneNameToAssetName.put("Boeing 787", "787");
        mAirplaneNameToAssetName.put("A-10", "a10");
        mAirplaneNameToAssetName.put("Airbus A321", "a321");
        mAirplaneNameToAssetName.put("Airbus A330", "a330");
        mAirplaneNameToAssetName.put("Airbus A340", "a340");
        mAirplaneNameToAssetName.put("Airbus A380", "a380");
        mAirplaneNameToAssetName.put("Boeing C-17 Globemaster", "c17");
        mAirplaneNameToAssetName.put("Cessna 172", "c172");
        mAirplaneNameToAssetName.put("Cessna 208", "c208");
        mAirplaneNameToAssetName.put("Bombardier CRJ-200", "crj200");
        mAirplaneNameToAssetName.put("Super Decathlon", "decathlon");
        mAirplaneNameToAssetName.put("Embraer", "embraer");
        mAirplaneNameToAssetName.put("F-14", "f14");
        mAirplaneNameToAssetName.put("F-16", "f16");
        mAirplaneNameToAssetName.put("F/A-18", "f18");
        mAirplaneNameToAssetName.put("F-22", "f22");
        mAirplaneNameToAssetName.put("P-38", "p38");
        mAirplaneNameToAssetName.put("Space Shuttle", "shuttle");
        mAirplaneNameToAssetName.put("Spitfire", "spitfire");
        mAirplaneNameToAssetName.put("Cirrus SR22", "sr22");
        mAirplaneNameToAssetName.put("Cessna Citation X", "ccx");
    }

    private void loadAssets(Class<?> clazz) throws IllegalAccessException {
        mIcons = new HashMap<String, BitmapDescriptor>();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (field.getName().startsWith(DRAWABLE_PREFIX)) {

                mIcons.put(field.getName().substring(DRAWABLE_PREFIX.length()), BitmapDescriptorFactory.fromResource(field.getInt(clazz)));
            }
        }
    }

    public BitmapDescriptor getAsset(Flight flight) {
        BitmapDescriptor descriptor = null;
        for (Map.Entry<String, String> entry : mAirplaneNameToAssetName.entrySet()) {
            if (flight.getAircraftName().startsWith(entry.getKey())) {
                descriptor = mIcons.get(entry.getValue());
            }
        }
        if (descriptor == null) {
            Log.w("AirplaneBitmapProvider", "Coulnd't find icon for " + flight.getAircraftName() + " !");
            descriptor = mIcons.get("airplane");
        }
        return descriptor;
    }
}

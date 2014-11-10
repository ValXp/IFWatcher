package com.valxp.app.infiniteflightwatcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.valxp.app.infiniteflightwatcher.model.Flight;
import com.valxp.app.infiniteflightwatcher.model.Users;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ValXp on 5/25/14.
 */
public class AirplaneBitmapProvider {
    private static final String DRAWABLE_PREFIX = "airplanes_";

    private Context mContext;
    private Map<String, Integer> mIcons;
    private Map<String, String> mAirplaneNameToAssetName;

    public AirplaneBitmapProvider(Context context) {
        mContext = context;
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
        mIcons = new HashMap<String, Integer>();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (field.getName().startsWith(DRAWABLE_PREFIX)) {
                mIcons.put(field.getName().substring(DRAWABLE_PREFIX.length()), field.getInt(clazz));
            }
        }
    }

    public BitmapDescriptor getAsset(Flight flight, boolean selected) {
        Integer drawableId = null;
        for (Map.Entry<String, String> entry : mAirplaneNameToAssetName.entrySet()) {
            if (flight.getAircraftName().startsWith(entry.getKey())) {
                drawableId = mIcons.get(entry.getValue());
            }
        }
        if (drawableId == null) {
            Log.w("AirplaneBitmapProvider", "Coulnd't find icon for " + flight.getAircraftName() + " !");
            drawableId = mIcons.get("airplane");
        }
        Drawable drawable = mContext.getResources().getDrawable(drawableId);

        Users.User user = flight.getUser();
        int color = mContext.getResources().getColor(selected ? R.color.orange_selected_color : R.color.orange_color);
        if (user.getRole() == Users.User.Role.ADMIN)
            color = mContext.getResources().getColor(selected ? R.color.admin_selected_color : R.color.admin_color);
        else if (user.getRole() == Users.User.Role.TESTER)
            color = mContext.getResources().getColor(selected ? R.color.tester_selected_color : R.color.tester_color);
        if (user.getRank() != null && user.getRank() == 1)
            color = mContext.getResources().getColor(R.color.gold_color);

        Flight.FlightData data = flight.getCurrentData();
        float shadowDistance = 0.04f;
        if (data != null) {
            shadowDistance = (float) (Math.min(data.altitude, 50000) / 100000.0); // When matthieu visits ISS
        }
        int widthOffset = (int) (drawable.getIntrinsicWidth() * shadowDistance);
        int heightOffset = (int) (drawable.getIntrinsicHeight() * shadowDistance);

        int shadowColor = selected ? Color.parseColor("#88222222") : Color.parseColor("#88000000");

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);
        drawable.setColorFilter(shadowColor, PorterDuff.Mode.SRC_IN);
        drawable.setBounds(widthOffset, heightOffset, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        drawable.setBounds(0, 0, canvas.getWidth() - widthOffset, canvas.getHeight() - heightOffset);
        drawable.draw(canvas);

        BitmapDescriptor descriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
        return descriptor;
    }
}

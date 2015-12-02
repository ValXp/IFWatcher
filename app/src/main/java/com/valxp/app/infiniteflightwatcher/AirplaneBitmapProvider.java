package com.valxp.app.infiniteflightwatcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ScaleDrawable;
import android.view.Gravity;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.valxp.app.infiniteflightwatcher.model.Flight;
import com.valxp.app.infiniteflightwatcher.model.Users;

import java.io.IOException;

/**
 * Created by ValXp on 5/25/14.
 */
public class AirplaneBitmapProvider {

    private Context mContext;

    public AirplaneBitmapProvider(Context context) {
        mContext = context;
    }

    private Drawable drawableFromFlight(Flight flight) {
        Drawable drawable = null;
        try {
            drawable = Drawable.createFromStream(mContext.getAssets().open("markers/" + flight.getLivery().getPlaneId() + ".png"), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (drawable == null) {
            try {
                drawable = Drawable.createFromStream(mContext.getAssets().open("markers/0.png"), null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return drawable;
    }

    public BitmapDescriptor getAsset(Flight flight, boolean selected) {

        Drawable drawable = drawableFromFlight(flight);

        Users.User user = flight.getUser();
        int color = mContext.getResources().getColor(selected ? R.color.orange_selected_color : R.color.orange_color);
//        if (user.getRole() == Users.User.Role.ADMIN)
//            color = mContext.getResources().getColor(selected ? R.color.admin_selected_color : R.color.admin_color);
//        else if (user.getRole() == Users.User.Role.TESTER)
//            color = mContext.getResources().getColor(selected ? R.color.tester_selected_color : R.color.tester_color);
        if (user.getRank() != null && user.getRank() == 1)
            color = mContext.getResources().getColor(R.color.gold_color);

        Flight.FlightData data = flight.getCurrentData();
        float shadowDistance = 0.04f;
        if (data != null) {
            shadowDistance = (float) (Math.min(data.altitude, 50000) / 400000.0); // When matthieu visits ISS
        }
        int drawableWidth = Utils.dpToPx(32);
        int drawableHeight = Utils.dpToPx(32);
        int widthOffset = (int) (drawableWidth * shadowDistance);
        int heightOffset = (int) (drawableHeight * shadowDistance);

        int shadowColor = selected ? Color.parseColor("#88222222") : Color.parseColor("#88000000");

        Bitmap bitmap = Bitmap.createBitmap(drawableWidth, drawableHeight, Bitmap.Config.ARGB_4444);
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

package com.valxp.app.infiniteflightwatcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ScaleDrawable;
import android.util.Log;
import android.view.Gravity;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.valxp.app.infiniteflightwatcher.caching.DrawableMemoryCache;
import com.valxp.app.infiniteflightwatcher.model.Flight;
import com.valxp.app.infiniteflightwatcher.model.Users;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by ValXp on 5/25/14.
 */
public class AirplaneBitmapProvider {

    private Context mContext;
    private DrawableMemoryCache mMemoryCache;
    private ExecutorService mThreadPool;

    public interface OnMarkerDownload {
        void OnMarkerDownloaded(BitmapDescriptor descriptor);
    }

    public AirplaneBitmapProvider(Context context) {
        mContext = context;
        mMemoryCache = new DrawableMemoryCache(context, "markers", APIConstants.APICalls.MARKERS);
        mThreadPool = Executors.newFixedThreadPool(Math.min(Runtime.getRuntime().availableProcessors(), 2));
    }

    private BitmapDescriptor drawableToDescriptor(Drawable drawable, Flight flight, boolean selected) {
        Users.User user = flight.getUser();
        int color = selected ? R.color.orange_selected_color : R.color.orange_color;

        if (user.isAdmin()) {
            color = R.color.dev_color;
        }
        if (user.isMod()) {
            color = R.color.mod_color;
        }
        color = mContext.getResources().getColor(color);

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
        return  descriptor;
    }

    public BitmapDescriptor getAsset(final Flight flight, final OnMarkerDownload callback, final boolean selected) {

        Drawable drawable = null;
        Runnable onFailureTask = null;
        final String flightId = flight.getLivery().getPlaneId();
        try {
            drawable = mMemoryCache.getDrawable(flightId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (drawable == null) {

            try {
                onFailureTask = new Runnable() {
                    @Override
                    public void run() {
                        final Drawable drawable1 = mMemoryCache.getDrawable(flightId, true);
                        BitmapDescriptor descriptor = null;
                        if (drawable1 != null)
                            descriptor = drawableToDescriptor(drawable1, flight, selected);
                        final BitmapDescriptor descriptor1 = descriptor;
                        ((Activity)mContext).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    callback.OnMarkerDownloaded(descriptor1);
                                } catch (Exception e)
                                {
                                    // Will happen when setting a marker that has already been released.
                                }
                            }
                        });
                    }
                };
                drawable = Drawable.createFromStream(mContext.getAssets().open("markers/0.png"), null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        BitmapDescriptor descriptor = drawableToDescriptor(drawable, flight, selected);
        if (onFailureTask != null)
            mThreadPool.execute(onFailureTask);
        return descriptor;
    }
}

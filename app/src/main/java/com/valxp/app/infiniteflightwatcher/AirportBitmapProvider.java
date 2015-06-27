package com.valxp.app.infiniteflightwatcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.valxp.app.infiniteflightwatcher.model.Airport;

/**
 * Created by ValXp on 6/26/15.
 */
public class AirportBitmapProvider {

    public static BitmapDescriptor getAsset(Context ctx, Airport airport) {

        TextView t = (TextView) LayoutInflater.from(ctx).inflate(R.layout.region_counter, null);
        t.setText(airport.ICAO);
        t.setTextSize(11);
        t.measure(View.MeasureSpec.getSize(t.getMeasuredWidth()), View.MeasureSpec.getSize(t.getMeasuredHeight()));
        Bitmap bmp = Bitmap.createBitmap(t.getMeasuredWidth(), t.getMeasuredHeight(), Bitmap.Config.ARGB_4444);
        t.layout(0, 0, bmp.getWidth(), bmp.getHeight());
        Canvas canvas = new Canvas(bmp);
        t.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bmp);
    }
}

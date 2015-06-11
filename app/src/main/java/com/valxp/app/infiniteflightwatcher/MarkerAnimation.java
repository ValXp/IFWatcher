/* Copyright 2013 Google Inc.
   Licensed under Apache 2.0: http://www.apache.org/licenses/LICENSE-2.0.html */
package com.valxp.app.infiniteflightwatcher;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.util.Property;
import android.view.animation.LinearInterpolator;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;


import static java.lang.Math.asin;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

public class MarkerAnimation {
    private static Activity mContext;

    private static TypeEvaluator<LatLng> typeEvaluator = new TypeEvaluator<LatLng>() {
        @Override
        public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
            return linearInterpolate(fraction, startValue, endValue);
        }
    };

    private static Property<Marker, LatLng> markerProperty = Property.of(Marker.class, LatLng.class, "position");

    static public void initContext(Activity ctx) {
        mContext = ctx;
    }


    static public Animator animatePolyline(final StrokedPolyLine polyline, final LatLng startPosition, final LatLng finalPosition, long durationMs) {
        final ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                polyline.setPoints(polyline.getPoints().get(0), linearInterpolate(animation.getAnimatedFraction(), startPosition, finalPosition));
            }
        });
        valueAnimator.setFloatValues(0, 1); // Ignored.
        valueAnimator.setInterpolator(new LinearInterpolator());
        valueAnimator.setDuration(durationMs);
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                valueAnimator.start();
            }
        });
        return valueAnimator;
    }

    static public Animator animateMarker(Marker marker, LatLng finalPosition, long durationMs) {
        final ObjectAnimator animator = ObjectAnimator.ofObject(marker, markerProperty, typeEvaluator, finalPosition);
        animator.setDuration(durationMs);
        animator.setInterpolator(new LinearInterpolator());
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                animator.start();
            }
        });
        return animator;
    }

    static public void stopAnimator(final Animator animator) {
        if (animator == null)
            return;
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                animator.cancel();
            }
        });

    }

    public static LatLng linearInterpolate(float fraction, LatLng from, LatLng to) {
        double deltaLat = to.latitude - from.latitude;
        double deltaLon = to.longitude - from.longitude;
        return new LatLng(deltaLat * fraction + from.latitude, deltaLon * fraction + from.longitude);
    }

    /* From github.com/googlemaps/android-maps-utils */
    public static  LatLng interpolate(float fraction, LatLng from, LatLng to) {
        // http://en.wikipedia.org/wiki/Slerp
        double fromLat = toRadians(from.latitude);
        double fromLng = toRadians(from.longitude);
        double toLat = toRadians(to.latitude);
        double toLng = toRadians(to.longitude);
        double cosFromLat = cos(fromLat);
        double cosToLat = cos(toLat);

        // Computes Spherical interpolation coefficients.
        double angle = computeAngleBetween(fromLat, fromLng, toLat, toLng);
        double sinAngle = sin(angle);
        if (sinAngle < 1E-6) {
            return from;
        }
        double a = sin((1 - fraction) * angle) / sinAngle;
        double b = sin(fraction * angle) / sinAngle;

        // Converts from polar to vector and interpolate.
        double x = a * cosFromLat * cos(fromLng) + b * cosToLat * cos(toLng);
        double y = a * cosFromLat * sin(fromLng) + b * cosToLat * sin(toLng);
        double z = a * sin(fromLat) + b * sin(toLat);

        // Converts interpolated vector back to polar.
        double lat = atan2(z, sqrt(x * x + y * y));
        double lng = atan2(y, x);
        return new LatLng(toDegrees(lat), toDegrees(lng));
    }

    private static double computeAngleBetween(double fromLat, double fromLng, double toLat, double toLng) {
        // Haversine's formula
        double dLat = fromLat - toLat;
        double dLng = fromLng - toLng;
        return 2 * asin(sqrt(pow(sin(dLat / 2), 2) +
                cos(fromLat) * cos(toLat) * pow(sin(dLng / 2), 2)));
    }
}
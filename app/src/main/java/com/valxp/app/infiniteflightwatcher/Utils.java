package com.valxp.app.infiniteflightwatcher;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ValXp on 5/19/15.
 */
public class Utils {
    private static Context mContext;

    public static void initContext(Context ctx) {
        mContext = ctx;
    }

    public static float pxFromDp(float dp) {
        return dp * mContext.getResources().getDisplayMetrics().density;
    }
    public static float dpFromPx(float px) {
        return px / mContext.getResources().getDisplayMetrics().density;
    }
    public static class Benchmark {
        private static Map<String, Long> mTimers = new HashMap<>();

        public static void start(String name) {
            mTimers.put(name, new Long(0));
            mTimers.put(name, System.currentTimeMillis());
        }

        public static void stopAndLog(String name) {
            Log.d("Benchmark", name + " took " + stop(name) + "ms");
        }

        public static long stop(String name) {
            Long value = mTimers.get(name);
            if (value != null) {
//                mTimers.remove(name);
                return System.currentTimeMillis() - value;
            }
            return -1;
        }

    }

    // Return an approximation of the pixel size in meter from a zoom level
    public static float meterPerDp(float zoomLevel) {
        return 40000000 / pxFromDp((float) (256 * Math.pow(2, zoomLevel)));
    }
}

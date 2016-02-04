package com.valxp.app.infiniteflightwatcher;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ValXp on 5/19/15.
 */
public class Utils {
    public static final long A_GRAIN_OF_ = 0x5417;
    private static Context mContext;
    private static Boolean mIsMyDevice;
    private static final List<String> myDevices = Arrays.asList(
            "44c651a066e88cb72419bca4154dd79ae4037bc36314e7b72c857437161aaf72");

    public static void initContext(Context ctx) {
        mContext = ctx;
    }

    public static int dpToPx(float dp) {
        return (int) (dp * mContext.getResources().getDisplayMetrics().density);
    }
    public static float pxToDp(int px) {
        return px / mContext.getResources().getDisplayMetrics().density;
    }
    public static class Benchmark {
        private static Map<String, Long> mTimers = Collections.synchronizedMap(new HashMap<String, Long>());

        public static void start(String name) {
            mTimers.put(name, 0l);
            mTimers.put(name, System.nanoTime());
        }

        public static void stopAndLog(String name) {
            if (BuildConfig.DEBUG) {
                Log.d("Benchmark", name + " took " + (stop(name) / 1000000) + "ms");
            }
        }

        public static long stop(String name) {
            Long value = mTimers.get(name);
            if (value != null) {
                mTimers.remove(name);
                return System.nanoTime() - value;
            }
            return -1;
        }

    }

    // Return an approximation of the pixel size in meter from a zoom level
    public static float meterPerDp(float zoomLevel) {
        return 40000000 / dpToPx((float) (256 * Math.pow(2, zoomLevel)));
    }

    public static LinearLayout createLinearLayout(Context ctx) {
        LinearLayout mainLayout = new LinearLayout(ctx);
//        mainLayout.setBackgroundResource(R.drawable.shadowed_ui_background);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return mainLayout;
    }

    public static TextView createTextView(Context ctx, String text) {
        TextView view = new TextView(ctx);

        view.setText(text);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    public static boolean isMyDevice(Context ctx) {
        if (mIsMyDevice == null) {
            String sha256 = "";
            try {
                String android_id = Settings.Secure.getString(ctx.getContentResolver(),
                        Settings.Secure.ANDROID_ID);
                BigInteger value = new BigInteger(android_id, 16);
                value = value.add(BigInteger.valueOf(A_GRAIN_OF_));
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.reset();
                byte[] sha = digest.digest(value.toString(16).getBytes("UTF-8"));
                digest.reset();
                sha256 = String.format("%0" + (sha.length * 2) + 'x', new BigInteger(1, sha));
            } catch (Exception e) {
                e.printStackTrace();
            }

            mIsMyDevice = false;
            for (String deviceSHA : myDevices) {
                if (sha256.equals(deviceSHA)) {
                    mIsMyDevice = true;
                    break;
                }
            }
            Log.d("MapsActivity", "android_id: " + sha256);
        }
        return mIsMyDevice;
    }
}

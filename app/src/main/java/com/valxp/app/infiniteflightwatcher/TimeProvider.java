package com.valxp.app.infiniteflightwatcher;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Created by ValXp on 7/26/14.
 */
public class TimeProvider {
    private static long mTimeOffset = 0;

    public static void synchronizeWithInternet() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("http://www.timeapi.org/utc/now");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    mTimeOffset = sdf.parse(reader.readLine()).getTime() - System.currentTimeMillis();
                    Log.d("TimeProvider", "Time updated from server. Offset : " + mTimeOffset + "ms");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static long getTime() {
        return System.currentTimeMillis() + mTimeOffset;
    }
}

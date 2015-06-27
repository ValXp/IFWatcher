package com.valxp.app.infiniteflightwatcher.model;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.valxp.app.infiniteflightwatcher.APIConstants;
import com.valxp.app.infiniteflightwatcher.TimeProvider;
import com.valxp.app.infiniteflightwatcher.Utils;
import com.valxp.app.infiniteflightwatcher.Webservices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ValXp on 6/11/15.
 */
public class ATC {
    private static long REFRESH_RATE_MS = 20 * 1000;
    private static Map<Server, Map<String, List<ATC>>> mAtcs = new HashMap<>();
    private static long mLastUpdated = 0;

    // ICAO name indexed map of list of ATCs because there can be multiple ATCs per location
    public static Map<String, List<ATC>> getATC(Server server) {
        synchronized (mAtcs) {
            if (TimeProvider.getTime() - mLastUpdated < REFRESH_RATE_MS && mAtcs != null && mAtcs.get(server) != null)
                return mAtcs.get(server);
            Utils.Benchmark.start("ATC_Parsing");
            JSONArray array = Webservices.getJSON(APIConstants.APICalls.GET_ATC_FACILITIES, "&sessionid=" + server.getId());
            // Clearing the old ATCs because they might not exist anymore
            mAtcs.put(server, new HashMap<String, List<ATC>>());
            if (array != null) {
                try {
                    Log.d("ATC_", "ATC count: " + array.length());
                    for (int i = 0; i < array.length(); ++i) {
                        ATC atc = new ATC(array.getJSONObject(i));
                        List<ATC> atcList = mAtcs.get(server).get(atc.name);
                        if (atcList == null) {
                            atcList = new ArrayList<>();
                            mAtcs.get(server).put(atc.name, atcList);
                        }
                        atcList.add(atc);
                    }
                    mLastUpdated = TimeProvider.getTime();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            Utils.Benchmark.stopAndLog("ATC_Parsing");
            return mAtcs.get(server);
        }
    }

    public String frequencyID;
    public LatLng position;
    public String name; // ICAO
    public long startTime;
    public ATCType type;
    public Users.User user;

    public enum ATCType {
        Ground(0),
        Tower(1),
        Unicom(2),
        Clearance(3),
        Approach(4),
        Departure(5),
        Center(6),
        ATIS(7),
        Aircraft(8),
        Recorded(9),
        Unknown(10),
        Unused(11);

        private int mValue;
        ATCType(int value) {
            mValue = value;
        }
        public int getValue() {
            return mValue;
        }
        public static ATCType valueToType(int value) {
            for (ATCType type : values()) {
                if (type.mValue == value)
                    return type;
            }
            return Unknown;
        }
    }

    public ATC(JSONObject object) throws JSONException {
        frequencyID = object.getString("FrequencyID");
        position = new LatLng(object.getDouble("Latitude"), object.getDouble("Longitude"));
        name = object.getString("Name");
        String date = object.getString("StartTime");
        startTime = Long.parseLong(date.substring(date.indexOf('(') + 1, date.indexOf(')')));
        type = ATCType.valueToType(object.getInt("Type"));
        user = new Users.User(object.getString("UserID"), object.getString("UserName"));
    }
}

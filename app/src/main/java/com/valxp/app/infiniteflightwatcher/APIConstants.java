package com.valxp.app.infiniteflightwatcher;

import android.content.Context;
import android.util.Log;

/**
 * Created by ValXp on 5/24/14.
 */
public class APIConstants {
    private static final String BASE_URL = "http://infinite-flight-public-api.cloudapp.net/v1/";
    private static final String POSTFIX = "/?apikey=";
    private static String KEY;

    public static void init(Context ctx) {
        KEY = ctx.getResources().getString(R.string.infinite_flight_key);
    }

    public enum APICalls {
        // Infinite flight public API
        FLIGHTS(BASE_URL + "Flights.aspx", true),
        FLIGHT_DETAILS(BASE_URL + "FlightDetails.aspx", true),
        USER_DETAILS(BASE_URL + "UserDetails.aspx", true),
        SESSIONS_INFO(BASE_URL + "GetSessionsInfo.aspx", true),
        GET_ATC_FACILITIES(BASE_URL + "GetATCFacilities.aspx", true),
        GET_FLIGHT_PLAN(BASE_URL + "GetFlightPlans.aspx", true),

        // Metar Data
        METAR("http://www.aviationweather.gov/adds/dataserver_current/httpparam?dataSource=metars&requestType=retrieve&format=xml&hoursBeforeNow=1"),

        // Liveries + IDs
        LIVERIES("https://valxp.net/IFWatcher/resources/"),
        LIVERY_PREVIEWS("https://valxp.net/IFWatcher/resources/livery_previews/"),
        MARKERS("https://valxp.net/IFWatcher/resources/markers/"),

        // Time
        TIME("https://valxp.net/IFWatcher/api/utc.php");

        APICalls(String call, boolean needsKey) {
            this.value = call;
            this.needsKey = needsKey;
        }

        APICalls(String call) {
            this.value = call;
            this.needsKey = false;
        }

        @Override
        public String toString() {
            if (needsKey && KEY == null) {
                Log.e("APIConstants", "Error! API Key is not set!!");
            }
            return (needsKey && KEY != null ? (value + POSTFIX + KEY) : value);
        }

        private final String value;
        private final boolean needsKey;
    }
}

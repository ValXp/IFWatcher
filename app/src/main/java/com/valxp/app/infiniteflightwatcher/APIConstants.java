package com.valxp.app.infiniteflightwatcher;

/**
 * Created by ValXp on 5/24/14.
 */
public class APIConstants {
    private static final String BASE_URL = #replace with the base url here#;

    public enum APICalls {
        FLIGHTS(BASE_URL + #replace with the call's url here#),
        FLIGHT_DETAILS(BASE_URL + #replace with the call's url here#),
        USER_DETAILS(BASE_URL + #replace with the call's url here#);

        private final String value;

        APICalls(String call) {
            this.value = call;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}

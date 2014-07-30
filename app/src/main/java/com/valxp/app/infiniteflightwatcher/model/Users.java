package com.valxp.app.infiniteflightwatcher.model;

import com.valxp.app.infiniteflightwatcher.APIConstants;
import com.valxp.app.infiniteflightwatcher.Webservices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ValXp on 6/26/14.
 */
public class Users {
    private Map<String, User> mUsers; // Map of id to user

    public Users() {
        mUsers = new HashMap<String, User>();
    }

    public User addUser(String id) {
        User user = mUsers.get(id);
        if (user == null) {
            mUsers.put(id, new User(id));
        }
        return mUsers.get(id);
    }

    public void update(String id, boolean force) {
        if (id == null) {
            id = "";
            for (Map.Entry<String, User> pair : mUsers.entrySet()) {
                if (!pair.getValue().mIsSet || force)
                    id += "\""+pair.getKey() + "\",";
            }
        } else if (mUsers.get(id) == null || mUsers.get(id).mIsSet && !force){
            return;
        } else {
            id = "\"" +id+ "\"";
        }
        if (id.length() == 0) {
            return;
        }
        String request = "{\"UserIDs\":["+id+"]}";

        try {
            parseJson(Webservices.getJSON(APIConstants.APICalls.USER_DETAILS, request));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void update(boolean force) {
        update(null, force);
    }


    private void parseJson(JSONArray array) throws JSONException {
        if (array == null)
            return;
        for (int i = 0; i < array.length(); ++i) {
            User temp = new User(array.getJSONObject(i));
            User user = mUsers.get(temp.mId);
            if (user != null) {
                user.set(temp);
            } else {
                mUsers.put(temp.getId(), temp);
            }
        }
    }


    public User getUser(String id) {
        return mUsers.get(id);
    }


    public static class User {
        private String mId;
        private Double mFlightTime;
        private Long mLandingCount;
        private Long mLastFlight; // Time
        private String mName;
        private Long mOnlineFlights;
        private Long mRank;
        private Long mSkills;
        private Double mStanding; // 0 -> 0% 1 -> 100%
        private Long mViolations;
        private Role mRole;

        private boolean mIsSet;

        public enum Role {
            UNKNOWN(0, "Unknown"),
            USER(1, "User"),
            TESTER(4, "Tester"),
            ADMIN(16, "Admin");

            private final int mValue;
            private final String mName;
            Role(int value, String name) {
                mValue = value;
                mName = name;
            }
            public int getValue() {
                return mValue;
            }
            public String toString() {
                return mName;
            }
            public static Role fromValue(int value) {
                Role[] roles = Role.class.getEnumConstants();
                for (Role role : roles) {
                    if (role.getValue() == value) {
                        return role;
                    }
                }
                return UNKNOWN;
            }
        }

        public User(String id) {
            mId = id;
            mIsSet = false;
        }

        private User(JSONObject object) throws JSONException {

            mFlightTime = object.getDouble("FlightTime");
            mLandingCount = object.getLong("LandingCount");
            mLastFlight = object.getLong("LastFlight");
            mName = object.getString("Name");
            mOnlineFlights = object.getLong("OnlineFlights");
            mRank = object.getLong("Rank");
            mSkills = object.getLong("Skills");
            mStanding = object.getDouble("Standing");
            mId = object.getString("UserID");
            mViolations = object.getLong("Violations");
            Integer role = object.getInt("Roles");
            mRole = role == null ? Role.fromValue(0) : Role.fromValue(role.intValue());

            mIsSet = true;
        }

        public void set(User other) {
            this.mFlightTime = other.mFlightTime;
            this.mLandingCount = other.mLandingCount;
            this.mLastFlight = other.mLastFlight;
            this.mName = other.mName;
            this.mOnlineFlights = other.mOnlineFlights;
            this.mRank = other.mRank;
            this.mSkills = other.mSkills;
            this.mStanding = other.mStanding;
            this.mViolations = other.mViolations;
            this.mIsSet = true;
            this.mRole = other.mRole;
        }

        public void markForUpdate() {
            mIsSet = false;
        }

        public String getId() {
            return mId;
        }

        public Double getFlightTime() {
            return mFlightTime;
        }

        public Long getLastFlight() {
            return mLastFlight;
        }

        public String getName() {
            return mName;
        }

        public Long getOnlineFlights() {
            return mOnlineFlights;
        }

        public Long getRank() {
            return mRank;
        }

        public Long getSkills() {
            return mSkills;
        }

        public Double getStanding() {
            return mStanding;
        }

        public Long getViolations() {
            return mViolations;
        }

        public boolean isSet() {
            return mIsSet;
        }

        public Long getLandingCount() {
            return mLandingCount;
        }

        public Role getRole() {
            return mRole;
        }
    }
}

package com.valxp.app.infiniteflightwatcher.model;

import android.util.Log;

import com.valxp.app.infiniteflightwatcher.APIConstants;
import com.valxp.app.infiniteflightwatcher.TimeProvider;
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
    public static long REFRESH_THRESHOLD = 1000 * 60;
    public static long MAX_USERS_PER_REQUEST = 10;
    private Map<String, User> mUsers; // Map of id to user

    public Users() {
        mUsers = new HashMap<String, User>();
    }

    public User addUser(String id, String name) {
        synchronized (mUsers) {
            User user = mUsers.get(id);
            if (user == null) {
                mUsers.put(id, new User(id, name));
            }
            return mUsers.get(id);
        }
    }

    public void update(String id, boolean force) {
        if (id == null) {
            id = "";
            long count = 0;
            for (Map.Entry<String, User> pair : mUsers.entrySet()) {
                if ((pair.getValue().needsRefresh() || force) && ++count <= MAX_USERS_PER_REQUEST) {
                    id += "\"" + pair.getKey() + "\",";
                } else if (force) {
                    pair.getValue().markForUpdate();
                }
            }
        } else if (mUsers.get(id) == null || mUsers.get(id).mIsSet && !force){
            return;
        } else {
            id = "\"" +id+ "\"";
        }
        if (id.length() == 0) {
            return;
        }
        Log.d("Users", "Updating user data for id: " + id);
        String request = "{\"UserIDs\":["+id+"]}";

        try {
            parseJson(Webservices.getJSON(APIConstants.APICalls.USER_DETAILS, request));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public long doesNeedUpdate() {
        long count = 0;
        for (Map.Entry<String, User> pair : mUsers.entrySet()) {
            if (pair.getValue().needsRefresh())
                count++;
        }
        return count;
    }

    public void update(boolean force) {
        update(null, force);
    }

    public Map<String, User> getUsers() {
        return mUsers;
    }

    public int getActiveUserCount() {
        synchronized (mUsers) {
            int count = 0;
            for (Map.Entry<String, User> pair : mUsers.entrySet()) {
                if (pair.getValue().getCurrentFlight() != null) {
                    ++count;
                }
            }
            return count;
        }
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
                synchronized (mUsers) {
                    mUsers.put(temp.getId(), temp);
                }
            }
        }
    }


    public User getUser(String id) {
        return mUsers.get(id);
    }


    public static class User {
        private String mId = "";
        private Double mFlightTime = 0d;
        private Long mLandingCount = 0l;
        private Long mLastFlight = 0l; // Time
        private String mName;
        private Long mOnlineFlights = 0l;
        private Long mRank = 0l;
        private Long mSkills = 0l;
        private Double mStanding = 0d; // 0 -> 0% 1 -> 100%
        private Long mViolations = 0l;
        private Role mRole = Role.UNKNOWN;

        private Flight mCurrentFlight = null;

        private boolean mIsSet = false;
        private boolean mNeedsRefresh = false;
        private long mLastRefresh = 0l;

        private boolean mDontUpdate = false;

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

        public User(String id, String name) {
            mId = id;
            mIsSet = false;
            mName = name;
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
            mRole = role == null ? Role.UNKNOWN : Role.fromValue(role.intValue());

            mCurrentFlight = null;
            mNeedsRefresh = false;
            mLastRefresh = TimeProvider.getTime();
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
            this.mRole = other.mRole;
            this.mIsSet = true;
            this.mNeedsRefresh = false;
            this.mLastRefresh = TimeProvider.getTime();
        }

        public void dontupdate() {
            mDontUpdate = true;
        }

        public void markForUpdate() {
            mNeedsRefresh = true;
        }
        public boolean needsRefresh() {
            return (!mDontUpdate && mNeedsRefresh && TimeProvider.getTime() - mLastRefresh > REFRESH_THRESHOLD);
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

        public Flight getCurrentFlight() {
            return mCurrentFlight;
        }

        public void setCurrentFlight(Flight flight) {
            mCurrentFlight = flight;
        }
    }
}

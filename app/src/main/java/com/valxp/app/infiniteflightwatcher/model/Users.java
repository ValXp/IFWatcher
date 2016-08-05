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
                } else if (force && !pair.getValue().mDontUpdate) {
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
            parseJson(Webservices.getJSON(APIConstants.APICalls.USER_DETAILS, null,request));
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
        private Double mStanding = 0d; // 0 -> 0% 1 -> 100%
        private Long mViolations = 0l;
        private PilotStats mPilotStats;
        private boolean mIsAdmin;
        private boolean mIsMod;

        private Flight mCurrentFlight = null;

        private boolean mIsSet = false;
        private boolean mNeedsRefresh = false;
        private long mLastRefresh = 0l;

        private boolean mDontUpdate = false;

        public User(String id, String name) {
            mId = id;
            mIsSet = false;
            mName = name;
        }

        public class PilotStats {
            private Long mFlightTimeLongPeriod = 0l;
            private Long mGhostingCountMediumPeriod = 0l;
            private Long mGhostingCountShortPeriod = 0l;
            private Long mGrade = 0l;
            private String mGradeName = "";
            private Long mLandingCountLongPeriod = 0l;
            private Long mTotalFlightTime = 0l;
            private Long mTotalLandings = 0l;
            private Long mTotalXP = 0l;
            private Long mViolationsMediumPeriod = 0l;
            private Long mViolationsShortPeriod = 0l;

            private PilotStats(JSONObject object) throws JSONException {
                mFlightTimeLongPeriod = object.getLong("FlightTimeLongPeriod");
                mGhostingCountMediumPeriod = object.getLong("GhostingCountMediumPeriod");
                mGhostingCountShortPeriod = object.getLong("GhostingCountShortPeriod");
                mGrade = object.getLong("Grade");
                mGradeName = object.getString("GradeName");
                mLandingCountLongPeriod = object.getLong("LandingCountLongPeriod");
                mTotalFlightTime = object.getLong("TotalFlightTime");
                mTotalLandings = object.getLong("TotalLandings");
                mTotalXP = object.getLong("TotalXP");
                mViolationsMediumPeriod = object.getLong("ViolationsMediumPeriod");
                mViolationsShortPeriod = object.getLong("ViolationsShortPeriod");
            }

            public Long getFlightTimeLongPeriod() {
                return mFlightTimeLongPeriod;
            }

            public Long getGhostingCountMediumPeriod() {
                return mGhostingCountMediumPeriod;
            }

            public Long getGhostingCountShortPeriod() {
                return mGhostingCountShortPeriod;
            }

            public Long getGrade() {
                return mGrade;
            }

            public String getGradeName() {
                return mGradeName;
            }

            public Long getLandingCountLongPeriod() {
                return mLandingCountLongPeriod;
            }

            public Long getTotalFlightTime() {
                return mTotalFlightTime;
            }

            public Long getTotalLandings() {
                return mTotalLandings;
            }

            public Long getTotalXP() {
                return mTotalXP;
            }

            public Long getViolationsMediumPeriod() {
                return mViolationsMediumPeriod;
            }

            public Long getViolationsShortPeriod() {
                return mViolationsShortPeriod;
            }
        }

        private User(JSONObject object) throws JSONException {

            mPilotStats = new PilotStats(object.getJSONObject("PilotStats"));
            setUserRole(object);
            mFlightTime = object.getDouble("FlightTime");
            mLandingCount = object.getLong("LandingCount");
            mLastFlight = object.getLong("LastFlight");
            mName = object.getString("Name");
            mOnlineFlights = object.getLong("OnlineFlights");
            mStanding = object.getDouble("Standing");
            mId = object.getString("UserID");
            mViolations = object.getLong("Violations");

            mCurrentFlight = null;
            mNeedsRefresh = false;
            mLastRefresh = TimeProvider.getTime();
            mIsSet = true;
        }

        private void setUserRole(JSONObject object) throws JSONException {
            JSONArray array = object.getJSONArray("Groups");
            for (int i = 0; i < array.length(); ++i) {
                String id = array.getString(i);
                mIsAdmin = mIsAdmin || id.equalsIgnoreCase("D07AFAD8-79DF-4363-B1C7-A5A1DDE6E3C8");
                mIsMod = mIsMod || id.equalsIgnoreCase("8C93A113-0C6C-491F-926D-1361E43A5833");
            }
            if (mIsAdmin)
                mIsMod = false;
        }

        public void set(User other) {
            this.mFlightTime = other.mFlightTime;
            this.mLandingCount = other.mLandingCount;
            this.mLastFlight = other.mLastFlight;
            this.mName = other.mName;
            this.mOnlineFlights = other.mOnlineFlights;
            this.mIsAdmin = other.mIsAdmin;
            this.mIsMod = other.mIsMod;
            this.mPilotStats = other.mPilotStats;
            this.mStanding = other.mStanding;
            this.mViolations = other.mViolations;
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

        public PilotStats getPilotStats() { return mPilotStats; }

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

        public Flight getCurrentFlight() {
            return mCurrentFlight;
        }

        public void setCurrentFlight(Flight flight) {
            mCurrentFlight = flight;
        }

        public boolean isMod() {
            return mIsMod;
        }

        public boolean isAdmin() {
            return mIsAdmin;
        }
    }
}

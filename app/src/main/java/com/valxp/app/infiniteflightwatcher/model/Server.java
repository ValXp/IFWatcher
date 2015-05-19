package com.valxp.app.infiniteflightwatcher.model;

import com.valxp.app.infiniteflightwatcher.APIConstants;
import com.valxp.app.infiniteflightwatcher.TimeProvider;
import com.valxp.app.infiniteflightwatcher.Webservices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * Created by ValXp on 9/1/14.
 */
public class Server {
    public static long SERVER_UPDATE_THRESHOLD = 1000 * 60;
    private static long mLastUpdated = 0;

    private String mDescription;
    private String mId;
    private Long mMaxUsers;
    private String mName;
    private Integer mType;
    private Long mUserCount;


    public static HashMap<String, Server> getServers(HashMap<String, Server> oldServers) {
        if (TimeProvider.getTime() - mLastUpdated < SERVER_UPDATE_THRESHOLD && oldServers != null) {
            return oldServers;
        }
        mLastUpdated = TimeProvider.getTime();
        HashMap<String, Server> servers = oldServers;
        if (servers == null)
            servers = new HashMap<String, Server>();
        JSONArray array = Webservices.getJSON(APIConstants.APICalls.SESSIONS_INFO);
        if (array == null) {
            mLastUpdated = (long) (TimeProvider.getTime() - SERVER_UPDATE_THRESHOLD + (1000 * 10));
            return servers;
        }
        try {
            for (int i = 0; i < array.length(); ++i) {
                Server server = new Server(array.getJSONObject(i));
                synchronized (servers) {
                    Server old = servers.get(server.getId());
                    if (old != null) {
                        old.update(server);
                    } else {
                        servers.put(server.getId(), server);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return servers;
    }

    private Server(JSONObject object) throws JSONException {
        mId = object.getString("Id");
        mDescription = object.getString("Description");
        mMaxUsers = object.getLong("MaxUsers");
        mName = object.getString("Name");
        mType = object.getInt("Type");
        mUserCount = object.getLong("UserCount");
    }

    synchronized private void update(Server server) {
        mDescription = server.getDescription();
        mMaxUsers = server.getMaxUsers();
        mName = server.getName();
        mType = server.getType();
        mUserCount = server.getUserCount();
    }

    public String getDescription() {
        return mDescription;
    }

    public String getId() {
        return mId;
    }

    public Long getMaxUsers() {
        return mMaxUsers;
    }

    public Integer getType() {
        return mType;
    }

    public String getName() {
        return mName;
    }


    public Long getUserCount() {
        return mUserCount;
    }
}

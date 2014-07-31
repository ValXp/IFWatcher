package com.valxp.app.infiniteflightwatcher.adapters;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.TextView;

import com.valxp.app.infiniteflightwatcher.R;
import com.valxp.app.infiniteflightwatcher.model.Fleet;
import com.valxp.app.infiniteflightwatcher.model.Regions;
import com.valxp.app.infiniteflightwatcher.model.Users;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Created by ValXp on 6/22/14.
 */
public class MainListAdapter implements ExpandableListAdapter {
    public static final int REGIONS_INDEX = 0;
    public static final int USERS_INDEX = 1;
    private Context mContext;
    private Regions mRegions;
    private List<Regions.Region> mRegionList;
    private List<Users.User> mUserList;


    public MainListAdapter(Context context, Fleet fleet, Regions regions) {
        mContext = context;

        mRegions = regions;

        mRegionList = new ArrayList<Regions.Region>();
        mRegionList.addAll(mRegions);
        Collections.sort(mRegionList, new Comparator<Regions.Region>() {
            @Override
            public int compare(Regions.Region region, Regions.Region region2) {
                return region2.getPlayerCount() - region.getPlayerCount();
            }
        });

        mUserList = new ArrayList<Users.User>();
        for (Map.Entry<String, Users.User> entry : fleet.getUsers().getUsers().entrySet()) {
            Users.User user = entry.getValue();
            if (user.getCurrentFlight() != null) {
                mUserList.add(user);
            }
        }
        Log.d("MainListAdapter", "Userlist size : " + mUserList.size());
        Collections.sort(mUserList, new Comparator<Users.User>() {
            @Override
            public int compare(Users.User user, Users.User user2) {
                Regions.Region region = mRegions.regionContainingPoint(user.getCurrentFlight().getAproxLocation());
                Regions.Region region2 = mRegions.regionContainingPoint(user2.getCurrentFlight().getAproxLocation());
                if (region == null)
                    return -1;
                if (region2 == null)
                    return 1;
                return region2.hashCode() - region.hashCode();
            }
        });
        Log.d("MainListAdapter", "After sort Userlist size : " + mUserList.size());
    }

    @Override
    public void registerDataSetObserver(DataSetObserver dataSetObserver) {

    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {

    }

    @Override
    public int getGroupCount() {
        return 2;
    }

    @Override
    public int getChildrenCount(int i) {
        if (i == REGIONS_INDEX)
            return mRegionList.size();
        if (i == USERS_INDEX)
            return mUserList.size();
        return 0;
    }

    @Override
    public Object getGroup(int i) {
        if (i == REGIONS_INDEX)
            return mRegionList;
        if (i == USERS_INDEX)
            return mUserList;
        return null;
    }

    @Override
    public Object getChild(int i, int i2) {
        List<Object> list = (List<Object>) getGroup(i);
        if (list != null)
            return list.get(i2);
        return null;
    }

    @Override
    public long getGroupId(int i) {
        return i;
    }

    @Override
    public long getChildId(int i, int i2) {
        return i2;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int i, boolean b, View view, ViewGroup viewGroup) {
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.group_view, null);
        }
        TextView name = (TextView) view.findViewById(R.id.group_name);
        TextView count = (TextView) view.findViewById(R.id.group_count);

        if (i == REGIONS_INDEX) {
            name.setText("Regions");
            count.setText("");
        } else if (i == USERS_INDEX) {
            name.setText("Users");
            count.setText("(" + getChildrenCount(i) + ")");
        }

        return view;
    }

    @Override
    public View getChildView(int i, int i2, boolean b, View view, ViewGroup viewGroup) {
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.item_view, null);
        }
        TextView name = (TextView) view.findViewById(R.id.item_name);
        TextView count = (TextView) view.findViewById(R.id.item_count);
        view.setTag(null);

        Object item = getChild(i, i2);
        if (i == REGIONS_INDEX && item != null) {
            Regions.Region region = (Regions.Region)item;
            name.setText(region.getName());
            count.setText("(" + region.getPlayerCount() + ")");
            int color = region.getPlayerCount() == 0 ? android.R.color.darker_gray : android.R.color.black;
            name.setTextColor(mContext.getResources().getColor(color));
            count.setTextColor(mContext.getResources().getColor(color));
            view.setTag(region);
        } else if (i == USERS_INDEX && item != null) {
            Users.User user = (Users.User) item;
            int color = android.R.color.black;
            switch (user.getRole()) {
                case UNKNOWN:
                    break;
                case USER:
                    break;
                case TESTER:
                    color = R.color.tester_color;
                    break;
                case ADMIN:
                    color = R.color.admin_color;
                    break;
            }
            if (user.getRank() == 1)
                color = R.color.gold_color;
            name.setTextColor(mContext.getResources().getColor(color));
            count.setTextColor(mContext.getResources().getColor(android.R.color.black));
            name.setText(user.getName());
            String text;
            if (user.getCurrentFlight() == null)
                text = "Offline";
            else {
                view.setTag(user.getCurrentFlight());
                Regions.Region region = mRegions.regionContainingPoint(user.getCurrentFlight().getAproxLocation());
                if (region == null) {
                    text = "Lost";
                } else {
                    text = "In " + region.getName();
                }
            }
            count.setText(text);
        }
        return view;
    }

    @Override
    public boolean isChildSelectable(int i, int i2) {
        return true;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void onGroupExpanded(int i) {

    }

    @Override
    public void onGroupCollapsed(int i) {

    }

    @Override
    public long getCombinedChildId(long l, long l2) {
        return getCombinedGroupId(l) + l2;
    }

    @Override
    public long getCombinedGroupId(long l) {
        return l * 1000;
    }
}

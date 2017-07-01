package com.valxp.app.infiniteflightwatcher.activities;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.valxp.app.infiniteflightwatcher.APIConstants;
import com.valxp.app.infiniteflightwatcher.R;
import com.valxp.app.infiniteflightwatcher.RadarLoadingView;
import com.valxp.app.infiniteflightwatcher.TimeProvider;
import com.valxp.app.infiniteflightwatcher.Utils;
import com.valxp.app.infiniteflightwatcher.caching.FileDiskCache;
import com.valxp.app.infiniteflightwatcher.model.Liveries;
import com.valxp.app.infiniteflightwatcher.model.Regions;
import com.valxp.app.infiniteflightwatcher.model.Server;

import java.util.HashMap;

public class ServerChooserActivity extends Activity {
    public static final String INTENT_SERVER_ID = "INTENT_SERVER_ID";

    private RecyclerView mServerListView;

    private ServerListAdapter mServerListAdapter;
    private HashMap<String, Server> mServers;
    private Thread mThread;
    private TextView mTitleText;
    private RadarLoadingView mRadarView;
    FileDiskCache mDiskCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.initContext(this);
        APIConstants.init(this);
        setContentView(R.layout.activity_server_chooser);
        mServerListView = (RecyclerView) findViewById(R.id.server_list);
        mTitleText = (TextView) findViewById(R.id.server_chooser_title);
        mRadarView = (RadarLoadingView) findViewById(R.id.radar_loading);
        mTitleText.setText(R.string.connecting_to_if);
        mDiskCache = new FileDiskCache(this, "data_cache", APIConstants.APICalls.LIVERIES);

        mServers = null;
        mServerListAdapter = new ServerListAdapter();
        mServerListView.setAdapter(mServerListAdapter);
        mServerListView.setLayoutManager(new LinearLayoutManager(this));

        mServerListView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Pre-load regions because it can take a while
                Regions.getInstance(ServerChooserActivity.this);
            }
        }).start();
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String path = mDiskCache.getFilePath("airplanes.txt", true, true);
                Liveries.initLiveries(ServerChooserActivity.this, path);
                while (mServers == null) {
                    mServers = Server.getServers(mServers);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTitleText.setText(R.string.please_choose_a_server);
                        mRadarView.setVisibility(View.GONE);
                        mServerListView.setVisibility(View.VISIBLE);
                        mServerListView.invalidate();
                    }
                });
            }
        });

        mThread.start();
        TimeProvider.synchronizeWithInternet();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int ret = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (ret != ConnectionResult.SUCCESS) {
            Dialog errDialog = GoogleApiAvailability.getInstance().getErrorDialog(this, ret, 0);
            errDialog.setCancelable(false);
            errDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    finish();
                }
            });
            errDialog.show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }


    public class ServerListAdapter extends RecyclerView.Adapter<ServerListAdapter.ServerViewHolder> {


        @Override
        public ServerViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            ServerViewHolder vh = new ServerListAdapter.ServerViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.server_card_item, viewGroup, false));
            return vh;
        }

        @Override
        public void onBindViewHolder(ServerViewHolder serverViewHolder, int i) {
            serverViewHolder.setItem(i);
        }

        @Override
        public int getItemCount() {
            if (mServers == null)
                return 0;
            return mServers.size();
        }

        public class ServerViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            private CardView mCard;
            private TextView mTitle;
            private TextView mPlayerCount;
            private int mId = -1;

            public ServerViewHolder(View itemView) {
                super(itemView);
                mCard = (CardView)itemView;
                mCard.setCardElevation(Utils.dpToPx(5));
                mCard.setOnClickListener(this);
                mTitle = (TextView) mCard.findViewById(R.id.server_title);
                mPlayerCount = (TextView) mCard.findViewById(R.id.player_count);
            }

            public void setItem(int i) {
                if (mServers == null || i >= mServers.size())
                    return;
                mId = i;
                Server server = (Server)mServers.values().toArray()[i];
                mTitle.setText(server.getName());
                mPlayerCount.setText("(" + server.getUserCount() + "/" + server.getMaxUsers() + ")");
            }

            @Override
            public void onClick(View view) {
                if (mServers == null || mId >= mServers.size())
                    return;
                Server server = (Server)mServers.values().toArray()[mId];

                Intent intent = new Intent(ServerChooserActivity.this, MapsActivity.class);
                intent.putExtra(INTENT_SERVER_ID, server.getId());
                startActivity(intent);
            }
        }
    }

}

package com.valxp.app.infiniteflightwatcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by ValXp on 7/29/14.
 */
public class AppUpdater {

    public static void checkUpdate(final Activity activity) {
        new Thread(new Runnable() {
            @Override
            public void run() {

                URL url = null;
                try {
                    url = new URL("http://valxp.net/IFWatcher/version.txt");
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
                try {
                    InputStream stream = url.openStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                    List<String> lines = new ArrayList<String>();
                    String line = null;
                    do {
                        line = reader.readLine();
                        if (line != null)
                            lines.add(line);
                    } while (line != null);
                    if (lines.size() == 0)
                        return;
                    final int newVersion = Integer.decode(lines.get(0));
                    String description = "";
                    Iterator<String> it = lines.iterator();
                    if (it.hasNext())
                        it.next();
                    while (it.hasNext()) {
                        description += it.next() + "\n";
                    }
                    final String changelog = description;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            PackageInfo pInfo = null;
                            try {
                                pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                            } catch (PackageManager.NameNotFoundException e) {
                                e.printStackTrace();
                            }
                            if (newVersion > pInfo.versionCode) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                                builder.setTitle("New update available!\nCurrent : v" + pInfo.versionCode + " Latest : v" + newVersion);
                                String message = "Do you want to download the new version ?\n\n";
                                if (changelog.length() > 0)
                                    message += "Changelog :\n" + changelog;
                                builder.setMessage(message);
                                builder.setPositiveButton("Okay!", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://valxp.net/IFWatcher/IFWatcher.apk"));
                                        activity.startActivity(intent);
                                    }
                                });
                                builder.setNegativeButton("Leave me alone", null);
                                builder.create().show();
                            } else {
                                Toast.makeText(activity, "You are up to date ! (v" + pInfo.versionCode + ")", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

}

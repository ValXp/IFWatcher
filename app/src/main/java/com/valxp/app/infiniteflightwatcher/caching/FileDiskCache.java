package com.valxp.app.infiniteflightwatcher.caching;

import android.content.Context;
import android.util.Log;

import com.valxp.app.infiniteflightwatcher.APIConstants;
import com.valxp.app.infiniteflightwatcher.Utils;
import com.valxp.app.infiniteflightwatcher.Webservices;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

/**
 * Created by ValXp on 12/1/15.
 */
public class FileDiskCache {
    private String mCacheName;
    private Context mContext;
    private String mCacheLocation;
    private APIConstants.APICalls mRemotePath;

    public FileDiskCache(Context ctx, String cacheName, APIConstants.APICalls remotePath) {
        mCacheName = cacheName;
        mContext = ctx;
        mRemotePath = remotePath;
        mCacheLocation = mContext.getCacheDir().getAbsolutePath() + "/" + mCacheName + "/";
        File directory = new File(mCacheLocation);
        directory.mkdirs();
        if (!directory.isDirectory()) {
            Log.w("FileDiskCache", "Failed to create cache directory! " + directory);
        }
    }

    public String getFilePath(String fileName, boolean blockIfNotPresent) {
        String fullFileName = mCacheLocation + fileName;
        String metaName = fullFileName + ".meta";
        String etag = getEtag(metaName);
        File file = new File(fullFileName);
        if (blockIfNotPresent && (!file.exists() || etag == null || System.currentTimeMillis() - file.lastModified() > 1000 * 3600 * 24 * 14)) // every 2 weeks
        {
            String benchName = "Downloading " + fileName;
            Utils.Benchmark.start(benchName);
            String newEtag = Webservices.getFile(mRemotePath, fileName, etag, fullFileName);
            Utils.Benchmark.stopAndLog(benchName);

            file = new File(fullFileName);
            file.setLastModified(System.currentTimeMillis());
            if (file.exists() && newEtag != null) {
                writeEtag(metaName, newEtag);
            }
        }
        return file.exists() ? fullFileName : null;
    }

    private void writeEtag(String fileName, String etag) {
        try {
            FileWriter writer = new FileWriter(fileName, false);
            writer.write(etag + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getEtag(String fileName) {
        File file = new File(fileName);
        if (!file.exists())
            return null;
        try {
            Scanner scanner = new Scanner(file);
            return scanner.nextLine();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}

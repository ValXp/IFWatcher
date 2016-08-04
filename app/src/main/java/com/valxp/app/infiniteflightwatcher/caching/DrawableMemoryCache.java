package com.valxp.app.infiniteflightwatcher.caching;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.LruCache;

import com.valxp.app.infiniteflightwatcher.APIConstants;

import java.util.WeakHashMap;

/**
 * Created by ValXp on 12/1/15.
 */
public class DrawableMemoryCache {

    private Context mContext;
    LruCache<String, Drawable> mMemoryCache;
    private FileDiskCache mDiskCache;

    public DrawableMemoryCache(Context ctx, String cacheName, APIConstants.APICalls remotePath) {
        mContext = ctx;
        mDiskCache = new FileDiskCache(ctx, cacheName, remotePath);
        mMemoryCache = new LruCache<>(2 * 1024 * 1024);
    }

    public Drawable getDrawable(String id) {
        return getDrawable(id, false);
    }

    public Drawable getDrawable(String id, boolean blockIfNotPresent) {
        // Don't bother if it's a dummy id.
        if (id.equals("0"))
            return null;
        Drawable drawable = mMemoryCache.get(id);
        if (drawable == null) {
            String fileName = mDiskCache.getFilePath(id + ".png", blockIfNotPresent);
            if (fileName != null) {
                drawable = loadDrawable(fileName);
                mMemoryCache.put(id, drawable);
            }
        }
        return drawable;
    }

    private Drawable loadDrawable(String fileName) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        //options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(fileName, options);
        BitmapDrawable drawable = new BitmapDrawable(mContext.getResources(), bitmap);
        return drawable;
    }

}

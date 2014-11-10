package com.valxp.app.infiniteflightwatcher.heatmap;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;

import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileProvider;

import java.io.ByteArrayOutputStream;
import java.util.Vector;

public class HeatMapTileProvider implements TileProvider {
    private static final String TAG = "HeatMapTileProvider";
    private static final int TILE_WIDTH = 100;
    private static final int TILE_HEIGHT = 100;
    private Vector<Heatmap> mHeatMaps = new Vector<Heatmap>();

    @Override
    public Tile getTile(int x, int y, int zoom) {
        return tileGenerator(x, y, zoom);
    }

    public void addHeatMap(Heatmap map) {
        mHeatMaps.add(map);
    }

    public void removeHeatMap(Heatmap map) {
        mHeatMaps.remove(map);
    }

    public boolean containsHeatMap(Heatmap map) {
        return mHeatMaps.contains(map);
    }

    public void removeAllHeatMaps() {
        mHeatMaps.clear();
    }

    private Tile tileGenerator(int x, int y, int zoom) {
        long before = System.currentTimeMillis();
        Bitmap bmp = Bitmap.createBitmap(TILE_WIDTH, TILE_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        draw(cv, TILE_WIDTH, TILE_HEIGHT, x, y, zoom);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
        Tile tile = new Tile(TILE_WIDTH, TILE_HEIGHT, stream.toByteArray());
        Log.d(TAG, "Tile total time : " + (System.currentTimeMillis() - before));
        return tile;
    }

    private int valueToColor(double value, double maximum) {
        int alpha = 0x55;
        int red = 0;
        int green = 0;
        int blue = 0;
        double step = maximum / 6.0;

        if (value <= step) {
            alpha = (int) (0x55 * (value / step));
            blue = 0xff;
        } else if (value <= step * 2) { // blue 100%. Green increasing
            double inRatio = (value - step) / step;
            blue = 0xff;
            green = (int) (0xff * inRatio);
        } else if (value <= step * 3) { // Green 100%. blue decreasing
            double inRatio = (value - (2 * step)) / step;
            green = 0xff;
            blue = (int) (0xff * (1 - inRatio));
        } else if (value <= step * 4) { //Green 100%. red increasing
            double inRatio = (value - (3 * step)) / step;
            green = 0xff;
            red = (int) (0xff * inRatio);
        } else if (value <= step * 5) { // Red 100%. green decreasing
            double inRatio = (value - (4 * step)) / step;
            red = 0xff;
            green = (int) (0xff * (1 - inRatio));
        } else {
            red = 0xff;
        }
        return blue | (green << 8) | (red << 16) | (alpha << 24);
    }

    private void draw(Canvas cv, int width, int height, int xPos, int yPos, int zoom) {
        if (mHeatMaps.size() == 0)
            return;
        long readHeatmapTotal = 0;
        long writeCanvasTotal = 0;
        long begin = System.currentTimeMillis();

        double xMin = ((double) xPos) / (1 << zoom);
        double yMin = ((double) yPos) / (1 << zoom);
        double xMax = ((double) (xPos + 1)) / (1 << zoom);
        double yMax = ((double) (yPos + 1)) / (1 << zoom);
        double xDelta = xMax - xMin;
        double yDelta = yMax - yMin;
        double xRatio = xDelta / width;
        double yRatio = yDelta / height;
        Paint pt = new Paint();


        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                double mX = x * xRatio + xMin;
                double mY = y * yRatio + yMin;
                long before = System.currentTimeMillis();
                float val = 0;
                float max = 0;
                for (Heatmap m : mHeatMaps) {
                    val = Math.max(m.getMercatorPoint(mX, mY), val);
                    max = Math.max(m.getMaximum(), max);
                }
                int color = valueToColor(val, max);
                readHeatmapTotal += System.currentTimeMillis() - before;
                before = System.currentTimeMillis();
                pt.setColor(color);
                cv.drawPoint(x, y, pt);
                writeCanvasTotal += System.currentTimeMillis() - before;
            }
        }
        Log.d(TAG, "Tile generated : total : " + (System.currentTimeMillis() - begin) + " readHeatmap : " + readHeatmapTotal + " writeCanvas : " + writeCanvasTotal);
    }
}

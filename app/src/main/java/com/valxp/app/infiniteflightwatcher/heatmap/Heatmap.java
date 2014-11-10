package com.valxp.app.infiniteflightwatcher.heatmap;

import android.util.Log;

public class Heatmap {
    float[] mMap;
    int mXRes;
    int mYRes;
    float mMaximum;
    float[] mKernel;
    int mKernelWidth;
    int mKernelHeight;
    double mXOffset;
    double mYOffset;
    double mXRatio;
    double mYRatio;

    public Heatmap(int xRes, int yRes, int blurRadius, double xOffset, double yOffset, double xDelta, double yDelta) {
        Log.d("HeatMap", "xRes="+xRes+" yRes="+yRes+" blurRadius="+blurRadius+" xOffset="+xOffset+" yOffset="+yOffset+" xDelta="+xDelta+" yDelta="+yDelta);
        mXOffset = xOffset;
        mYOffset = yOffset;
        mXRatio = xRes / xDelta;
        mYRatio = yRes / yDelta;

        mXRes = xRes;
        mYRes = yRes;
        mMap = new float[mXRes * mYRes];
        clear();
        generateKernel(blurRadius);
    }

    public void addMercatorPoint(double x, double y, float weight) {
        x = (x - mXOffset) * mXRatio;
        y = (y - mYOffset) * mYRatio;
        addWeight((int) x, (int) y, weight);
    }

    public float getMercatorPoint(double x, double y) { // Anti-aliased
        x = (x - mXOffset) * mXRatio;
        y = (y - mYOffset) * mYRatio;
        double minX = (int)x;
        double maxX = (int)x + 1;
        double xRatio = x - minX;
        double minY = (int)y;
        double maxY = (int)y + 1;
        double yRatio = y - minY;

        float xValue = (float) ((readAt((int)minX, (int)y) * (1 - xRatio)) + (readAt((int)maxX, (int)y) * (xRatio)));
        float yValue = (float) ((readAt((int)x, (int)minY) * (1 - yRatio)) + (readAt((int)x, (int)maxY) * (yRatio)));
        
        return (xValue + yValue + readAt((int) x, (int) y)) / 3;
    }

    public void clear() {
        for (int i = 0; i < mXRes * mYRes; ++i)
            mMap[i] = 0;
        mMaximum = 0;
    }

    public float getMaximum() {
        return mMaximum;
    }

    private float gaussian(float x, float mu, float sigma) {
        return (float) Math.exp(-(((x - mu) / (sigma)) * ((x - mu) / (sigma))) / 2.0);
    }

    private void generateKernel(int blurRadius) {
        mKernelWidth = 2 * blurRadius + 1;
        mKernelHeight = 2 * blurRadius + 1;
        mKernel = new float[mKernelWidth * mKernelHeight];

        float sigma = blurRadius / 2.0f;

        float sum = 0;
        for (int row = 0; row < mKernelWidth; row++)
            for (int col = 0; col < mKernelHeight; col++) {
                mKernel[(row * mKernelWidth) + col] = gaussian(row, blurRadius, sigma) * gaussian(col, blurRadius, sigma);
                sum += mKernel[row * mKernelWidth + col];
            }

        // normalize
        for (int row = 0; row < mKernelWidth; row++)
            for (int col = 0; col < mKernelHeight; col++) {
                mKernel[(row * mKernelWidth) + col] /= sum;
            }
    }

    private void addWeight(int xPos, int yPos, float weight) {
        for (int x = 0; x < mKernelWidth; ++x) {
            for (int y = 0; y < mKernelHeight; ++y) {
                int mapX = xPos + x - (mKernelWidth / 2);
                int mapY = yPos + y - (mKernelHeight / 2);
                float oldValue = readAt(mapX, mapY);
                writeAt(mapX, mapY, kernelAt(x, y) * weight + oldValue);
            }
        }
    }

    private float kernelAt(int x, int y) {
        return mKernel[y * mKernelWidth + x];
    }

    private void writeAt(int x, int y, float weight) {
        if (x < 0 || y < 0 || x >= mXRes || y >= mYRes || (y * mXRes) + x >= (mXRes * mYRes) || weight < 0) {
//            Log.d("HeatMap", "invalid write of " + weight + " at " + x + "x" + y);
            return;
        }
        mMap[(y * mXRes) + x] = weight;
        if (weight > mMaximum)
            mMaximum = weight;
    }

    private float readAt(int x, int y) {
        if (x < 0 || y < 0 || x >= mXRes || y >= mYRes || (y * mXRes) + x >= (mXRes * mYRes)) 
            return 0;
//        Log.d("HeatMap", "successful read of  " + mMap[(y * mXRes) + x] + " at " + x + "x" + y);
        return mMap[(y * mXRes) + x];
    }

    public void dump() {
        for (int y = 0; y < mYRes; y++) {
            String line = "";
            for (int x = 0; x < mXRes; x++) {
                line = line + mMap[(y * mXRes) + x] + " ";
            }
//            Log.d("HeatMap", line);
        }
    }

}

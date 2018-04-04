package com.valxp.app.infiniteflightwatcher;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/**
 * TODO: document your custom view class.
 */
public class RadarLoadingView extends View {
    private static float DEGREES_PER_S = 100f;
    private static int BLIP_COUNT = 10;
    private Bitmap mBackground;
    private Bitmap mSweep;
    private Bitmap mBlip;
    private float mAngle;
    private long mTimer;
    private ArrayList<Blip> mBlips;
    private Random mRand;

    private class Blip
    {
        public Blip()
        {
            life = mRand.nextFloat();
            x = 0;
            y = 0;
            paint = new Paint();
        }

        public void Draw(Canvas canvas, float elapsedSec, float currentAngle, float scale)
        {
            // We make sure life lasts exactly one sweep rotation
            life -= elapsedSec * (DEGREES_PER_S / 360f);

            // Blip died, time to recreate it
            if (life <= -mRand.nextFloat())
            {
                double radianAngle = Math.toRadians(currentAngle - 90);
                float multiplier = Math.abs(mRand.nextFloat());
                x = (float)Math.cos(radianAngle) * multiplier;
                y = (float)Math.sin(radianAngle) * multiplier;
                life = 1;
            }
            if (life <= 0)
            {
                return;
            }

            // Render only if position is set
            if (x < 0.1 && y < 0.1)
                return;
            canvas.save();
            paint.setAlpha((int)(255 * life));
            canvas.drawBitmap(mBlip, x * scale - (mBlip.getWidth() / 2), y * scale - (mBlip.getHeight() / 2), paint);
        }
        private float x, y;
        private float life;
        private Paint paint;
    }

    public RadarLoadingView(Context context) {
        super(context);
        init(null, 0);
    }

    public RadarLoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public RadarLoadingView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {

        mBackground = BitmapFactory.decodeResource(getResources(), R.drawable.loading_background);
        mSweep = BitmapFactory.decodeResource(getResources(), R.drawable.loading_sweep);
        mBlip = BitmapFactory.decodeResource(getResources(), R.drawable.blip);
        mAngle = 0;
        mTimer = System.currentTimeMillis();
        mRand = new Random();
        mBlips = new ArrayList<>();
        for (int i = 0; i < BLIP_COUNT; ++i)
            mBlips.add(new Blip());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float elapsed = (System.currentTimeMillis() - mTimer) / 1000.0f;
        mAngle -= elapsed * DEGREES_PER_S;

        // TODO: consider storing these as member variables to reduce
        // allocations per draw cycle.
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();

        int contentWidth = getWidth() - paddingLeft - paddingRight;
        int contentHeight = getHeight() - paddingTop - paddingBottom;

        float scaleW = contentWidth / (float)mBackground.getWidth();
        float scaleH = contentHeight / (float)mBackground.getHeight();
        float scale = scaleH > scaleW ? scaleW : scaleH;
        canvas.save();
        canvas.scale(scale, scale);
        canvas.translate(paddingLeft, paddingTop);


        canvas.drawBitmap(mBackground, 0, 0, null);

        canvas.translate((mSweep.getWidth() / 2), (mSweep.getHeight() / 2));

        for (Blip blip: mBlips) {
            blip.Draw(canvas, elapsed, mAngle, mBackground.getWidth() / 2.1f);
        }

        canvas.rotate(mAngle);
        canvas.drawBitmap(mSweep, -(mSweep.getWidth() / 2), -(mSweep.getHeight() / 2), null);
        canvas.restore();
        mTimer = System.currentTimeMillis();

        postInvalidateDelayed(33);
    }


}

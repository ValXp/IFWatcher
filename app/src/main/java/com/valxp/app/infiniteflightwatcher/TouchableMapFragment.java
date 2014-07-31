package com.valxp.app.infiniteflightwatcher;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.gms.maps.SupportMapFragment;

/**
 * Created by ValXp on 7/30/14.
 * Stolen from here : http://dimitar.me/how-to-detect-a-user-pantouchdrag-on-android-map-v2/
 */
public class TouchableMapFragment extends SupportMapFragment {
    public View mOriginalContentView;
    public TouchableWrapper mTouchView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        mOriginalContentView = super.onCreateView(inflater, parent, savedInstanceState);
        mTouchView = new TouchableWrapper(getActivity());
        mTouchView.addView(mOriginalContentView);
        return mTouchView;
    }

    @Override
    public View getView() {
        return mOriginalContentView;
    }

    public static class TouchableWrapper extends FrameLayout {
        private long lastTouched = 0;
        private static final long SCROLL_TIME = 50L; // 200 Milliseconds, but you can adjust that to your liking
        private UpdateMapAfterUserInteraction updateMapAfterUserInteraction;

        public TouchableWrapper(Context context) {
            super(context);
            // Force the host activity to implement the UpdateMapAfterUserInteraction Interface
            try {
                updateMapAfterUserInteraction = (MapsActivity) context;
            } catch (ClassCastException e) {
                throw new ClassCastException(context.toString() + " must implement UpdateMapAfterUserInteraction");
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    if (ev.getHistorySize() > 0 && ev.getHistoricalX(0, 0) > 30)
                        updateMapAfterUserInteraction.onUpdateMapAfterUserInteraction();
                    break;
            }
            return super.dispatchTouchEvent(ev);
        }

        // Map Activity must implement this interface
        public interface UpdateMapAfterUserInteraction {
            public void onUpdateMapAfterUserInteraction();
        }
    }
}

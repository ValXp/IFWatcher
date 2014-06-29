package com.valxp.app.infiniteflightwatcher;

import android.content.Context;
import android.support.v4.util.LongSparseArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.valxp.app.infiniteflightwatcher.model.Flight;
import com.valxp.app.infiniteflightwatcher.model.Users;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ValXp on 6/25/14.
 */
public class InfoPane extends LinearLayout {

    private Context mContext;

    private LinearLayout mLeftPane;
    private LinearLayout mRightPane;

    private Map<FlightIds, TextView> mLeftTexts;
    private HashMap<UserIds, TextView> mRightTexts;


    public enum FlightIds{
      CallSign,
      Plane,
      Speed,
      Altitude,
      FlightDuration,
      LastReport
    };

    public enum UserIds {
        Name,
        Rank,
        Standing,
        XP,
        FlightTime,
        LandingCount,
        Violations,
        OnlineFlights
    };

    public InfoPane(Context context) {
        super(context);
        mContext = context;
    }

    public InfoPane(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public InfoPane(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        init();
    }

    private void init() {

        mLeftPane = (LinearLayout) findViewById(R.id.left_pane);
        mRightPane = (LinearLayout) findViewById(R.id.right_pane);

        mLeftTexts = new HashMap<FlightIds, TextView>();
        mRightTexts = new HashMap<UserIds, TextView>();


        int i = 0;
        for (FlightIds id : FlightIds.class.getEnumConstants()) {
            addText(id, FlightIds.class.getEnumConstants().length > ++i);
        }
        i = 0;
        for (UserIds id : UserIds.class.getEnumConstants()) {
            addText(id, UserIds.class.getEnumConstants().length > ++i);
        }

    }

    private TextView newText() {
        TextView text = new TextView(mContext);
        text.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        text.setTextColor(0xff000000);
        return text;
    }

    private View newSeparator() {
        View sep = new View(mContext);

        sep.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        sep.setBackgroundColor(0xFF000000);
        return sep;
    }

    private void addText(UserIds id, boolean appendSeparator) {
        TextView text = newText();
        mRightPane.addView(text);
        mRightTexts.put(id, text);
        if (appendSeparator)
            mRightPane.addView(newSeparator());
    }

    private void addText(FlightIds id, boolean appendSeparator) {
        TextView text = newText();
        mLeftPane.addView(text);
        mLeftTexts.put(id, text);
        if (appendSeparator)
            mLeftPane.addView(newSeparator());
    }


    public void show(Flight flight) {
        if (getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
            TranslateAnimation trans = new TranslateAnimation(-600, 0, 0, 0);
            trans.setInterpolator(new DecelerateInterpolator());
            trans.setDuration(500);
            trans.setFillAfter(false);
            AlphaAnimation alpha = new AlphaAnimation(0, 1);
            alpha.setDuration(200);
            alpha.setStartOffset(300);
            alpha.setFillAfter(true);
            AnimationSet set = new AnimationSet(false);
            set.addAnimation(alpha);
            set.addAnimation(trans);
            startAnimation(set);
        }



        LongSparseArray<Flight.FlightData> history =  flight.getFlightHistory();
        Flight.FlightData lastData = history.valueAt(history.size() - 1);
        mLeftTexts.get(FlightIds.CallSign).setText(flight.getCallSign());
        mLeftTexts.get(FlightIds.Plane).setText(flight.getAircraftName());
        mLeftTexts.get(FlightIds.Speed).setText(Math.round(lastData.speed) + " kts");
        mLeftTexts.get(FlightIds.Altitude).setText(Math.round(lastData.altitude) + " feet");
        String lastUpdate = "";
        Long lastUpdateSeconds = lastData.getAgeMs() / 1000;
        if (lastUpdateSeconds > 0) {
            if (lastUpdateSeconds > 60)
                lastUpdate = (lastUpdateSeconds / 60) + " minute" + (lastUpdateSeconds / 60 > 1 ? "s" : "") + " ago";
            else
                lastUpdate = lastUpdateSeconds + " second" + (lastUpdateSeconds > 1 ? "s" : "") + " ago";
        } else {
            lastUpdate = "Right now";
        }
        mLeftTexts.get(FlightIds.FlightDuration).setText("Flight duration: " + (history.valueAt(0).getAgeMs() / 60000) + "m");
        mLeftTexts.get(FlightIds.LastReport).setText(lastUpdate);

        Users.User user = flight.getUser();
        if (user.isSet()) {
            mRightTexts.get(UserIds.Name).setText(flight.getDisplayName());
            mRightTexts.get(UserIds.Rank).setText("Rank: " + user.getRank().toString());
            mRightTexts.get(UserIds.Standing).setText("Standing: " + Math.round(user.getStanding() * 100)+ "%");
            mRightTexts.get(UserIds.XP).setText("XP: " + user.getSkills());
            mRightTexts.get(UserIds.FlightTime).setText("Total Flight Time: " + (int)Math.floor(user.getFlightTime() / 60) + "h");
            mRightTexts.get(UserIds.LandingCount).setText("Landings: " + user.getLandingCount());
            mRightTexts.get(UserIds.Violations).setText("Violations: " + user.getViolations());
            mRightTexts.get(UserIds.OnlineFlights).setText("Online Flights : " + user.getOnlineFlights());
        }

    }

    public void hide() {
        if (getVisibility() != View.GONE) {
            setVisibility(View.GONE);
            TranslateAnimation trans = new TranslateAnimation(0, -400, 0, 0);
            trans.setInterpolator(new AccelerateInterpolator());
            trans.setDuration(500);
            trans.setFillAfter(false);
            AlphaAnimation alpha = new AlphaAnimation(1, 0);
            alpha.setDuration(200);
            alpha.setFillAfter(true);
            AnimationSet set = new AnimationSet(false);
            set.addAnimation(alpha);
            set.addAnimation(trans);
            startAnimation(set);
        }
    }
}

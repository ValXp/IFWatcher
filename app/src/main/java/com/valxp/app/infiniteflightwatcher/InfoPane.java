package com.valxp.app.infiniteflightwatcher;

import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v4.util.LongSparseArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.valxp.app.infiniteflightwatcher.model.Flight;
import com.valxp.app.infiniteflightwatcher.model.Users;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ValXp on 6/25/14.
 */
public class InfoPane extends RelativeLayout implements View.OnClickListener {

    private Context mContext;

    private LinearLayout mRightPane;
    private LinearLayout mLeftPane;

    private ImageView mPlaneImage;
    private TextView mTapToSeeMore;
    private View mImageLayout;
    private View mInnerInfoPane;
    private ImageView mMyLoc;

    private Map<FlightIds, TextView> mRightTexts;
    private HashMap<UserIds, TextView> mLeftTexts;

    private boolean mIsFullyDisplayed = false;
    private boolean mFollow;
    private LayoutTransition mTransition;


    public enum FlightIds{
        CallSign,
        Plane,
        Speed,
        Altitude,
        FlightDuration,
        TotalDistance,
        EndToEndDistance,
        LastReport
    };

    public enum UserIds {
        Name,
        Roles,
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

    private LayoutTransition createTransition() {
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(200);
        transition.setDuration(LayoutTransition.APPEARING, 200);
        transition.setStartDelay(LayoutTransition.APPEARING, 200);
        transition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        transition.setStartDelay(LayoutTransition.DISAPPEARING, 200);
        return transition;
    }

    private void init() {
        mTransition = createTransition();

        mRightPane = (LinearLayout) findViewById(R.id.right_pane);
        mLeftPane = (LinearLayout) findViewById(R.id.left_pane);

        mRightPane.setLayoutTransition(mTransition);
        mLeftPane.setLayoutTransition(mTransition);

        mPlaneImage = (ImageView) findViewById(R.id.plane_image);
        mTapToSeeMore = (TextView) findViewById(R.id.tap_to_see_more);

        mImageLayout = findViewById(R.id.image_layout);
        mInnerInfoPane = findViewById(R.id.inner_info_pane);

        mMyLoc = (ImageView) findViewById(R.id.my_loc);

        mRightPane.setOnClickListener(this);
        mLeftPane.setOnClickListener(this);
        mPlaneImage.setOnClickListener(this);
        mTapToSeeMore.setOnClickListener(this);
        mMyLoc.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                setFollow(!isFollowing());
            }
        });

        mRightTexts = new HashMap<FlightIds, TextView>();
        mLeftTexts = new HashMap<UserIds, TextView>();


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

    private View newSeparator(Object tag) {
        View sep = new View(mContext);

        sep.setTag(tag);
        sep.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        sep.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        return sep;
    }

    private void addText(UserIds id, boolean appendSeparator) {
        TextView text = newText();
        mLeftPane.addView(text);
        mLeftTexts.put(id, text);
        if (appendSeparator)
            mLeftPane.addView(newSeparator(text));
    }

    private void addText(FlightIds id, boolean appendSeparator) {
        TextView text = newText();
        mRightPane.addView(text);
        mRightTexts.put(id, text);
        if (appendSeparator)
            mRightPane.addView(newSeparator(text));
    }

    private void updatePlaneImage(Flight flight) {
        String plane = flight.getAircraftName();
        plane = plane.replace(" ", "_").replace("-", "_").replace("/", "_").toLowerCase();
        plane = "image_" + plane;

        Field[] fields = R.drawable.class.getDeclaredFields();
        for (Field field : fields) {
            if (field.getName().equals(plane)) {
                try {
                    mPlaneImage.setImageResource(field.getInt(R.drawable.class));
                    mPlaneImage.setVisibility(View.VISIBLE);
                    return;
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        mPlaneImage.setVisibility(View.GONE);
    }

    @Override
    public void onClick(View view) {
        mIsFullyDisplayed = !mIsFullyDisplayed;
        updateFullVisibility();
    }

    private void updateFullVisibility() {
        for (int i = 0; i < mLeftPane.getChildCount(); ++i) {
            mLeftPane.getChildAt(i).setVisibility(i > 2 && !mIsFullyDisplayed ? GONE : VISIBLE);
        }
        for (int i = 0; i < mRightPane.getChildCount(); ++i) {
            mRightPane.getChildAt(i).setVisibility(i > 2 && !mIsFullyDisplayed ? GONE : VISIBLE);
        }
        mTapToSeeMore.setVisibility(mIsFullyDisplayed ? GONE : VISIBLE);
    }

    public void setFollow(boolean follow) {
        mFollow = follow;

        String color = mFollow ? "#000000" : "#50000000";
        Drawable d = getResources().getDrawable(android.R.drawable.ic_menu_mylocation);
        d.setColorFilter(Color.parseColor(color), PorterDuff.Mode.SRC_ATOP);
        mMyLoc.setImageDrawable(d);
    }

    public boolean isFollowing() {
        return mFollow;
    }

    public boolean show(Flight flight) {

        boolean isUpdate = getVisibility() == VISIBLE;
        if (isUpdate && mTransition.isRunning())
            return false;
        updateFullVisibility();

        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
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


        updatePlaneImage(flight);

        LongSparseArray<Flight.FlightData> history =  flight.getFlightHistory();
        Flight.FlightData lastData = history.valueAt(history.size() - 1);
        int fpm = (int)Math.round(lastData.verticalSpeed);
        String sign = fpm > 0 ? "+":"";
        mRightTexts.get(FlightIds.CallSign).setText(flight.getCallSign());
        mRightTexts.get(FlightIds.Plane).setText(flight.getAircraftName());
        mRightTexts.get(FlightIds.Speed).setText(Math.round(lastData.speed) + " kts");
        mRightTexts.get(FlightIds.Altitude).setText(Math.round(lastData.altitude) + " feet (" + sign + fpm + " fpm)");
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
        mRightTexts.get(FlightIds.FlightDuration).setText("Duration: " + (history.valueAt(0).getAgeMs() / 60000) + "m");
        mRightTexts.get(FlightIds.TotalDistance).setText("Distance: " + (int)flight.getFlightAbsoluteDistance() + "NM");
        mRightTexts.get(FlightIds.EndToEndDistance).setText("End to end: " + (int)flight.getEndToEndDistance() + "NM");
        mRightTexts.get(FlightIds.LastReport).setText(lastUpdate);

        int bgDrawable = R.drawable.shadowed_ui_background;
        Users.User user = flight.getUser();
        if (user.isSet()) {
            if (user.getRank() == 1) {
                bgDrawable = R.drawable.shadowed_ui_background_gold;
            }
//             else if (user.getRole() == Users.User.Role.ADMIN) {
//                bgDrawable = R.drawable.shadowed_ui_background_admin;
//            } else if (user.getRole() == Users.User.Role.TESTER) {
//                bgDrawable = R.drawable.shadowed_ui_background_tester;
//            }
            //mLeftTexts.get(UserIds.Roles).setText("Role: " + user.getRole());
            mLeftTexts.get(UserIds.Standing).setText("Standing: " + Math.round(user.getStanding() * 100)+ "%");
            mLeftTexts.get(UserIds.XP).setText("XP: " + user.getSkills());
            mLeftTexts.get(UserIds.FlightTime).setText((int)Math.floor(user.getFlightTime() / 60) + " flight hours");
            mLeftTexts.get(UserIds.LandingCount).setText("Landings: " + user.getLandingCount());
            mLeftTexts.get(UserIds.Violations).setText("Violations: " + user.getViolations());
            mLeftTexts.get(UserIds.OnlineFlights).setText("Online Flights : " + user.getOnlineFlights());
        } else {
            mLeftTexts.get(UserIds.Roles).setText("Loading...");
            mLeftTexts.get(UserIds.Standing).setText("Loading...");
            mLeftTexts.get(UserIds.XP).setText("Loading...");
            mLeftTexts.get(UserIds.FlightTime).setText("Loading...");
            mLeftTexts.get(UserIds.LandingCount).setText("Loading...");
            mLeftTexts.get(UserIds.Violations).setText("Loading...");
            mLeftTexts.get(UserIds.OnlineFlights).setText("Loading...");
        }
        mLeftTexts.get(UserIds.Name).setText(flight.getDisplayName());
//        mInnerInfoPane.setBackgroundDrawable(getResources().getDrawable(bgDrawable));
        mImageLayout.setBackgroundDrawable(getResources().getDrawable(bgDrawable));
//        mMyLoc.setBackgroundDrawable(getResources().getDrawable(bgDrawable));
        return true;
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

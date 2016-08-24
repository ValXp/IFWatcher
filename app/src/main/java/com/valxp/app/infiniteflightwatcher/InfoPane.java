package com.valxp.app.infiniteflightwatcher;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v4.util.LongSparseArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.valxp.app.infiniteflightwatcher.caching.DrawableMemoryCache;
import com.valxp.app.infiniteflightwatcher.model.Flight;
import com.valxp.app.infiniteflightwatcher.model.Users;

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
    private ProgressBar mDownloadProgress;
    private ImageView mMyLoc;
    private View mShare;
    private String mURL = "";

    private Map<FlightIds, TextView> mRightTexts;
    private HashMap<UserIds, TextView> mLeftTexts;

    private boolean mIsFullyDisplayed = false;
    private boolean mFollow;
    private LayoutTransition mTransition;
    private DrawableMemoryCache mPlaneImageCache;
    private PlaneDownloaderTask mPlaneImageDownloader;


    public enum FlightIds{
        CallSign,
        Plane,
        Livery,
        Speed,
        Altitude,
        FlightDuration,
        TotalDistance,
        LastReport
    }

    public enum UserIds {
        Name,
        Grade,
        Standing,
        XP,
        FlightTime,
        LandingCount,
        Violations,
        OnlineFlights
    }

    public InfoPane(Context context) {
        super(context);
        mContext = context;
        mPlaneImageCache = new DrawableMemoryCache(mContext, "plane_images", APIConstants.APICalls.LIVERY_PREVIEWS);
    }

    public InfoPane(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mPlaneImageCache = new DrawableMemoryCache(mContext, "plane_images", APIConstants.APICalls.LIVERY_PREVIEWS);
    }

    public InfoPane(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mPlaneImageCache = new DrawableMemoryCache(mContext, "plane_images", APIConstants.APICalls.LIVERY_PREVIEWS);
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
        mDownloadProgress = (ProgressBar) findViewById(R.id.downloadProgress);
        mTapToSeeMore = (TextView) findViewById(R.id.tap_to_see_more);

        mImageLayout = findViewById(R.id.image_layout);
        mInnerInfoPane = findViewById(R.id.inner_info_pane);

        mMyLoc = (ImageView) findViewById(R.id.my_loc);
        mShare = findViewById(R.id.share);

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
        mShare.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent share = new Intent(android.content.Intent.ACTION_SEND);
                share.setType("text/plain");
//                share.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

                share.putExtra(Intent.EXTRA_SUBJECT, "Follow my flight!");
                share.putExtra(Intent.EXTRA_TEXT, mURL);

                mContext.startActivity(Intent.createChooser(share, "Share Flight"));
            }
        });

        mRightTexts = new HashMap<>();
        mLeftTexts = new HashMap<>();


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

    private class PlaneDownloaderTask extends AsyncTask<String, Void, Drawable> {
        protected Drawable doInBackground(String... strings) {
            String flightId = strings[0];
            return mPlaneImageCache.getDrawable(flightId, true);
        }

        protected void onPostExecute(Drawable drawable) {
            if (drawable == null) {
                mPlaneImage.setImageResource(R.drawable.default_livery);
            } else {
                mPlaneImage.setImageDrawable(drawable);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Utils.dpToPx(180), Utils.dpToPx(108));
            mPlaneImage.setLayoutParams(params);
            mPlaneImage.setVisibility(View.VISIBLE);
            mDownloadProgress.setVisibility(View.GONE);
        }
    }

    private String lastId;
    private void updatePlaneImage(Flight flight) {
        String liveryId = flight.getLivery().getId();
        if (liveryId.equals(lastId)) {
            return;
        }
        lastId = liveryId;
        if (mPlaneImageDownloader != null)
            mPlaneImageDownloader.cancel(true);
        mPlaneImage.setVisibility(View.GONE);
        mDownloadProgress.setVisibility(View.VISIBLE);
        Drawable drawable = mPlaneImageCache.getDrawable(liveryId);
        if (drawable != null) {
            mPlaneImage.setImageDrawable(drawable);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Utils.dpToPx(180), Utils.dpToPx(108));
            mPlaneImage.setLayoutParams(params);
            mPlaneImage.setVisibility(View.VISIBLE);
            mDownloadProgress.setVisibility(View.GONE);
        } else {
            mPlaneImageDownloader = new PlaneDownloaderTask();
            mPlaneImageDownloader.execute(liveryId);
        }
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
        assert d != null;
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
//            trans.setInterpolator(new DecelerateInterpolator());
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
        mRightTexts.get(FlightIds.Plane).setText(flight.getLivery().getPlaneName());
        mRightTexts.get(FlightIds.Livery).setText(flight.getLivery().getName());
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
        mRightTexts.get(FlightIds.LastReport).setText(lastUpdate);

        int bgDrawable = R.drawable.shadowed_ui_background;
        Users.User user = flight.getUser();
        String postfix = "";
        if (user.isSet()) {
            if (user.getPilotStats().getGrade() == 3) {
                bgDrawable = R.drawable.shadowed_ui_background_grade3;
            }
            if (user.getPilotStats().getGrade() == 4) {
                bgDrawable = R.drawable.shadowed_ui_background_grade4;
            }
            if (user.isMod()) {
                postfix = " (Mod)";
                bgDrawable = R.drawable.shadowed_ui_background_mod;
            }
            if (user.isAdmin()) {
                postfix = " (Dev)";
                bgDrawable = R.drawable.shadowed_ui_background_dev;
            }

            mLeftTexts.get(UserIds.Grade).setText(user.getPilotStats().getGradeName());
            mLeftTexts.get(UserIds.Standing).setText("Standing: " + Math.round(user.getStanding() * 100)+ "%");
            mLeftTexts.get(UserIds.XP).setText("XP: " + user.getPilotStats().getTotalXP());
            mLeftTexts.get(UserIds.FlightTime).setText((int)Math.floor(user.getFlightTime() / 60) + " flight hours");
            mLeftTexts.get(UserIds.LandingCount).setText("Landings: " + user.getLandingCount());
            mLeftTexts.get(UserIds.Violations).setText("Violations: " + user.getViolations());
            mLeftTexts.get(UserIds.OnlineFlights).setText("Online Flights : " + user.getOnlineFlights());
        } else {
            mLeftTexts.get(UserIds.Grade).setText("Loading...");
            mLeftTexts.get(UserIds.Standing).setText("Loading...");
            mLeftTexts.get(UserIds.XP).setText("Loading...");
            mLeftTexts.get(UserIds.FlightTime).setText("Loading...");
            mLeftTexts.get(UserIds.LandingCount).setText("Loading...");
            mLeftTexts.get(UserIds.Violations).setText("Loading...");
            mLeftTexts.get(UserIds.OnlineFlights).setText("Loading...");
        }

        mLeftTexts.get(UserIds.Name).setText(flight.getDisplayName() + postfix);
//        mInnerInfoPane.setBackgroundDrawable(getResources().getDrawable(bgDrawable));
        mImageLayout.setBackgroundDrawable(getResources().getDrawable(bgDrawable));
//        mMyLoc.setBackgroundDrawable(getResources().getDrawable(bgDrawable));
        mURL = "https://www.liveflightapp.com/?f=" + flight.getFlightID() + "&s=" + flight.getServer().getId();
        return true;
    }

    public void hide() {
        if (getVisibility() != View.GONE) {
            setVisibility(View.GONE);
            TranslateAnimation trans = new TranslateAnimation(0, -400, 0, 0);
//            trans.setInterpolator(new AccelerateInterpolator());
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

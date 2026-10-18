package com.revolution.arrowrush;

import android.app.Activity;
import android.util.Log;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/** Opt-in AdMob rewarded hint. TEST AD UNIT ONLY. Reward granted ONLY by SDK callback. */
public final class RewardedHints {
    private static final String TEST_UNIT="ca-app-pub-3940256099942544/5224354917";
    private final Activity activity;
    private RewardedAd available;
    private boolean loading=false;
    public RewardedHints(Activity activity) {
        this.activity=activity;
        new Thread(() -> MobileAds.initialize(activity, status -> activity.runOnUiThread(this::load))).start();
    }
    public void load() {
        if(available!=null || loading) return;
        loading=true;
        RewardedAd.load(activity,TEST_UNIT,new AdRequest.Builder().build(),new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(RewardedAd ad) { available=ad;loading=false; }
            @Override public void onAdFailedToLoad(LoadAdError error) {
                available=null;loading=false;
                Log.d("ArrowRush", "Test rewarded ad unavailable: "+error.getMessage());
            }
        });
    }
    public boolean ready() { return available!=null; }
    public void show(Runnable onEarned,Runnable onUnavailable) {
        if(available==null) { load(); onUnavailable.run(); return; }
        RewardedAd showing=available;
        available=null;
        showing.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { load(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError adError) {
                load(); onUnavailable.run();
            }
        });
        showing.show(activity,reward -> onEarned.run());
    }
}

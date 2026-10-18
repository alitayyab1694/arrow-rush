package com.revolution.arrowrush;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

public final class MainActivity extends Activity {
    private ArrowRushView game;
    private RewardedHints hints;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        game=new ArrowRushView(this);
        setContentView(game);
        hints=new RewardedHints(this);
        game.setRewardedHints(hints);
    }
    public void shareText(String message) {
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT,message);
        startActivity(Intent.createChooser(i,"Share challenge"));
    }
    @Override public void onBackPressed() {
        if(game!=null&&game.goBack())return;
        super.onBackPressed();
    }
}

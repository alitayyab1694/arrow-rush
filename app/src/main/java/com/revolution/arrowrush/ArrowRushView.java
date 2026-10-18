package com.revolution.arrowrush;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/** Low-poly, asset-free, portrait Canvas renderer. Full game runs offline except opt-in rewarded ads. */
public final class ArrowRushView extends View {
    private enum Screen { HOME, BOARD, WIN, LEVELS, SHOP }
    private static final float W=390f,H=844f;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path poly=new Path();
    private final Random fxRandom=new Random(71);
    private final SharedPreferences prefs;
    private final MainActivity activity;
    private RewardedHints ads;
    private Screen screen=Screen.HOME;
    private PuzzleEngine.Level level;
    private final ArrayList<PuzzleEngine.Piece> remaining=new ArrayList<>();
    private final ArrayList<Spark> sparks=new ArrayList<>();
    private int highest, coins, freeHints, hintsUsed, mistakes, pulseCombos, earnedCoins, earnedStars;
    private int theme, hintId=-1, total=0, levelPage=0;
    private long levelStarted, noticeEnd, hintUntil, badUntil, winStamp;
    private String notice="";
    private boolean isDaily=false, sound=true;
    private float scale=1f,originX=0f,originY=0f;
    private long previousFrame=0;
    private int accent=Color.rgb(82,241,203);
    private static final int TEXT=Color.rgb(244,249,255), SUB=Color.rgb(145,168,194);
    private static final int BG=Color.rgb(7,16,30), SURFACE=Color.rgb(20,34,54);
    private static final int[] COLORS={0xFF69DFD1,0xFF9B88F5,0xFFFFB866,0xFFFF7796};

    private static final class Spark {
        float x,y,vx,vy,t,max;
        int color;
        Spark(float x,float y,float vx,float vy,float max,int color) {
            this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.max=max;this.t=max;this.color=color;
        }
    }
    public ArrowRushView(MainActivity context) {
        super(context);
        activity=context;
        prefs=context.getSharedPreferences("arrow_rush_progress_v1",Context.MODE_PRIVATE);
        highest=Math.max(1,prefs.getInt("highest",1));
        coins=prefs.getInt("coins",0);
        freeHints=prefs.getInt("free_hints",2);
        theme=prefs.getInt("theme",0);
        sound=prefs.getBoolean("sound",true);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setFocusable(true);
    }
    public void setRewardedHints(RewardedHints ads) {this.ads=ads;}
    private void save() {prefs.edit().putInt("highest",highest).putInt("coins",coins)
        .putInt("free_hints",freeHints).putInt("theme",theme).putBoolean("sound",sound).apply();}
    private void toast(String message) {notice=message;noticeEnd=SystemClock.uptimeMillis()+2100;invalidate();}
    private boolean unlocked(int which) {return which==0||prefs.getBoolean("skin_"+which,false);}
    private void start(int number,boolean daily) {
        level=PuzzleEngine.generate(number,daily);
        remaining.clear();remaining.addAll(level.pieces);
        total=remaining.size();isDaily=daily;
        hintsUsed=0;mistakes=0;pulseCombos=0;hintId=-1;
        levelStarted=SystemClock.uptimeMillis();winStamp=0;
        sparks.clear();screen=Screen.BOARD;invalidate();
    }
    private int today() {return (int)LocalDate.now(ZoneOffset.UTC).toEpochDay();}
    private String tierText() {return level.tier.name();}
    private void exitPiece(PuzzleEngine.Piece piece) {
        if(!PuzzleEngine.canExit(piece,remaining)) {
            mistakes++;badUntil=SystemClock.uptimeMillis()+350;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            toast("Blocked! Clear its route first.");return;
        }
        remaining.remove(piece);
        hintId=-1;
        burst(piece,1f);
        int removed=1;
        if(piece.pulse) {
            List<PuzzleEngine.Piece> chain=PuzzleEngine.pulseChain(piece,remaining);
            for(PuzzleEngine.Piece other:chain) burst(other,1.4f);
            removed+=chain.size();
            if(chain.size()>0) {pulseCombos++;toast("PULSE CHAIN  x"+(chain.size()+1)+"!");}
        }
        if(remaining.isEmpty()) {
            // Avoid switching to win UI before the last particles travel.
            if(winStamp==0) {
                winStamp=SystemClock.uptimeMillis();
                postDelayed(()->finish(),520);
            }
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        invalidate();
    }
    private void burst(PuzzleEngine.Piece piece,float amount) {
        float cell=314f/level.cols;
        float cx=(W-314f)/2+piece.col*cell+cell/2;
        float cy=274+piece.row*cell+cell/2;
        int color=COLORS[piece.color];
        for(int i=0;i<(int)(13*amount);i++) {
            double a=(i*2*Math.PI/(13*amount))+fxRandom.nextDouble()*.4;
            float s=1.5f+fxRandom.nextFloat()*3.5f;
            sparks.add(new Spark(cx,cy,(float)Math.cos(a)*s,(float)Math.sin(a)*s,
                0.32f+fxRandom.nextFloat()*.48f,color));
        }
    }
    private void finish() {
        if(screen!=Screen.BOARD||level==null||!remaining.isEmpty())return;
        earnedStars=mistakes==0&&hintsUsed==0?3:(mistakes<=2?2:1);
        int base=15+level.tier.ordinal()*8+earnedStars*4;
        if(isDaily) {
            if(prefs.getInt("daily_reward_day",-1)==today()) earnedCoins=0;
            else {earnedCoins=base+15;prefs.edit().putInt("daily_reward_day",today()).apply();}
        } else {
            boolean previouslyCompleted=prefs.getBoolean("won_"+level.number,false);
            earnedCoins=previouslyCompleted?0:base;
            if(!previouslyCompleted)prefs.edit().putBoolean("won_"+level.number,true).apply();
            highest=Math.max(highest,level.number+1);
            int old=prefs.getInt("star_"+level.number,0);
            if(earnedStars>old)prefs.edit().putInt("star_"+level.number,earnedStars).apply();
        }
        coins+=earnedCoins;save();screen=Screen.WIN;invalidate();
    }
    private void showHint() {
        if(remaining.isEmpty())return;
        if(freeHints>0) {
            freeHints--;save();revealHint();return;
        }
        if(ads==null){toast("Ad service initializing. Try again.");return;}
        ads.show(this::revealHint,()->toast("No ad available. Try again later."));
    }
    private void revealHint() {
        if(screen!=Screen.BOARD)return;
        PuzzleEngine.Piece safe=PuzzleEngine.hint(remaining);
        if(safe==null){toast("No valid move found. Restart this level.");return;}
        hintsUsed++;
        hintId=safe.id;hintUntil=SystemClock.uptimeMillis()+6500;
        toast("Follow the glowing arrow!");invalidate();
    }
    public boolean goBack() {
        if(screen==Screen.HOME)return false;
        if(screen==Screen.WIN||screen==Screen.BOARD||screen==Screen.SHOP||screen==Screen.LEVELS)
            screen=Screen.HOME;
        invalidate();return true;
    }
    @Override protected void onDraw(Canvas original) {
        super.onDraw(original);
        original.drawColor(BG);
        scale=Math.min(getWidth()/W,getHeight()/H);
        originX=(getWidth()-W*scale)/2f;
        originY=(getHeight()-H*scale)/2f;
        original.save();original.translate(originX,originY);original.scale(scale,scale);
        background(original);
        switch(screen) {
            case HOME: drawHome(original);break;
            case BOARD: drawBoard(original);break;
            case WIN: drawWin(original);break;
            case LEVELS: drawLevels(original);break;
            case SHOP: drawShop(original);break;
        }
        drawNotice(original);
        drawSparks(original);
        original.restore();
    }
    private void fill(int color) {p.reset();p.setAntiAlias(true);p.setColor(color);p.setStyle(Paint.Style.FILL);}
    private void rr(Canvas c,float x,float y,float w,float h,float r,int color) {
        fill(color);c.drawRoundRect(x,y,x+w,y+h,r,r,p);
    }
    private void stroke(Canvas c,float x,float y,float w,float h,float r,int color,float width) {
        fill(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);
        c.drawRoundRect(x,y,x+w,y+h,r,r,p);p.setStyle(Paint.Style.FILL);
    }
    private void grad(Canvas c,float x,float y,float w,float h,float r,int c1,int c2) {
        fill(c1);p.setShader(new LinearGradient(x,y,x+w,y+h,c1,c2,Shader.TileMode.CLAMP));
        c.drawRoundRect(x,y,x+w,y+h,r,r,p);p.setShader(null);
    }
    private void label(Canvas c,String text,float x,float y,float size,int color,boolean bold) {
        fill(color);p.setTypeface(Typeface.create(bold?"sans-serif-black":"sans-serif-medium",Typeface.NORMAL));
        p.setTextSize(size);p.setTextAlign(Paint.Align.LEFT);c.drawText(text,x,y,p);
    }
    private void centered(Canvas c,String text,float cx,float baseline,float size,int color,boolean bold) {
        fill(color);p.setTypeface(Typeface.create(bold?"sans-serif-black":"sans-serif-medium",Typeface.NORMAL));
        p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);c.drawText(text,cx,baseline,p);
        p.setTextAlign(Paint.Align.LEFT);
    }
    private void polygon(Canvas c,int color,float... xy) {
        fill(color);poly.reset();poly.moveTo(xy[0],xy[1]);
        for(int i=2;i<xy.length;i+=2)poly.lineTo(xy[i],xy[i+1]);
        poly.close();c.drawPath(poly,p);
    }
    private void background(Canvas c) {
        fill(BG);p.setShader(new LinearGradient(0,0,390,844,0xFF081426,0xFF06101E,Shader.TileMode.CLAMP));
        c.drawRect(0,0,390,844,p);p.setShader(null);
        // Non-texture low-poly background, constant geometry and tiny overdraw.
        polygon(c,0x122EE5BE,300,0,390,0,390,244,349,142);
        polygon(c,0x1332C4B4,0,560,0,843,166,844,85,688);
        polygon(c,0x127D68E5,40,170,152,109,174,166,109,234);
        polygon(c,0x15808AEF,266,742,390,643,390,844,318,844);
        fill(0x1440D4C0);c.drawCircle(365,180,75,p);
    }
    private void button(Canvas c,String text,float x,float y,float w,float h,boolean solid) {
        rr(c,x,y+5,w,h,17,0x66000000);
        if(solid)grad(c,x,y,w,h,17,theme==1?0xFFFAB081:0xFF6CF0D0,
                theme==2?0xFF63C6FF:0xFF67BFFF);
        else {rr(c,x,y,w,h,17,0xFF172A40);stroke(c,x,y,w,h,17,0xFF36516C,1);}
        centered(c,text,x+w/2,y+h/2+5,14,solid?0xFF092435:TEXT,true);
    }
    private void cornerBack(Canvas c) {
        rr(c,22,43,43,43,13,0xFF1A2B43);
        centered(c,"‹",43.5f,73,32,TEXT,false);
    }
    private void tinyCoin(Canvas c,float x,float y) {
        fill(0xFFFFB954);c.drawCircle(x,y,10,p);
        fill(0xFF9C5D22);c.drawCircle(x,y,6,p);
        label(c,""+coins,x+16,y+5,14,TEXT,true);
    }
    private void logo(Canvas c,float cx,float y,float size) {
        c.save();c.translate(cx,y);c.rotate(45f);
        grad(c,-size/2,-size/2,size,size,12,0xFF1F5F72,0xFF3E3A86);
        stroke(c,-size/2,-size/2,size,size,12,0xFF81FFE5,2);
        c.restore();
        c.save();c.translate(cx,y);c.scale(size/74,size/74);
        polygon(c,0xFF74F4D6,-25,-9,8,-9,8,-20,30,0,8,20,8,9,-25,9);
        c.restore();
    }
    private void drawHome(Canvas c) {
        tinyCoin(c,320,56);
        label(c,"REVOLUTION ARCADE",25,60,12,0xFF80ABCA,true);
        logo(c,195,167,98);
        centered(c,"ARROW",195,298,55,TEXT,true);
        centered(c,"RUSH",195,355,68,0xFF69E8D1,true);
        centered(c,"PULSE ESCAPE",195,383,16,0xFFC2CBF6,true);
        centered(c,"ONE TAP. BIG CHAIN REACTIONS.",195,417,12,SUB,false);
        // Preview illustration illustrates combo arrow, all drawn in vectors.
        rr(c,35,450,320,68,18,0xFF15293C);
        for(int k=0;k<4;k++) {
            float xx=76+k*76;
            colorArrow(c,xx,482,22,k%4,k==2,false);
        }
        centered(c,"◈  PULSE CHAINS  ◈",195,536,11,0xFFA5D9E1,true);
        button(c,"▶   PLAY LEVEL  "+highest,31,563,328,59,true);
        button(c,"◆   TODAY'S DAILY RESCUE",31,638,328,53,false);
        button(c,"LEVELS",31,708,152,53,false);
        button(c,"CITY SHOP",207,708,152,53,false);
        centered(c,"LIGHTWEIGHT  •  OFFLINE PUZZLES  •  NO FORCED ADS",195,802,10,SUB,false);
    }
    private void drawBoard(Canvas c) {
        cornerBack(c);tinyCoin(c,320,58);
        label(c,isDaily?"DAILY RESCUE":"LEVEL "+level.number,78,61,20,TEXT,true);
        label(c,"ESCAPE ALL ARROWS",79,83,10,SUB,false);
        int tierColor=level.tier==PuzzleEngine.Tier.EASY?0xFF64EDCD:
            level.tier==PuzzleEngine.Tier.MEDIUM?0xFF8BBCF9:
            level.tier==PuzzleEngine.Tier.HARD?0xFFFFBE73:0xFFFF849D;
        rr(c,23,115,115,29,9,0xFF1B2E44);
        centered(c,tierText(),80,135,11,tierColor,true);
        int rescued=total-remaining.size();
        label(c,"RESCUED  "+rescued+" / "+total,224,135,12,TEXT,true);
        rr(c,23,155,343,9,4,0xFF22384D);
        if(rescued>0)grad(c,23,155,343f*rescued/total,9,4,0xFF51EFD3,0xFF7AA5FA);
        rr(c,23,184,343,54,14,0xFF152B3D);
        label(c,"◆  PULSE CHAIN",39,207,13,0xFF7EFFE3,true);
        label(c,"Tap glowing arrows to trigger bonus escapes",39,226,10,SUB,false);
        centered(c,""+pulseCombos,340,216,26,TEXT,true);
        // Main play tray.
        rr(c,18,257,354,351,25,0xFF112438);
        stroke(c,18,257,354,351,25,0xFF314C64,1f);
        polygon(c,0x102EA1A9,23,278,103,260,45,415);
        polygon(c,0x123B7CC2,365,442,365,580,270,601);
        float size=314,cell=size/level.cols;
        float baseX=(W-size)/2,baseY=274;
        long time=SystemClock.uptimeMillis();
        for(PuzzleEngine.Piece piece:remaining) {
            float x=baseX+piece.col*cell+3;
            float y=baseY+piece.row*cell+3;
            float side=cell-6;
            boolean highlighted=piece.id==hintId&&time<hintUntil;
            drawTile(c,x,y,side,piece,highlighted);
        }
        label(c,"CHOOSE A CLEAR EXIT  •  NO RANDOM TRAPS",27,631,11,0xFF96B4CD,true);
        rr(c,23,653,344,57,15,0xFF172A40);
        label(c,"HINT",38,678,16,TEXT,true);
        label(c,freeHints>0?""+freeHints+" FREE LEFT":"WATCH A REWARDED AD",38,697,10,0xFF94B5CE,false);
        rr(c,252,664,101,35,12,0xFF295266);
        centered(c,freeHints>0?"FREE HINT":"WATCH AD",302,687,11,0xFF82FFDA,true);
        button(c,"RESTART",23,731,164,53,false);
        button(c,"LEVELS",203,731,164,53,false);
        centered(c,"Every board is verified solvable",195,821,11,SUB,false);
    }
    private void drawTile(Canvas c,float x,float y,float side,PuzzleEngine.Piece piece,boolean highlighted) {
        int color=COLORS[piece.color];
        if(highlighted) {
            rr(c,x-3,y-3,side+6,side+6,14,0xAAE0FF86);
            stroke(c,x-3,y-3,side+6,side+6,14,0xFFFFFFFF,2);
        }
        rr(c,x,y+3,side,side,Math.min(12,side*.27f),0xB0000817);
        grad(c,x,y,side,side-1,Math.min(11,side*.28f),darken(color,.42f),darken(color,.69f));
        polygon(c,0x256FFFFF,x+4,y+3,x+side-7,y+3,x+side-17,y+side*.43f,x+5,y+side*.63f);
        polygon(c,0x26000000,x+side-7,y+3,x+side-3,y+side-6,x+side*.52f,y+side-5);
        stroke(c,x+1,y+1,side-2,side-2,Math.min(11,side*.28f),0x445DFFFF,1);
        if(piece.pulse) {
            fill(0xFFF9D677);c.drawCircle(x+side-8,y+8,Math.max(3,side*.10f),p);
        }
        colorArrow(c,x+side*.5f,y+side*.52f,side*.64f,piece.direction.ordinal(),piece.pulse,highlighted);
    }
    private String repeatChar(char glyph,int count) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<count;i++)out.append(glyph);
        return out.toString();
    }
    private int darken(int color,float ratio) {
        int r=(int)(Color.red(color)*ratio),g=(int)(Color.green(color)*ratio),b=(int)(Color.blue(color)*ratio);
        return Color.rgb(r,g,b);
    }
    private void colorArrow(Canvas c,float cx,float cy,float span,int dir,boolean pulse,boolean glowing) {
        c.save();c.translate(cx,cy);c.rotate(dir*90f-90f); // ordinal UP RIGHT DOWN LEFT -> degrees -90 0 90 180
        float scale=span/35f;c.scale(scale,scale);
        int col=pulse?0xFFFFE39C:0xFFE9FDFF;
        polygon(c,0x44000000,-13,-5,4,-5,4,-13,17,0,4,13,4,5,-13,5);
        polygon(c,col,-15,-5,2,-5,2,-12,16,0,2,12,2,5,-15,5);
        if(glowing){fill(0xFFFDFEAB);c.drawCircle(-17,0,2,p);}
        c.restore();
    }
    private void drawWin(Canvas c) {
        cornerBack(c);
        centered(c,"MISSION COMPLETE",195,138,21,0xFF76FCE0,true);
        logo(c,195,253,110);
        centered(c,"CITY RESCUED!",195,356,35,TEXT,true);
        centered(c,isDaily?"Daily rescue solved":"Level "+level.number+" conquered",195,391,15,SUB,false);
        rr(c,43,427,304,97,19,0xFF1B2F43);
        centered(c,repeatChar('★',earnedStars)+repeatChar('☆',3-earnedStars),195,484,35,0xFFFFCB75,true);
        label(c,"PULSE CHAINS",61,564,12,SUB,true);
        label(c,""+pulseCombos,314,564,16,TEXT,true);
        label(c,"COINS EARNED",61,607,12,SUB,true);
        label(c,"+"+earnedCoins,293,607,17,0xFFFFC873,true);
        button(c,isDaily?"PLAY LEVEL "+highest:"NEXT LEVEL",30,661,330,61,true);
        button(c,"SHARE YOUR RESCUE",30,738,330,54,false);
    }
    private void drawLevels(Canvas c) {
        cornerBack(c);tinyCoin(c,321,56);
        label(c,"MISSION MAP",26,133,32,TEXT,true);
        label(c,"Each board has a verified escape route.",26,158,12,SUB,false);
        int start=levelPage*12+1;
        for(int i=0;i<12;i++) {
            int number=start+i;int row=i/3,col=i%3;
            float x=26+col*115,y=194+row*114;
            boolean locked=number>highest;
            rr(c,x,y,107,103,17,locked?0xFF142339:0xFF1E384E);
            stroke(c,x,y,107,103,17,locked?0xFF26354C:0xFF3D7184,1);
            centered(c,locked?"●":""+number,x+54,y+44,25,locked?0xFF60778C:TEXT,true);
            int stars=prefs.getInt("star_"+number,0);
            if(locked) centered(c,"LOCKED",x+54,y+72,10,SUB,true);
            else if(stars>0)centered(c,repeatChar('★',stars),x+54,y+75,16,0xFFFFCA65,true);
            else centered(c,PuzzleEngine.tierFor(number).name(),x+54,y+73,9,0xFF86F8D7,true);
        }
        button(c,"‹ PREVIOUS",26,680,158,57,false);
        button(c,"NEXT ›",206,680,158,57,false);
        centered(c,"PAGE "+(levelPage+1)+"   •   LEVEL "+start+"–"+(start+11),195,772,11,SUB,false);
    }
    private void drawShop(Canvas c) {
        cornerBack(c);tinyCoin(c,320,58);
        label(c,"CITY SHOP",27,135,34,TEXT,true);
        label(c,"Customize your rescue fleet with coins.",27,159,12,SUB,false);
        for(int i=0;i<3;i++) {
            float y=199+i*165;
            boolean owned=unlocked(i);
            rr(c,23,y,344,146,20,0xFF172B40);
            grad(c,36,y+17,95,111,18,
                i==0?0xFF245B68:i==1?0xFF74516A:0xFF375F67,
                i==0?0xFF233B66:i==1?0xFF4B385D:0xFF273F66);
            c.save();c.translate(84,y+70);c.rotate(-25);c.translate(-84,-y-70);
            colorArrow(c,84,y+70,53,i,true,false);
            c.restore();
            label(c,i==0?"NEON CLASSIC":i==1?"CORAL SUNSET":"ICE CIRCUIT",147,y+49,16,TEXT,true);
            label(c,i==0?"The original pulse":i==1?"Warm futuristic look":"Electric blue glow",147,y+72,11,SUB,false);
            int cost=i==1?110:i==2?240:0;
            rr(c,147,y+92,196,38,12,owned?0xFF27554D:0xFF2B425B);
            centered(c,owned?(theme==i?"ACTIVE  ✓":"EQUIP"):"UNLOCK  ◈ "+cost,245,y+116,12,owned?0xFF81FFDA:TEXT,true);
        }
        centered(c,"Coins come from missions. No payment needed.",195,749,12,SUB,false);
        centered(c,"Real-money purchases are not enabled in this build.",195,771,10,SUB,false);
    }
    private void drawNotice(Canvas c) {
        if(SystemClock.uptimeMillis()>=noticeEnd)return;
        rr(c,40,88,310,41,14,0xFF2B4459);
        centered(c,notice,195,113,12,TEXT,true);
    }
    private void drawSparks(Canvas c) {
        if(sparks.isEmpty())return;
        long now=SystemClock.uptimeMillis();
        float dt=previousFrame==0?0.016f:Math.min(.05f,(now-previousFrame)/1000f);
        previousFrame=now;
        for(int i=sparks.size()-1;i>=0;i--) {
            Spark s=sparks.get(i);s.x+=s.vx*dt*56;s.y+=s.vy*dt*56;
            s.vy+=dt*2;s.t-=dt;
            if(s.t<=0){sparks.remove(i);continue;}
            fill(s.color);
            p.setAlpha((int)(255*s.t/s.max));
            c.drawCircle(s.x,s.y,2.2f+3*(s.t/s.max),p);
        }
        postInvalidateDelayed(16);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction()!=MotionEvent.ACTION_UP)return true;
        float x=(event.getX()-originX)/scale,y=(event.getY()-originY)/scale;
        if(x<0||x>W||y<0||y>H)return true;
        if(screen!=Screen.HOME&&x>18&&x<71&&y>37&&y<91) {goBack();return true;}
        switch(screen) {
            case HOME:
                if(y>=560&&y<626)start(highest,false);
                else if(y>=635&&y<697)start(today(),true);
                else if(y>=701&&y<772) {
                    if(x<194){levelPage=(highest-1)/12;screen=Screen.LEVELS;}
                    else screen=Screen.SHOP;
                }
                break;
            case BOARD:
                if(y>=274&&y<588&&x>=(W-314)/2&&x<(W+314)/2) {
                    float cell=314f/level.cols;
                    int col=(int)((x-(W-314)/2)/cell),row=(int)((y-274)/cell);
                    for(PuzzleEngine.Piece piece:new ArrayList<>(remaining))
                        if(piece.row==row&&piece.col==col){exitPiece(piece);break;}
                } else if(y>=650&&y<=715){showHint();}
                else if(y>=721&&y<=795) {
                    if(x<194)start(level.number,isDaily);
                    else{levelPage=Math.max(0,(highest-1)/12);screen=Screen.LEVELS;}
                }
                break;
            case WIN:
                if(y>=650&&y<728)start(isDaily?highest:level.number+1,false);
                else if(y>=733&&y<797)activity.shareText("I rescued Arrow Rush "+(isDaily?"daily mission":"level "+level.number)+" with "+earnedStars+" stars and "+pulseCombos+" Pulse Chains! Can you beat that? #ArrowRush");
                break;
            case LEVELS:
                if(y>=680&&y<=746) {
                    if(x<194)levelPage=Math.max(0,levelPage-1);
                    else if((levelPage+1)*12<Math.max(highest,12))levelPage++;
                } else if(y>=194&&y<194+4*114) {
                    int col=(int)((x-26)/115),row=(int)((y-194)/114);
                    if(x>=26&&col>=0&&col<3&&row>=0&&row<4) {
                        int number=levelPage*12+row*3+col+1;
                        if(number<=highest)start(number,false);
                        else toast("Complete previous missions to unlock.");
                    }
                }
                break;
            case SHOP:
                for(int i=0;i<3;i++) {
                    float yy=199+i*165;
                    if(y>=yy&&y<yy+145) {
                        int cost=i==1?110:i==2?240:0;
                        if(!unlocked(i)) {
                            if(coins<cost){toast("Earn more coins by completing missions.");break;}
                            coins-=cost;prefs.edit().putBoolean("skin_"+i,true).apply();
                        }
                        theme=i;save();toast("Theme equipped!");break;
                    }
                }
                break;
        }
        invalidate();return true;
    }
}

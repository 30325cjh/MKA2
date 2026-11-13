package com.mka2.assistant;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class OverlayService extends Service {
    public static final String ACTION_CHARACTER_REACTION = "com.mka2.assistant.CHARACTER_REACTION";
    public static final String EXTRA_EXPRESSION = "expression";
    public static final String EXTRA_SOUND = "sound";

    private static final String CHANNEL_ID = "mka2_assistant";
    private static final long DOUBLE_TAP_MS = 450L;
    private static final String ASSET_ROOT = "aris/";

    private WindowManager windowManager;
    private FrameLayout bubble;
    private ImageView characterView;
    private ImageView haloView;
    private TextView fallback;
    private long lastTap;
    private float downX, downY;
    private int downWindowX, downWindowY;
    private WindowManager.LayoutParams params;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ValueAnimator haloAnimator;
    private MediaPlayer mediaPlayer;
    private BroadcastReceiver reactionReceiver;

    private final Map<String, String> expressionFiles = new HashMap<String, String>() {{
        put("default", "expressions/default.png");
        put("smile", "expressions/smile.png");
        put("question", "expressions/question.png");
        put("happy", "expressions/happy.png");
        put("confident", "expressions/confident.png");
    }};

    private final Map<String, String> soundFiles = new HashMap<String, String>() {{
        put("question", "sounds/question.mp3");
        put("question2", "sounds/question2.mp3");
        put("question3", "sounds/question3.mp3");
        put("surprise", "sounds/surprise.mp3");
        put("smile", "sounds/smile.mp3");
        put("confident", "sounds/confident.mp3");
        put("confused", "sounds/confused.mp3");
        put("confirm", "sounds/confirm.mp3");
        put("glitch", "sounds/glitch.mp3");
    }};

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("MKA2 비서 실행 중")
                .setContentText("아리스와 대화하려면 캐릭터를 두 번 누르세요.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
        startForeground(1, notification);
        registerReactionReceiver();
        showCharacter();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "MKA2 비서", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void registerReactionReceiver() {
        reactionReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String expression = intent.getStringExtra(EXTRA_EXPRESSION);
                String sound = intent.getStringExtra(EXTRA_SOUND);
                if (expression != null) changeExpression(expression);
                if (sound != null) playSound(sound);
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_CHARACTER_REACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(reactionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(reactionReceiver, filter);
    }

    private void showCharacter() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        bubble = new FrameLayout(this);

        haloView = new ImageView(this);
        haloView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams haloParams = new FrameLayout.LayoutParams(dp(62), dp(62), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        haloParams.topMargin = dp(1);
        bubble.addView(haloView, haloParams);
        loadAssetImage(haloView, ASSET_ROOT + "halo.webp");

        characterView = new ImageView(this);
        characterView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams characterParams = new FrameLayout.LayoutParams(dp(184), dp(207), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bubble.addView(characterView, characterParams);
        fallback = new TextView(this);
        fallback.setText("아리스");
        fallback.setTextSize(17);
        fallback.setTextColor(0xFFFFFFFF);
        fallback.setGravity(Gravity.CENTER);
        fallback.setBackgroundColor(0xDD6750A4);
        FrameLayout.LayoutParams fallbackParams = new FrameLayout.LayoutParams(dp(90), dp(42), Gravity.CENTER);
        bubble.addView(fallback, fallbackParams);
        changeExpression("default");
        startHaloFloat();

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(dp(190), dp(230), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(8);
        params.y = dp(150);
        windowManager.addView(bubble, params);

        bubble.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    downWindowX = params.x;
                    downWindowY = params.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float moveX = event.getRawX() - downX;
                    float moveY = event.getRawY() - downY;
                    if (Math.abs(moveX) > dp(5) || Math.abs(moveY) > dp(5)) {
                        params.x = downWindowX + (int) moveX;
                        params.y = downWindowY + (int) moveY;
                        windowManager.updateViewLayout(bubble, params);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (Math.abs(dx) <= dp(5) && Math.abs(dy) <= dp(5)) {
                        long now = System.currentTimeMillis();
                        if (now - lastTap < DOUBLE_TAP_MS) {
                            lastTap = 0;
                            Intent chat = new Intent(this, ChatActivity.class);
                            chat.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(chat);
                        } else {
                            lastTap = now;
                            showGreeting();
                        }
                    }
                    return true;
            }
            return true;
        });
    }

    private void showGreeting() {
        changeExpression("smile");
        playSound("confirm");
        handler.postDelayed(() -> changeExpression("default"), 1700);
    }

    private void changeExpression(String expression) {
        if (characterView == null) return;
        String file = expressionFiles.get(expression);
        if (file == null) file = expressionFiles.get("default");
        ImageView next = new ImageView(this);
        next.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams nextParams = new FrameLayout.LayoutParams(dp(184), dp(207),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        next.setTranslationY(dp(22));
        next.setAlpha(0f);
        if (!loadAssetImage(next, ASSET_ROOT + file)) {
            if (fallback != null) fallback.setVisibility(View.VISIBLE);
            return;
        }
        if (fallback != null) fallback.setVisibility(View.GONE);
        FrameLayout parent = bubble;
        if (parent == null) return;
        ImageView old = characterView;
        parent.addView(next, nextParams);
        next.animate().translationY(0f).alpha(1f).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
        if (old != null && old.getParent() == parent) {
            old.animate().translationY(dp(-16)).alpha(0f).setDuration(220)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator animation) {
                            if (old.getParent() == parent) parent.removeView(old);
                        }
                    }).start();
        }
        characterView = next;
    }

    private boolean loadAssetImage(ImageView target, String path) {
        try {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(getAssets().open(path));
            if (bitmap == null) return false;
            target.setImageBitmap(bitmap);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void startHaloFloat() {
        if (haloView == null) return;
        haloAnimator = ValueAnimator.ofFloat(0f, -dp(4), 0f, dp(2), 0f);
        haloAnimator.setDuration(2400);
        haloAnimator.setRepeatCount(ValueAnimator.INFINITE);
        haloAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        haloAnimator.addUpdateListener(a -> {
            if (haloView != null) haloView.setTranslationY((Float) a.getAnimatedValue());
        });
        haloAnimator.start();
    }

    private void playSound(String sound) {
        String file = soundFiles.get(sound);
        if (file == null) return;
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
            AssetFileDescriptor afd = getAssets().openFd(ASSET_ROOT + file);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                if (mediaPlayer == mp) mediaPlayer = null;
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                mp.release();
                if (mediaPlayer == mp) mediaPlayer = null;
                return true;
            });
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception ignored) {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        if (haloAnimator != null) haloAnimator.cancel();
        handler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (reactionReceiver != null) {
            try { unregisterReceiver(reactionReceiver); } catch (Exception ignored) { }
        }
        if (windowManager != null && bubble != null) {
            try { windowManager.removeView(bubble); } catch (Exception ignored) { }
        }
        bubble = null;
        characterView = null;
        haloView = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

package com.mka2.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class OverlayService extends Service {
    private static final String CHANNEL_ID = "mka2_assistant";
    private WindowManager windowManager;
    private View bubble;
    private long lastTap;
    private float downX, downY;
    private WindowManager.LayoutParams params;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("MKA2 비서 실행 중")
                .setContentText("화면 위 캐릭터를 눌러 대화하세요.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
        startForeground(1, notification);
        showBubble();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "MKA2 비서", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void showBubble() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        TextView avatar = new TextView(this);
        avatar.setText("✦");
        avatar.setTextSize(28);
        avatar.setTextColor(0xFFFFFFFF);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackgroundColor(0xDD6750A4);
        avatar.setElevation(12f);
        bubble = avatar;

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(60, 60, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 12;
        params.y = 220;
        windowManager.addView(bubble, params);

        bubble.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        params.x += (int) dx;
                        params.y += (int) dy;
                        windowManager.updateViewLayout(bubble, params);
                    } else {
                        long now = System.currentTimeMillis();
                        if (now - lastTap < 450) {
                            Intent chat = new Intent(this, ChatActivity.class);
                            chat.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(chat);
                            lastTap = 0;
                        } else {
                            lastTap = now;
                            avatar.setText("안녕!");
                            avatar.postDelayed(() -> {
                                if (bubble != null) avatar.setText("✦");
                            }, 1400);
                        }
                    }
                    return true;
            }
            return true;
        });
    }

    @Override
    public void onDestroy() {
        if (windowManager != null && bubble != null) {
            try { windowManager.removeView(bubble); } catch (Exception ignored) { }
        }
        bubble = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

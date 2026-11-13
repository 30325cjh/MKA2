package com.mka2.assistant;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int OVERLAY_REQUEST = 201;
    private static final String CHANNEL_ID = "mka2_assistant";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        createNotificationChannel();
        showSetup();
    }

    private void showSetup() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 48, 36, 36);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("MKA2 AI 비서");
        title.setTextSize(28);
        root.addView(title);

        TextView description = new TextView(this);
        description.setText("다른 앱 위에 표시 권한을 허용하면 화면에 비서 아이콘이 나타납니다.");
        description.setTextSize(16);
        description.setPadding(0, 24, 0, 24);
        root.addView(description);

        Button permission = new Button(this);
        permission.setText("화면 위 표시 권한 허용");
        permission.setOnClickListener(v -> requestOverlayPermission());
        root.addView(permission);

        Button start = new Button(this);
        start.setText("비서 실행");
        start.setOnClickListener(v -> startAssistant());
        root.addView(start);

        setContentView(root);
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQUEST);
        } else {
            startAssistant();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQUEST && Settings.canDrawOverlays(this)) {
            startAssistant();
        }
    }

    private void startAssistant() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }
        Intent service = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
        finish();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "MKA2 비서", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("화면 위 비서 기능을 유지합니다.");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}

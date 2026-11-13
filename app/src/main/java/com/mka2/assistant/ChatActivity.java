package com.mka2.assistant;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public class ChatActivity extends Activity {
    private LinearLayout messages;
    private EditText input;
    private Button sendButton;
    private ScrollView scroll;
    private final JSONArray history = new JSONArray();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 16);

        TextView title = new TextView(this);
        title.setText("MKA2 비서 · Gemini");
        title.setTextSize(22);
        root.addView(title);

        scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0, 20, 0, 20);
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addMessage("안녕하세요! 무엇을 도와드릴까요?", false);

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("메시지를 입력하세요");
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        sendButton = new Button(this);
        sendButton.setText("전송");
        sendButton.setOnClickListener(v -> sendMessage());
        composer.addView(sendButton);
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
        root.addView(composer);
        setContentView(root);
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        addMessage(text, true);

        String lower = text.toLowerCase(Locale.ROOT);
        String promptText = text;
        if (lower.startsWith("검색 ") || lower.startsWith("검색해 ")) {
            String query = text.replaceFirst("^검색(해)?\\s*", "");
            if (query.trim().isEmpty()) {
                addMessage("무엇을 검색할까요?", false);
                return;
            }
            promptText = "웹에서 검색하고 최신 결과를 출처와 함께 요약해줘: " + query;
        }

        if (lower.startsWith("앱 실행 ")) {
            launchInstalledApp(text.substring("앱 실행 ".length()).trim());
            return;
        }

        try {
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("text", promptText);
            history.put(userMessage);
        } catch (Exception e) {
            addMessage("메시지를 준비하지 못했어요. 다시 시도해 주세요.", false);
            return;
        }

        sendButton.setEnabled(false);
        input.setEnabled(false);
        addMessage("생각 중...", false);

        AssistantApi.send(history, new AssistantApi.Callback() {
            @Override
            public void onSuccess(String answer) {
                mainHandler.post(() -> {
                    removeLastMessage();
                    addMessage(answer, false);
                    try {
                        JSONObject modelMessage = new JSONObject();
                        modelMessage.put("role", "model");
                        modelMessage.put("text", answer);
                        history.put(modelMessage);
                    } catch (Exception ignored) { }
                    trimHistory();
                    setComposerEnabled(true);
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    removeLastMessage();
                    addMessage(message, false);
                    setComposerEnabled(true);
                });
            }
        });
    }

    private void trimHistory() {
        while (history.length() > 24) history.remove(0);
    }

    private void setComposerEnabled(boolean enabled) {
        sendButton.setEnabled(enabled);
        input.setEnabled(enabled);
        if (enabled) input.requestFocus();
    }

    private void removeLastMessage() {
        int count = messages.getChildCount();
        if (count > 0) messages.removeViewAt(count - 1);
    }

    private void launchInstalledApp(String requestedName) {
        if (requestedName.isEmpty()) {
            addMessage("실행할 앱 이름을 알려주세요.", false);
            return;
        }

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String wanted = requestedName.toLowerCase(Locale.ROOT);
        for (ApplicationInfo app : apps) {
            String label = pm.getApplicationLabel(app).toString();
            if (label.toLowerCase(Locale.ROOT).equals(wanted)) {
                Intent launch = pm.getLaunchIntentForPackage(app.packageName);
                if (launch != null) {
                    addMessage(label + " 앱을 실행할게요.", false);
                    startActivity(launch);
                    return;
                }
            }
        }
        for (ApplicationInfo app : apps) {
            String label = pm.getApplicationLabel(app).toString();
            if (label.toLowerCase(Locale.ROOT).contains(wanted)) {
                Intent launch = pm.getLaunchIntentForPackage(app.packageName);
                if (launch != null) {
                    addMessage(label + " 앱을 실행할게요.", false);
                    startActivity(launch);
                    return;
                }
            }
        }
        addMessage("'" + requestedName + "' 앱을 찾지 못했어요. 설치된 앱 이름으로 다시 말해주세요.", false);
    }

    private void addMessage(String text, boolean user) {
        TextView line = new TextView(this);
        line.setText((user ? "나: " : "비서: ") + text);
        line.setTextSize(16);
        line.setPadding(12, 10, 12, 10);
        messages.addView(line);
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }
}

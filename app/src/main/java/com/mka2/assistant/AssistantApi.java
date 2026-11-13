package com.mka2.assistant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AssistantApi {
    // Replace this after deploying the backend in server/.
    // Keep the Gemini API key on the backend; never place it in the APK.
    private static final String BASE_URL = "https://YOUR-MKA2-BACKEND.example.com";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onSuccess(String answer);
        void onError(String message);
    }

    private AssistantApi() { }

    public static void send(JSONArray history, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BASE_URL + "/api/chat");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(60000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                JSONObject payload = new JSONObject();
                payload.put("messages", history);
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder responseText = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) responseText.append(line);
                }

                JSONObject response = new JSONObject(responseText.toString());
                if (status < 200 || status >= 300) {
                    String error = response.optString("error", "서버 요청에 실패했어요. (" + status + ")");
                    callback.onError(error);
                } else {
                    String answer = response.optString("reply", "").trim();
                    if (answer.isEmpty()) callback.onError("AI 응답이 비어 있어요. 다시 시도해 주세요.");
                    else callback.onSuccess(answer);
                }
            } catch (Exception e) {
                callback.onError("AI 서버에 연결할 수 없어요. 서버 주소와 네트워크를 확인해 주세요.");
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}

# MKA2 Gemini backend

Small Node.js 20+ HTTPS service that keeps the Gemini API key off the Android device.

## Configure

- Set `GEMINI_API_KEY` as a secret environment variable in your hosting provider.
- Optional: set `GEMINI_MODEL` (defaults to `gemini-3.8-flash`).
- Optional: set `PORT` (defaults to `8787`).

Do not commit a real `.env` file or API key.

## Run locally

```sh
npm start
```

Set environment variables in your shell before starting the service. The service exposes:
- `GET /health`
- `POST /api/chat` with JSON `{"messages":[{"role":"user","text":"안녕"}]}`

The API accepts recent user/model messages and returns `{"reply":"..."}`. It has basic request-size, history-length, and per-IP rate limits. Deploy behind HTTPS and review hosting access controls and usage limits before sharing the endpoint.

## Connect Android

After deployment, copy the HTTPS origin (without a trailing slash) into `BASE_URL` in `AssistantApi.java`. Rebuild the Android app.

The API key belongs only in the backend secret store. Do not add it to Android source, Gradle config, APK resources, or GitHub commits.

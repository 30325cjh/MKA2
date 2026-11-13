import http from "node:http";

const PORT = Number(process.env.PORT || 8787);
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
const GEMINI_MODEL = process.env.GEMINI_MODEL || "gemini-3.8-flash";
const MAX_BODY_BYTES = 48 * 1024;
const MAX_MESSAGES = 24;
const MAX_MESSAGE_CHARS = 8000;

const rateBuckets = new Map();
const SYSTEM_INSTRUCTION = [
  "너는 MKA2라는 안드로이드 플로팅 AI 비서의 대화 엔진이다.",
  "항상 자연스럽고 친절한 한국어로 답한다. 사용자가 다른 언어를 쓰면 그 언어를 따른다.",
  "휴대폰 앱 실행이나 기기 제어는 앱이 별도로 처리하는 명령이며, 네가 직접 실행했다고 거짓말하지 않는다.",
  "사용자가 검색이나 최신 정보 확인을 요청하면 Google Search 도구를 사용하고, 답변 끝에 확인 가능한 출처를 표시한다.",
  "모르는 사실은 지어내지 않는다."
].join("\n");

function sendJson(res, status, body) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff"
  });
  res.end(JSON.stringify(body));
}

function isRateLimited(ip) {
  const now = Date.now();
  const bucket = rateBuckets.get(ip) || { start: now, count: 0 };
  if (now - bucket.start > 60_000) {
    bucket.start = now;
    bucket.count = 0;
  }
  bucket.count += 1;
  rateBuckets.set(ip, bucket);
  return bucket.count > 20;
}

async function readJson(req) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new Error("요청이 너무 커요.");
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

async function handleChat(req, res) {
  if (!GEMINI_API_KEY) {
    return sendJson(res, 503, { error: "서버에 Gemini API 키가 설정되지 않았어요." });
  }
  if (isRateLimited(req.socket.remoteAddress || "unknown")) {
    return sendJson(res, 429, { error: "요청이 너무 많아요. 잠시 후 다시 시도해 주세요." });
  }

  let body;
  try {
    body = await readJson(req);
  } catch {
    return sendJson(res, 400, { error: "요청 형식이 올바르지 않거나 너무 커요." });
  }

  if (!Array.isArray(body.messages) || body.messages.length === 0 ||
      body.messages.length > MAX_MESSAGES) {
    return sendJson(res, 400, { error: "대화 기록 형식이 올바르지 않아요." });
  }

  const contents = [];
  for (const message of body.messages) {
    if (!message || !["user", "model"].includes(message.role) ||
        typeof message.text !== "string" ||
        message.text.length === 0 || message.text.length > MAX_MESSAGE_CHARS) {
      return sendJson(res, 400, { error: "메시지 내용이 올바르지 않아요." });
    }
    contents.push({
      role: message.role,
      parts: [{ text: message.text }]
    });
  }

  try {
    const upstream = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(GEMINI_MODEL)}:generateContent`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": GEMINI_API_KEY
        },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: SYSTEM_INSTRUCTION }] },
          contents,
          tools: [{ google_search: {} }],
          generationConfig: { temperature: 0.8, maxOutputTokens: 1200 }
        }),
        signal: AbortSignal.timeout(55_000)
      }
    );

    const data = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      const status = upstream.status === 429 ? 429 : 502;
      return sendJson(res, status, {
        error: upstream.status === 429
          ? "Gemini 사용량 한도에 도달했어요. 잠시 후 다시 시도해 주세요."
          : "Gemini 서버에서 응답하지 않았어요. 서버 설정을 확인해 주세요."
      });
    }

    let reply = (data.candidates?.[0]?.content?.parts || [])
      .map(part => typeof part.text === "string" ? part.text : "")
      .join("")
      .trim();

    if (!reply) return sendJson(res, 502, { error: "Gemini가 텍스트 응답을 반환하지 않았어요." });

    const chunks = data.candidates?.[0]?.groundingMetadata?.groundingChunks || [];
    const sources = [];
    const seen = new Set();
    for (const chunk of chunks) {
      const web = chunk.web;
      if (web?.uri && !seen.has(web.uri) && sources.length < 4) {
        seen.add(web.uri);
        sources.push(`- ${web.title || "웹 출처"}: ${web.uri}`);
      }
    }
    if (sources.length) reply += "\n\n참고한 웹 출처\n" + sources.join("\n");
    return sendJson(res, 200, { reply });
  } catch {
    return sendJson(res, 502, { error: "Gemini 서버에 연결하지 못했어요." });
  }
}

const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    return sendJson(res, 200, { ok: true, service: "MKA2 Gemini backend" });
  }
  if (req.method === "POST" && req.url === "/api/chat") {
    return handleChat(req, res);
  }
  return sendJson(res, 404, { error: "Not found" });
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`MKA2 Gemini backend listening on port ${PORT}`);
});

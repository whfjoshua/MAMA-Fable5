/**
 * MAMA demo — AI gateway + CORS relay (Cloudflare Worker).
 *
 * TWO JOBS:
 *  1. /gemini  — "MAMA Cloud" gateway: testers tap Sign in with Google in the demo;
 *     this endpoint verifies their Google login, then calls Gemini with YOUR key
 *     (kept server-side). Testers never paste keys or touch Google Cloud.
 *  2. /minimax, /deepseek, /openai — plain CORS relays for providers that block
 *     browser calls (key travels through from the user, nothing stored).
 *
 * SETUP (~5 minutes):
 *  1. https://workers.cloudflare.com → Create Worker → paste this file → Deploy
 *  2. Worker → Settings → Variables and Secrets, add:
 *       GEMINI_API_KEY   (secret)  = your Gemini key from aistudio.google.com
 *       ALLOWED_EMAILS   (text)    = your-email@example.com,partner@example.com
 *                                    // Replace with your comma-separated allowlist
 *       GOOGLE_CLIENT_ID (text)    = optional but recommended: your OAuth Web
 *                                    Client ID, to reject tokens minted for other apps
 *       GEMINI_MODEL     (text)    = optional default model, e.g. gemini-3.5-flash
 *  3. In Google Cloud Console → Credentials → create ONE OAuth Client (Web),
 *     add your Netlify demo URL to "Authorized JavaScript origins".
 *  4. In the demo: Family → AI provider → "MAMA Cloud (Google login)",
 *     set Gateway URL to this worker's URL and the same Client ID, ship it.
 *     Testers just tap "Sign in with Google".
 */

const TARGETS = {
  "/minimax":    "https://api.minimax.io/v1/chat/completions",
  "/minimax-cn": "https://api.minimaxi.com/v1/chat/completions",
  "/deepseek":   "https://api.deepseek.com/chat/completions",
  "/openai":     "https://api.openai.com/v1/chat/completions"
};

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
  "Access-Control-Max-Age": "86400"
};

function json(status, obj) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }
    if (request.method !== "POST") {
      return new Response("POST only", { status: 405, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);

    // ---------- 1. MAMA Cloud gateway: Google login -> Gemini with server key ----------
    if (url.pathname === "/gemini") {
      if (!env.GEMINI_API_KEY) {
        return json(500, { error: "Gateway not configured: set the GEMINI_API_KEY secret" });
      }

      // Verify the Google ID token from "Sign in with Google"
      const auth = request.headers.get("Authorization") || "";
      const idToken = auth.startsWith("Bearer ") ? auth.slice(7) : "";
      if (!idToken) return json(401, { error: "Missing Google login token" });

      let info;
      try {
        const v = await fetch(
          "https://oauth2.googleapis.com/tokeninfo?id_token=" + encodeURIComponent(idToken)
        );
        if (!v.ok) return json(401, { error: "Google login token invalid or expired — sign in again" });
        info = await v.json();
      } catch (e) {
        return json(502, { error: "Could not verify Google login" });
      }

      if (env.GOOGLE_CLIENT_ID && info.aud !== env.GOOGLE_CLIENT_ID) {
        return json(401, { error: "Token was issued for a different app" });
      }
      if (env.ALLOWED_EMAILS) {
        const allowed = env.ALLOWED_EMAILS.split(",").map(s => s.trim().toLowerCase()).filter(Boolean);
        if (!allowed.includes((info.email || "").toLowerCase())) {
          return json(403, { error: `${info.email} is not on the family allowlist — contact the project owner to add you` });
        }
      }

      const model = url.searchParams.get("model") || env.GEMINI_MODEL || "gemini-3.5-flash";
      const upstream = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${env.GEMINI_API_KEY}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: request.body
        }
      );
      const body = await upstream.text();
      return new Response(body, {
        status: upstream.status,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" }
      });
    }

    // ---------- 2. Plain CORS relays ----------
    const target = TARGETS[url.pathname];
    if (!target) {
      return json(404, { error: `Unknown path '${url.pathname}'. Use /gemini or one of: ${Object.keys(TARGETS).join(", ")}` });
    }
    const upstream = await fetch(target, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": request.headers.get("Authorization") || ""
      },
      body: request.body
    });
    const body = await upstream.text();
    return new Response(body, {
      status: upstream.status,
      headers: {
        ...CORS_HEADERS,
        "Content-Type": upstream.headers.get("Content-Type") || "application/json"
      }
    });
  }
};

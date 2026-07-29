# MAMA Scheduler — Build Handoff

AI-powered family scheduler. Two artifacts live in this repo:

1. **`web_demo.html`** — single-file interactive demo (the thing the team is testing on Netlify). This is where all product iteration has happened. ~2,700 lines, zero dependencies, all state in `localStorage`.
2. **Android app** (`app/`) — full Kotlin rewrite (Material You + clean architecture), compiles independently. Feature-behind the demo; needs catch-up (see Backlog).

Supporting files: `cors_proxy_worker.js` (Cloudflare Worker: AI gateway + CORS relay), `deploy/index.html` (exact copy of web_demo.html for Netlify Drop), `README.md` (Android build instructions), `APP_SUMMARY.md` in `../mama_app_aistudio` (legacy app reference).

---

## Team & process

- **Joshua** (owner/dev), **Ocean** & **Agnes** (testers) — WhatsApp group "AI for $$", chat exports land in `~/Downloads/WhatsApp Chat - AI for $$ - WhatsApp version/_chat.txt`. Workflow: testers screenshot issues → fix in `web_demo.html` → `cp web_demo.html deploy/index.html` → drag `deploy/` onto netlify.com/drop.
- Live demo URL: hosted at app.josunday.ai/mama_app/ (and/or Netlify).
- **Testers' localStorage persists old data** — after parser/seed changes, tell them to tap "Reset demo data" (Family tab) or old "Activity"-titled events linger.
- Language: UI is bilingual EN/繁體中文 (Cantonese tone). Auto-detects `navigator.language`, toggle in Family tab.

## Web demo — architecture

Single `<script>` with no framework. Render cycle: global state object → `render()` → per-tab `render*()` functions return HTML strings → injected into `#screen`. All interactivity via inline `onclick` handlers calling global functions.

Key sections (in file order):
- `MAMA_GATEWAY` const (top of script) — **fill in worker URL + OAuth client ID to enable "MAMA Cloud" login** (currently empty)
- i18n: `I18N` dict, `t(key)`, `lang()`, `displayDateL()`, `DOWL()`
- State: `defaultState()` (kids incl. roles kid/parent/helper, events, pending, tasks, memories, trash, settings) + migration lines after `load()`
- `save()` → debounced `saveNow()` with quota-overflow eviction of oldest photos
- Google OAuth (advanced Gemini mode): `googleSignIn/Out` (token client, cloud-platform scope)
- MAMA Cloud: `mamaSignedIn/initMamaGsi/mamaSignOut` (GIS ID-token button), `callLLM` mama branch → `{gateway}/gemini?model=…`
- LLM providers: `AI_PROVIDERS` (sim / mama / gemini / deepseek / openai / minimax / custom), `callLLM`, `callLLMRetry` (1 retry), `extractJson` (strips ``` fences + `<think>` tags), `friendlyAIError` (CORS/China-endpoint hints)
- Prompts: `chatSystemPrompt()` (JSON action schema: move/cancel/add/note), `llmParseNL` (events schema with `kidNames[]`, location, keep-input-language)
- Local fallback: `parseNL` (activity keywords EN+zh, en-dash time ranges, "in 30 mins" relative, multi-kid participants, "at <place>" location, title fallback from cleaned words), `mockAgent` (weather °C, today, conflicts, move/cancel/add, note intent, `/help /summary /query /querythisweek`)
- Domain: `checkConflicts` (overlap / travel buffer / per-kid daily limit), `travelMins/travelKm` (simulated, deterministic), `weatherFor(date)` (simulated, deterministic)
- Memories: `addEventPhotos` (file picker → 560px JPEG → `state.memories`, cap 18), `openMemories` Ken Burns slideshow, ambient background mode
- UI: `renderAgenda` (hero "next up", stat tiles, inline pending confirm cards, tasks card, memories card, weather day-pills, multi-select member filter, travel lines, NL quick-add), `renderCalendar` (month/week/day), `renderApprovals`, `renderChat` (AI summary on "Ask AI", floating ✦ FAB), `renderFamily` (members+roles+photos, language, ambient, AI provider, sync mock, recently-deleted with edit&restore, privacy, tutorial)
- Dialogs: `openAddEvent` (multi-participant chips, native date+time inputs, duration incl. non-standard chip, buffer, location, notes, reminder, **live conflict preview**), `openAddKid` (add/edit, role, ∞ limit), `openAddTask`, `openGoSheet` (Google/Apple Maps, Uber deep link, DiDi scheme, copy), `openConfirm`, tour (3 steps, swipeable)

### Testing harness (no browser needed)
Extract script and run under Node with DOM stubs:
```bash
python3 -c "import re;open('/tmp/demo.js','w').write(re.search(r'<script>(.*)</script>', open('web_demo.html').read(), re.S).group(1))"
node --check /tmp/demo.js
```
Then concat a stub (`localStorage`, fake `document.getElementById` returning permissive objects, no-op timers) + the demo + assertions, and run. See conversation history pattern; assertions cover parser, conflicts, i18n, gateway calls (mock `fetch`), quota eviction, slideshow, filters. **Always run `node --check` + a regression file after edits, then `cp web_demo.html deploy/index.html`.**

### Known caveats (by design, demo-only)
- All data per-browser; no sync between testers' phones
- Weather + travel times are deterministic simulations
- Helper role is cosmetic (no real read-only login)
- DiDi deep link only fires if app installed; iOS photo EXIF relies on modern browser auto-orientation

## MAMA Cloud gateway (AI stability plan)

Goal: testers tap **Sign in with Google** — no API keys, no GCP setup.
- `cors_proxy_worker.js` → deploy on Cloudflare Workers. Env vars: `GEMINI_API_KEY` (secret), `ALLOWED_EMAILS` (comma list), `GOOGLE_CLIENT_ID` (optional aud check), `GEMINI_MODEL` (optional).
- `/gemini?model=…` verifies the Google ID token via `oauth2.googleapis.com/tokeninfo`, checks allowlist, forwards the Gemini-format body with the server-held key.
- Other paths `/minimax /minimax-cn /deepseek /openai` are plain CORS relays (user's own key passes through).
- One OAuth **Web** client in Google Cloud Console with the demo URL in Authorized JavaScript origins.
- **TODO: fill `MAMA_GATEWAY` in web_demo.html and redeploy** — until then the provider shows "not configured".

## Feedback backlog

### Done in demo (v1)
Undo + confirm on deletes · recently-deleted bin with edit&restore · minute-exact + typed time + native date picker · edit everything (events & members, ∞ limits) · proactive conflict warning in dialog · inline approval bubbles on home · multi-select participants + `[Name]` titles + multi-select member filter · locations with tap-to-Maps/Uber/DiDi · travel-time lines · weather (°C) on header/day-pills/cards + umbrella hint · notes + AI note parsing · reminders per event · tasks with due dates · share week via native share sheet · Chinese everywhere incl. AI provider section · profile photos · roles (parent/helper/kid) · swipeable tutorial · `/help /summary /query` commands · Memories (photo highlights, Ken Burns reel, ambient background) · AI auto-retry · en-dash & relative-time & short-prompt parsing.

### v2 (agreed with team)
Monthly achievements per kid (progress: "Leo: 4 swim lessons, learned breaststroke") · expense tracking by category · Telegram AI CS with human escalation · AI "more interactive/games".

### Real Android app work (the big items)
1. **Multi-parent/helper sync** — shared backend (Firebase or Google-Calendar-as-source-of-truth); helper = read-only, no AI, per-viewer travel/distances
2. Port demo features into Compose app (participants, roles, location, notes, undo, proactive conflicts, i18n, tasks, memories UI)
3. Real Maps travel times + current location; real weather API (Celsius)
4. `PhotoFinder.kt` already scans MediaStore by event timeslot + Gemini curation — wire it to the Memories reel UI
5. Reminders survive reboot (BOOT_COMPLETED re-schedule), exact-alarm permission flow (Android 14+)
6. Gemini structured output (`responseSchema`), Credential Manager migration, unit tests for `ConflictDetector`/`RecurrenceExpander`

## Android app quick facts
Package `com.mama.scheduler` · Kotlin 2.2 / AGP 9.1.1 / Gradle 9.3.1 / Java 21 · Compose BOM 2024.09 + Material You dynamic color · Hilt 2.59.2 (AGP9-compatible) · Room 2.7 (`mama_scheduler_db` v1, no migration from legacy app) · DataStore · WorkManager · Gemini via Retrofit/Moshi (`gemini-3.5-flash`) · Google Sign-In (legacy API) + Calendar API sync · `.env` holds `GEMINI_API_KEY` via secrets-gradle-plugin.
Build: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=~/Android/sdk && ./gradlew assembleDebug` — **never compiled yet; expect first-build fixes.**
Layers: `data/{local,remote,prefs,repository}` → `domain/` (NaturalLanguageParser, SchedulerAgent, ConflictDetector, RecurrenceExpander, PhotoFinder, WeatherService) → `ui/screens/{agenda,calendar,approvals,chat,profiles}` each with Hilt ViewModel; `MamaApp.kt` bottom-nav host; `sync/`, `notifications/`, `auth/GoogleAuthManager`.

# MAMA Scheduler

AI-powered family scheduling. Two artifacts live in this repo:

| Artifact | Status | What it is |
|---|---|---|
| **`web_demo.html`** | **Shipped** — testers running on Netlify | Single-file bilingual (EN / 繁體中文) family scheduler. ~2,700 lines, zero dependencies, all state in `localStorage`. This is the product. |
| **`app/`** | Feature-behind the demo, **compiles clean** | Kotlin / Compose / Hilt / Room Android rewrite (`com.mama.scheduler`). |

Plus:
- **`cors_proxy_worker.js`** — Cloudflare Worker: AI gateway (`/gemini`) + CORS relays for providers that block browser calls (`/minimax`, `/minimax-cn`, `/deepseek`, `/openai`).
- **`deploy/index.html`** — exact copy of `web_demo.html` ready for Netlify Drop.

> Read `HANDOFF.md` for full project state, architecture, feedback backlog, and team workflow.
> Read `CLAUDE.md` for the rules to follow when editing either artifact.

---

## Web demo (the shipped product)

Single `<script>` with no framework. Render cycle: global state → `render()` → per-tab `render*()` returns HTML → injected into `#screen`. All interactivity via inline `onclick` calling global functions.

**Features**
- Natural-language event parsing (Gemini + local fallback with en-dash / relative time / multi-kid / location)
- AI chat assistant (`/help /summary /query /querythisweek`) with friendly CORS / China-endpoint error hints
- Month / Week / Day calendar views
- Conflict detection: overlaps, travel buffers, per-kid daily limits
- Multi-select participants, `[Name]` titles, member filter
- Locations → tap-to-Maps / Uber / DiDi; travel-time lines
- Weather (°C) on header, day-pills, cards; umbrella hint
- Tasks with due dates; notes + AI note parsing; per-event reminders
- Memories: photo highlights, Ken Burns reel, ambient background mode (18-photo cap, quota-aware eviction)
- Recently-deleted bin with edit & restore; undo + confirm on deletes
- Roles: parent / helper / kid; ∞ limits per member
- Bilingual EN / 繁體中文 with auto-detect + manual toggle
- Swipeable tutorial

**Edit workflow** (mandatory after any change):
```bash
# 1. Lint the embedded JS
python3 -c "import re;open('/tmp/demo.js','w').write(re.search(r'<script>(.*)</script>', open('web_demo.html').read(), re.S).group(1))"
node --check /tmp/demo.js

# 2. Regression-test in Node with DOM stubs (no browser available):
#    stub localStorage + document.getElementById + timers, append demo.js, assert.
#    Cover parseNL, checkConflicts, i18n, and any touched feature.

# 3. Sync the deploy copy
cp web_demo.html deploy/index.html
```

User deploys by dragging `deploy/` onto netlify.com/drop.

**Rules of the road**
- Every user-facing string → `t("key")` with an `I18N` entry `[english, 繁體中文]`. Never hardcode either language.
- State changes need a migration line after `load()` — testers' `localStorage` persists old data; seed-only changes won't reach them.
- z-index layers: screen 1 < nav / fab / snackbar 5 < overlay 30 < memories 40. New dialogs render into `#dialog-root`.
- Photos / base64 are heavy — respect the 18-photo cap and quota-eviction in `saveNow()`.

---

## MAMA Cloud gateway (testers' key-free Google login)

Goal: testers tap **Sign in with Google** — no API keys, no GCP setup.

1. Deploy `cors_proxy_worker.js` to Cloudflare Workers.
2. Worker → Settings → Variables and Secrets:
   - `GEMINI_API_KEY` (secret) — your Gemini key from aistudio.google.com
   - `ALLOWED_EMAILS` (text) — comma-separated allowlist (e.g. `whfjoshua3@gmail.com,ocean@…,agnes@…`)
   - `GOOGLE_CLIENT_ID` (text, optional but recommended) — OAuth Web Client ID for `aud` check
   - `GEMINI_MODEL` (text, optional) — default model, e.g. `gemini-3.5-flash`
3. Google Cloud Console → Credentials → create **one** OAuth Web client. Add the Netlify demo URL to *Authorized JavaScript origins*.
4. In `web_demo.html`: fill the `MAMA_GATEWAY` const with the worker URL + OAuth Client ID, then `cp web_demo.html deploy/index.html` and redeploy.

Until `MAMA_GATEWAY` is filled, the "MAMA Cloud" provider shows *not configured*.

---

## Android app

Material You redesign + clean architecture over the original MAMA project.

**Design**
- Dynamic color from wallpaper on Android 12+ (toggle in Family tab); lavender brand fallback; full dark-mode
- Material 3 components throughout (tonal cards, segmented buttons, filter chips, badges, expressive typography)
- Bottom navigation with filled / outlined icon states + live Approvals badge

**Architecture**
- Layers: `data` (Room, Retrofit / Gemini, DataStore) → `domain` (parser, agent, conflict detection, recurrence, photos, weather) → `ui`
- Hilt DI everywhere; 5 per-screen ViewModels; no god-ViewModel
- Navigation Compose with proper per-tab state save / restore
- DataStore replaces SharedPreferences for settings

**Project structure**
```
com.mama.scheduler/
├── MainActivity.kt / MamaApplication.kt
├── di/                          # Hilt modules
├── core/DateUtils.kt
├── auth/GoogleAuthManager.kt
├── data/
│   ├── local/                   # Room entities + DAOs + database
│   ├── remote/GeminiApi.kt      # Retrofit + Moshi Gemini client
│   ├── prefs/SettingsRepository.kt   # DataStore settings
│   └── repository/              # EventRepository, ChatRepository
├── domain/                      # NaturalLanguageParser, SchedulerAgent,
│                                # ConflictDetector, RecurrenceExpander,
│                                # PhotoFinder, WeatherService
├── notifications/               # Alarms + morning summary worker
├── sync/                        # Google + system calendar sync
└── ui/
    ├── theme/                   # Material You theme
    ├── components/              # EventCard, dialogs, shared pieces
    ├── MamaApp.kt               # Nav host + bottom bar
    └── screens/                 # agenda, calendar, approvals, chat, profiles
```

**Features preserved from the original**
- Natural-language event parsing (Gemini + local fallback) → approval queue
- AI chat assistant with pending-action approve / reject and weather alerts
- Month / Week / Day calendar views
- Conflict detection: overlaps, travel buffers, per-kid daily limits
- Photo highlights (AI-screened from your library)
- Google Calendar + device calendar two-way sync
- Event reminders + daily morning summary notification

**Build**

Prerequisites: Java 21, Android SDK (API 36), `gradle-wrapper` (vendored).

```bash
# 1. Drop your Gemini key into .env (template: .env.example)
cp .env.example .env
# edit .env and set GEMINI_API_KEY=...

# 2. Build
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME=$HOME/Android/sdk
./gradlew assembleDebug

# 3. Install (emulator must be running, e.g. mama_test_x86 on M-series Mac)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Installs as a separate app (`com.mama.scheduler`), so any previous MAMA install stays untouched.

**Stack pins**
- Kotlin 2.2 · AGP 9.1.1 · Gradle 9.3.1 · Java 21
- Compose BOM 2024.09 · Hilt 2.59.2 (AGP 9-compatible) · Room 2.7
- DataStore · WorkManager · Navigation Compose
- Gemini via Retrofit / Moshi (`gemini-3.5-flash`)
- Google Sign-In (legacy API) + Calendar API sync

**Notes**
- `.env` is loaded by the `secrets-gradle-plugin` into `BuildConfig.GEMINI_API_KEY`; `.env` is gitignored — only `.env.example` is committed.
- OpenWeatherMap key placeholder lives in `domain/WeatherService.kt`; weather falls back to mock data without it.
- Database is fresh (`mama_scheduler_db` v1) — old app data is **not** migrated.
- Package `com.mama.mama` (legacy AI-studio build) and `com.mama.scheduler` (this rewrite) coexist on the same device.

---

## Team

- **Joshua** — owner / dev
- **Ocean** & **Agnes** — testers (WhatsApp group "AI for $$")
- Workflow: testers screenshot issues → fix in `web_demo.html` → `cp web_demo.html deploy/index.html` → drag `deploy/` onto netlify.com/drop
- Live demo URL is configured by Joshua at deploy time

## License

TBD — to be set when handed off.
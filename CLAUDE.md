# MAMA Scheduler

**Read `HANDOFF.md` first** — full project state, architecture, feedback backlog, and team workflow.

## The three artifacts

- `web_demo.html` — the live product being iterated with testers (single-file, no framework, bilingual EN/繁中). This is where feature work happens.
- `app/` — Android rewrite (Kotlin, Compose, Hilt, Room) packaged as `com.mama.scheduler` (versionName `2.0`, versionCode `1`). Compiles on the original developer's machine; feature-behind the demo. Coexists on-device with legacy `com.mama.mama` (separate package, no shared data).
- `cors_proxy_worker.js` — Cloudflare Worker serving as MAMA Cloud AI gateway (`/gemini`) and CORS relay for `/minimax`, `/minimax-cn`, `/deepseek`, `/openai`.

## Rules for the web demo

1. After ANY edit, verify + sync:
   ```bash
   python3 -c "import re;open('/tmp/demo.js','w').write(re.search(r'<script>(.*)</script>', open('web_demo.html').read(), re.S).group(1))"
   node --check /tmp/demo.js
   cp web_demo.html deploy/index.html
   ```
   User deploys by dragging `deploy/` onto netlify.com/drop.
2. Regression-test in Node with DOM stubs (pattern in HANDOFF.md §Testing harness) — cover `parseNL`, `checkConflicts`, i18n, and any touched feature. No browser available.
3. Every user-facing string goes through `t("key")` with an `I18N` entry `[english, 繁體中文]` — never hardcode either language.
4. State changes need a migration line after `load()` (testers have persisted localStorage; seed-only changes won't reach them).
5. Keep it a single file. Inline handlers call global functions; new dialogs render into `#dialog-root`; z-index layers: screen 1 < nav/fab/snackbar 5 < overlay 30 < memories 40.
6. Photos/base64 are heavy: respect the 18-photo cap and quota-eviction logic in `saveNow()`.

## Rules for the Android app

- Stack pins matter: AGP 9.1.1 needs Hilt ≥2.59, Gradle 9.1+. Build with Java 21 (`/opt/homebrew/opt/openjdk@21`), `ANDROID_HOME=~/Android/sdk`.
- Gemini key comes from `.env` via secrets-gradle-plugin → `BuildConfig.GEMINI_API_KEY`.
- When porting demo features, the demo is the spec — match its behavior and Chinese strings.

## Current top priorities

1. Fill `MAMA_GATEWAY` in `web_demo.html` + deploy `cors_proxy_worker.js` (testers' key-free Google login — see HANDOFF.md §MAMA Cloud)
2. Reconcile Android toolchain: docs say Java 21, `app/build.gradle.kts` currently sets `sourceCompatibility = VERSION_11`. Bump the build to 21 to match (post-handoff item).
3. Android UI is English-only at handoff; port the demo's `I18N` table to `values-zh-rTW/strings.xml` and externalize every hardcoded `Text(...)` to `stringResource(R.string.*)`.
4. Multi-parent sync design (the #1 team ask — blocked on choosing a backend).

See `README.md` §"Known limitations / post-handoff TODOs" for the full list.

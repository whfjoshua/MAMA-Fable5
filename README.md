# MAMA Scheduler 2.0

AI-powered family scheduling app — full rewrite of the original MAMA app with a Material You redesign and clean architecture.

## What's new vs. the original

**Design**
- Material You: dynamic color from your wallpaper on Android 12+ (toggle in Family tab), with a lavender brand fallback and full dark-mode support
- Modern Material 3 components throughout: tonal cards, segmented buttons, filter chips, badges, expressive typography
- Redesigned bottom navigation with filled/outlined icon states and a live badge on Approvals

**Architecture**
- The 3,570-line `SchedulerApp.kt` monolith is now ~20 focused UI files (one screen + one ViewModel per feature)
- Hilt dependency injection everywhere; no more god-ViewModel — 5 per-screen ViewModels
- Layers: `data` (Room, Retrofit/Gemini, DataStore) → `domain` (parser, agent, conflict detection, recurrence, photos, weather) → `ui`
- Navigation Compose with proper state save/restore per tab
- DataStore replaces SharedPreferences for settings

**Features (all preserved)**
- Natural-language event parsing (Gemini + local fallback) → approval queue
- AI chat assistant with pending-action approve/reject and weather alerts
- Month / Week / Day calendar views
- Conflict detection: overlaps, travel buffers, per-kid daily limits
- Photo highlights (AI-screened from your library)
- Google Calendar + device calendar two-way sync
- Event reminders and daily morning summary notification

## Project structure

```
com.mama.scheduler/
├── MainActivity.kt / MamaApplication.kt
├── di/AppModule.kt                  # Hilt module
├── core/DateUtils.kt
├── auth/GoogleAuthManager.kt
├── data/
│   ├── local/                       # Room entities + DAOs + database
│   ├── remote/GeminiApi.kt          # Retrofit + Moshi Gemini client
│   ├── prefs/SettingsRepository.kt  # DataStore settings
│   └── repository/                  # EventRepository, ChatRepository
├── domain/                          # NaturalLanguageParser, SchedulerAgent,
│                                    # ConflictDetector, RecurrenceExpander,
│                                    # PhotoFinder, WeatherService
├── notifications/                   # Alarms + morning summary worker
├── sync/                            # Google + system calendar sync
└── ui/
    ├── theme/                       # Material You theme
    ├── components/                  # EventCard, dialogs, shared pieces
    ├── MamaApp.kt                   # Nav host + bottom bar
    └── screens/                     # agenda, calendar, approvals, chat, profiles
```

## Build

Prerequisites: Android Studio (or Java 21 + Android SDK), same as the original project.

1. Put your Gemini API key in `.env` (see `.env.example`); or set it later in-app under Family → Gemini API key
2. Build:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME=/Users/whfjoshua/Android/sdk
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Installs as a separate app (`com.mama.scheduler`), so the original stays untouched.

## Notes

- Stack: Kotlin 2.2, AGP 9.1.1, Compose BOM 2024.09, Room 2.7, Hilt 2.59.2 (AGP 9-compatible), Navigation Compose, DataStore, WorkManager
- The OpenWeatherMap key placeholder lives in `domain/WeatherService.kt` (weather falls back to mock data without it)
- Database is fresh (v1, new name `mama_scheduler_db`) — old app data is not migrated

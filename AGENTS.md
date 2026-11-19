# AGENTS.md — guide for AI coding assistants

Read this before changing anything in this repo. It applies to Claude Code, Cursor, Copilot, Codex, and similar tools. `CLAUDE.md` points here.

## What this is
An Android home-screen widget that shows the owner's WakaTime coding stats. It is a single-module Kotlin app, distributed as signed APKs through GitHub Releases. It is not on the Play Store.

## Stack (pinned — don't bump casually)
| Thing | Version |
|---|---|
| Gradle (wrapper) | 8.9 |
| Android Gradle Plugin | 8.7.2 |
| Kotlin + Compose compiler plugin | 2.0.21 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| Java toolchain | 17 |
| Glance (`glance-appwidget`) | 1.1.1 |
| WorkManager | 2.9.1 (2.10+ needs compileSdk 35+ changes; check before bumping) |
| Compose BOM | 2024.10.01 |

There is no version catalog; versions live inline in `build.gradle.kts` and `app/build.gradle.kts`. Keep the dependency count small. Networking uses `HttpURLConnection` with `org.json` on purpose, so don't add Retrofit, OkHttp, Moshi, or Hilt unless asked.

## Layout
```
app/src/main/java/dev/vishnu/wakawidget/
  MainActivity.kt        Compose settings screen: paste API key, "Save key", pin widget
  ApiKeyStore.kt         API key encrypted with AES-GCM; the key lives in AndroidKeyStore
  WakaApi.kt             Blocking HTTP client (call it off the main thread)
  Stats.kt               Stats/Item/Day model, JSON (de)serialization, StatsCache, formatDuration()
  RefreshWorker.kt       CoroutineWorker; schedule() every 30 min, refreshNow(force)
  widget/
    WakaWidget.kt        GlanceAppWidget: 3 responsive layouts
    WakaWidgetReceiver.kt  Receiver; onUpdate schedules work and triggers a refresh
    RefreshAction.kt     The ↻ button's ActionCallback
    WidgetUpdater.kt     Writes stats/error into every widget's Glance state, then update()
.github/workflows/build.yml  CI: builds the signed APK and publishes the Release build-N
```

## Data flow
1. `RefreshWorker` (periodic, on-demand, or ↻) loads the key, then calls `WakaApi.fetchStats()`.
2. On success it calls `StatsCache.save()` (SharedPreferences) and `WidgetUpdater.push(stats, null)`.
3. `WidgetUpdater` writes `STATS_KEY` (JSON) and `ERROR_KEY` into each widget's Glance Preferences state, then calls `update()`.
4. `WakaWidget.provideContent` reads `currentState(STATS_KEY)`, falling back to the cache for newly placed widgets.

On failure, `push(null, "message")` keeps the old stats and only sets the error line. Don't clear stats on errors.

## WakaTime API
- Base: `https://wakatime.com/api/v1/users/current`
- Auth header: `Authorization: Basic base64(apiKey)`, using the raw key with no `:`.
- `/status_bar/today` returns today's `grand_total.total_seconds`, `languages[]`, and `projects[]`.
- `/summaries?start=YYYY-MM-DD&end=YYYY-MM-DD` returns the 7 days for the chart, as `data[].range.date` and `data[].grand_total.total_seconds`.
- **API keys are limited to plugin endpoints** (seen 2026-11-19). `/status_bar/today` works, but `/summaries`, `/stats`, `/durations` and `/insights` return 403 `"API Keys are only for plugins, use your personal oauth token"`. The app treats a 403 from `/summaries` as "no chart" (empty `week`), and only a 401, or a 403 from `status_bar`, as a bad key. The chart needs WakaTime OAuth (not built yet).
- The free plan only exposes about 2 weeks of history, so don't build features needing more without saying so.
- A 401 (or a 403 from `status_bar/today`) means a bad key (`AuthException`). Anything else is retried by WorkManager.
- Rate limits: refresh no more often than every 15 minutes (the WorkManager floor anyway).

## Glance rules (most common source of bugs)
- Widgets are RemoteViews underneath. Use only `androidx.glance.*` composables. Never import `androidx.compose.foundation`, `material3`, or `ui.Modifier` into widget files. Use `GlanceModifier`, `androidx.glance.text.Text`, and `androidx.glance.layout.*`.
- Rows and Columns are limited to 10 children, and the whole layout to about 50 views. The 7-day chart is 7 `Box` bars, not Canvas.
- Colors come from `GlanceTheme.colors.*` so they follow Material You and dark mode. Don't hardcode colors in the widget.
- Sizes are `SMALL` (110×50), `MEDIUM` (220×130), and `LARGE` (220×240) with `SizeMode.Responsive`. Branch on `LocalSize.current`, not on the widget ID.
- `cornerRadius` only works on Android 12+. That's fine; it's ignored below.
- Never do network or disk-heavy work inside `provideContent`. Fetch in the worker and push state.

## Build, sign, release
- CI (`build.yml`) runs on every push to `main` and on manual dispatch. It decodes the `KEYSTORE_BASE64` secret, runs `./gradlew assembleRelease`, and publishes Release `build-<run_number>` with a fixed-name asset `wakatime-widget.apk` (+ `.sha256`), so `releases/latest/download/wakatime-widget.apk` always points at the newest build. PRs run a separate unsigned `assembleDebug lint` job with no secrets.
- `versionCode` = `GITHUB_RUN_NUMBER`, so every CI build is installable over the previous one. Don't hardcode versionCode.
- Required repo secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Local builds without those env vars are signed with the **debug** key and can't be installed over CI builds.
- **Never** commit `*.jks`, `signing-secrets.txt`, API keys, or `local.properties`. `.gitignore` covers them; keep it that way.
- **Never** change `applicationId` (`dev.vishnu.wakawidget`) or the signing key. Either change breaks updates for everyone who installed it.

## Verifying changes
- Some AI sandboxes can't reach Google's Maven or the Android SDK. If you can't build, say so plainly. Don't claim it compiles. Push and read the Actions log, or ask the owner to paste it.
- If you can build: `./gradlew assembleRelease` must pass. Also run `./gradlew lint` when touching the manifest or resources.
- There are no tests yet. If you add logic (parsing, formatting), add JVM unit tests under `app/src/test/`. Keep `Stats.fromJson` and `formatDuration` pure so they stay testable.
- For widget UI changes, describe what each size bucket looks like after the change.

## Conventions
- Kotlin official style, 4-space indent, trailing commas allowed.
- User-facing text is plain and specific: say what happened and what to do ("API key rejected. Tap to fix"), with no apologies or exclamation marks.
- Keep the error line short enough to fit one line on the small widget (about 30 characters).
- Small, focused commits. Each push to `main` ships a release, so don't push half-done work to `main`; use a branch.

## Ideas backlog (not started — ask before doing)
- Daily goal with progress ring
- Pick which stat the small widget shows (today, week, or a project)
- Tap a chart bar for that day's breakdown
- Widget preview image (`previewLayout`) for the widget picker

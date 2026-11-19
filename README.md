# WakaTime Widget for Android

A resizable home-screen widget for your WakaTime coding stats.

| Widget size | Shows |
|---|---|
| Small (2×1) | Today's coding time |
| Medium (4×2) | + top languages and top project |
| Large (4×3+) | + bar chart of the last 7 days |

Refreshes about every 30 minutes in the background. Tap ↻ to refresh now, tap anywhere else to open settings. Colors follow your wallpaper (Material You) on Android 12+. Works on Android 8.0+.

Your API key is encrypted with the Android Keystore and only ever sent to wakatime.com.

## Setup (one time)

1. Create a new GitHub repo and push this folder to the `main` branch.
2. In the repo, open **Settings → Secrets and variables → Actions** and add four repository secrets, using the values from `signing-secrets.txt`:
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
3. Open the **Actions** tab and run **Build APK** (or push any commit).
4. After ~5 minutes, open **Releases**, download the APK on your phone and install it.

Keep `signing-secrets.txt` somewhere safe and **never commit it**. Every update must be signed with the same key, or Android will refuse to install it over the old version.

## Updates

Every push to `main` builds a new release (`build-N`) with a higher version code. To get updates automatically, add the repo URL to [Obtainium](https://github.com/ImranR98/Obtainium).

## Building locally

Open the folder in Android Studio, or run `./gradlew assembleRelease`. Without the keystore environment variables, local builds are signed with the debug key, so they can't be installed over a CI build (uninstall first).

## Project layout

- `widget/WakaWidget.kt`: the Glance widget and its three size layouts
- `WakaApi.kt`: calls `status_bar/today` and `summaries` (last 7 days)
- `RefreshWorker.kt`: background refresh via WorkManager
- `ApiKeyStore.kt`: encrypted API key storage
- `MainActivity.kt`: settings screen (paste key, add widget)

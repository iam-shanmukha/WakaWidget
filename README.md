# WakaTime Widget for Android

A resizable home-screen widget for your [WakaTime](https://wakatime.com) coding stats.

| Widget size | Shows |
|---|---|
| Small (2×1) | Today's coding time |
| Medium (4×2) | + top languages and top project |
| Large (4×3+) | + bar chart of the last 7 days |

### API key or OAuth token

The app accepts either one:

| You paste | Where to get it | Shows |
|---|---|---|
| Secret API key (`waka_…`) | [wakatime.com/settings/api-key](https://wakatime.com/settings/api-key) | Today's time, languages and projects |
| Personal OAuth token (`waka_tok_…`) | [wakatime.com/oauth](https://wakatime.com/oauth) | All of that, plus the 7-day chart |

WakaTime limits API keys to the endpoints its editor plugins use, so they can't read the 7-day history. With an API key, the large widget shows "7-day chart needs an OAuth token" where the chart would be. A personal OAuth token doesn't require registering an OAuth app.

It refreshes about every 30 minutes in the background. Tap ↻ to refresh now, or tap anywhere else to open settings. Colors follow your wallpaper (Material You) on Android 12+. Works on Android 8.0+.

## Install

1. On your phone, download **[wakatime-widget.apk](https://github.com/iam-shanmukha/WakaWidget/releases/latest/download/wakatime-widget.apk)** (always the newest build) and open it to install. You may need to allow installs from your browser. Older builds and SHA-256 checksums are on the [Releases](https://github.com/iam-shanmukha/WakaWidget/releases) page.
2. Open **WakaTime Widget**, paste your API key or personal OAuth token (see the table above) and tap **Save**.
3. Tap **Add widget to home screen**, or long-press the home screen, open Widgets and find WakaTime.

To get updates automatically, add this repo's URL to [Obtainium](https://github.com/ImranR98/Obtainium).

## Privacy

- Your API key or token is encrypted with a key held in the Android Keystore and stays on the phone.
- The app only talks to `wakatime.com`. There are no analytics, ads or other servers.
- The only permission it asks for is `INTERNET`.

## Building it yourself

You need JDK 17 and the Android SDK (platform 35), or Android Studio.

```sh
./gradlew assembleDebug      # app/build/outputs/apk/debug/
./gradlew assembleRelease    # signed with the debug key unless KEYSTORE_* env vars are set
```

A build signed with a different key can't be installed over the release APK. Uninstall the app first.

### Releasing from your own fork

CI (`.github/workflows/build.yml`) builds a signed APK on every push to `main` and publishes it, with a SHA-256 checksum, as Release `build-N`. It also keeps the APK as a workflow artifact. Pull requests get a debug build, which you can download from the run's Artifacts. To make releases from a fork:

1. Create a keystore and keep it somewhere safe. Every future update has to be signed with it.
   ```sh
   keytool -genkeypair -v -keystore release.jks -alias wakawidget \
     -keyalg RSA -keysize 4096 -validity 36500
   base64 -w0 release.jks   # value for KEYSTORE_BASE64
   ```
2. In your fork, open **Settings → Secrets and variables → Actions** and add `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
3. Push to `main`, or run **Build APK** from the Actions tab.

Never commit the keystore or its passwords. `.gitignore` already excludes `*.jks`, `*.keystore` and `signing-secrets.txt`.

## Project layout

- `widget/WakaWidget.kt`: the Glance widget and its three size layouts
- `WakaApi.kt`: calls `status_bar/today` and `summaries` (last 7 days)
- `RefreshWorker.kt`: background refresh via WorkManager
- `ApiKeyStore.kt`: encrypted API key storage
- `MainActivity.kt`: settings screen (paste key, add widget)

More detail on the architecture and on Glance's pitfalls is in [AGENTS.md](AGENTS.md).

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE). This project is not affiliated with WakaTime.

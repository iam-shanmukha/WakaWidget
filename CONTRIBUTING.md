# Contributing

Thanks for helping out. A few things keep this project easy to maintain:

- **Read [AGENTS.md](AGENTS.md) first.** It covers the architecture, the pinned versions and the Glance rules that cause most widget bugs.
- **Keep it small.** Networking deliberately uses `HttpURLConnection` and `org.json`. Please open an issue before adding a dependency or a larger feature.
- **Work on a branch and open a PR against `main`.** CI builds a debug APK for each PR. Every merge to `main` publishes a release.
- **Check your change builds:** run `./gradlew assembleDebug lint`. If you add parsing or formatting logic, add JVM unit tests under `app/src/test/`.
- **For widget UI changes,** describe or screenshot all three sizes (small, medium, large).
- **Never commit secrets:** API keys, keystores (`*.jks`) or `local.properties`.

## Reporting bugs

Include your Android version, launcher and widget size, and what the widget showed. Never paste your WakaTime API key.

## Security

To report a vulnerability, use GitHub's private vulnerability reporting (**Security → Report a vulnerability**) rather than a public issue.

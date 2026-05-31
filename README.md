# Sted

A native Android habit-tracker app. The app is deliberately built **without WebView and without external UI libraries** — the entire interface is constructed programmatically with native Android Views, so the official Android build tools produce a clean APK.

> 🇩🇪 Eine deutsche Version dieser Doku findest du in [`README_DE.md`](Steda_1.0/README_DE.md).

## Features

- Guided setup on first launch: pick a start date and create up to 10 habits, each with a weekly goal (0–7 days).
- Clean main view showing only your configured habits, with a settings gear in the top-right.
- Tabs: **Übersicht** (Overview), **Tracken** (Track), **Woche** (Week), **Tribe**.
- Daily status entry: empty, ✓ done, ✗ missed, – neutral/skip.
- Automatic daily, weekly, and overall completion rates, plus per-habit weekly-goal evaluation and current streak.
- Weekly reflection: focus, wins, blockers, adjustments.
- JSON export/import via Android's file picker for backup and device transfer.
- Reset the app from the settings menu.
- Dark-mode look with dark background, dark cards, and light text.
- Optional **Tribe Sync** over Firebase (Anonymous Auth + Firestore) — the app works fully offline without it.

## Tech stack

| | |
|---|---|
| Language | Java |
| UI | Native Android Views, built entirely in code |
| Package | `habit.tracker.steda` |
| minSdk | 24 |
| target / compileSdk | 36 |
| Android Gradle Plugin | 8.11.1 |
| Gradle | 9.0.0 |

## Build & run

### Prerequisites

- **JDK 17+ with a compiler** (`javac`) — a headless JRE is **not** enough. On Fedora, for example: `sudo dnf install java-21-openjdk-devel`.
- **Android SDK** with Platform 36 and Build-Tools 36.x. Point Gradle at it via a `local.properties` file in the project root:
  ```properties
  sdk.dir=/path/to/your/Android/Sdk
  ```
  (`local.properties` is machine-specific and intentionally git-ignored.)

### From the command line

```bash
cd Steda_1.0

# Build a debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Build and install on a connected device (USB debugging enabled)
./gradlew installDebug
```

### In Android Studio

1. **File → Open** and select the `Steda_1.0` project folder.
2. Wait for Gradle sync (install SDK Platform 36 / Build-Tools 36.x if prompted).
3. Connect a device with USB debugging enabled, select it, and press **Run ▶**.

## Firebase / Tribe Sync (optional)

Tribe Sync stays disabled until you provide your own Firebase project. The repository ships a **placeholder** config — it contains no real keys.

1. Create a Firebase project and enable **Anonymous Authentication** and **Cloud Firestore**.
2. Fill in your values in `Steda_1.0/app/src/main/res/values/firebase_config.xml`.
3. See [`FIREBASE_SETUP_DE.md`](Steda_1.0/FIREBASE_SETUP_DE.md) for the full walkthrough.

Without this configuration the app continues to work locally; only the shared Tribe features stay off.

## Contributing

Contributions are welcome! Please:

1. Fork the repo and create a feature branch.
2. Keep the no-WebView / no-external-UI-library constraint — the UI is plain Android Views.
3. Use your **own** Firebase project for any Tribe-Sync work; never commit real keys.
4. Open a pull request describing the change.

## Privacy

See [`PRIVACY.md`](Steda_1.0/PRIVACY.md) for the privacy policy and data-handling details.

## License

No license has been chosen yet. Until one is added, default copyright applies and reuse rights are not granted — consider adding an OSI-approved license (e.g. MIT or Apache-2.0) before inviting wider contributions.

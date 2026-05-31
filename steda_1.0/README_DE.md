# Endorphine Tribe - 10W Challenge Tracker – Android-Studio-Projekt

Native Android-App für eine 10-Wochen-Habit-Challenge. Die App ist bewusst ohne WebView und ohne externe UI-Libraries gebaut, damit Android Studio eine saubere APK mit offiziellen Build-Tools erzeugt.

## Enthaltene Funktionen

- Beim ersten Start startet ein Setup-Prozess
- Startdatum auswählen
- Bis zu 10 Habits anlegen
- Wochenziel pro Habit von 0 bis 7
- Danach cleane Hauptansicht mit nur den eingestellten Habits
- Oben rechts Zahnrad für Einstellungen
- Reiter: **Übersicht**, **Tracken**, **Wochen**, **Reflexion**
- Reiter **Habits** und **Export** wurden entfernt und in die Einstellungen verschoben
- Tägliche Status-Eingabe: leer, ✓ geschafft, ✗ nicht geschafft, – neutral/skip
- Automatische Tagesquote, Wochenquote und Gesamtquote
- Automatische Wochenziel-Auswertung pro Habit
- Aktueller Streak
- Reflexion pro Woche: Fokus, Wins, Blocker, Anpassung
- JSON Export/Import über Androids Datei-Auswahl
- App zurücksetzen aus dem Einstellungsmenü
- App-Name oben in der App und im Launcher: **Endorphine Tribe - 10W Challenge Tracker**
- Einfaches integriertes App-Logo als Launcher-Icon

## Technisches Setup

- Sprache: Java
- UI: native Android Views, komplett programmatisch gebaut
- minSdkVersion: 24
- targetSdkVersion: 36
- compileSdk: 36
- Android Gradle Plugin: 8.11.1
- Package: `habit.tracker.steda`

## Build in Android Studio

1. ZIP entpacken.
2. Android Studio öffnen.
3. **File > Open** und den entpackten Projektordner (Steda) auswählen.
4. Gradle Sync abwarten.
5. Falls Android Studio SDK-Komponenten verlangt: **Android SDK Platform 36** und **Build-Tools 36.x** installieren.
6. Für Test am Handy: Handy per USB verbinden, USB-Debugging aktivieren, oben beim Gerät auswählen und **Run ▶** drücken.
7. Für eine APK-Datei: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
8. Die Debug-APK liegt danach unter: `app/build/outputs/apk/debug/app-debug.apk`
9. Für eine signierte Version zum Verteilen: **Build > Generate Signed App Bundle / APK > APK**

## Hinweis

Die App synchronisiert nicht direkt mit einem gemeinsamen Excel- oder Google-Sheet. Sie ist als sinnvolle mobile App-Version des 10-Wochen-Habit-Trackers gedacht. Per JSON Export/Import kannst du Daten sichern oder auf andere Geräte übertragen.


## Aktuelle Design-Änderungen

- Titel in der App: `Endorphine Tribe`
- Untertitel: `10 Week Challenge Tracker`
- Darkmode-Optik mit dunklem Hintergrund, dunklen Karten und heller Schrift

## Firebase / Tribe Sync

Die Version enthält zusätzlich einen Netzwerkmodus. Details findest du in:

```text
FIREBASE_SETUP_DE.md
```

Ohne Firebase-Konfiguration funktioniert die App weiterhin lokal. Der Tribe-Sync wird erst aktiv, wenn du die Werte in `app/src/main/res/values/firebase_config.xml` einträgst und in Firebase Anonymous Auth + Firestore aktivierst.

## Änderung: Einstellungsrad Toggle

Wenn die App bereits im Einstellungsmenü ist und du erneut auf das Zahnrad tippst, schließt sich das Menü und die App springt zurück zur Übersicht.

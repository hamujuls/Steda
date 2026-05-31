# Firebase Sync Setup für Endorphine Tribe

Diese Version enthält den Netzwerk-/Gruppenmodus über Firebase:

- Firebase Anonymous Auth
- Cloud Firestore
- Gruppen-Code
- Tribe-Reiter mit gemeinsamer Übersicht
- Datenschutz-Modus: nur Fortschritt oder heutige Habit-Details

## 1. Firebase-Projekt erstellen

1. Öffne Firebase Console.
2. Neues Projekt erstellen, z.B. `endorphine-tribe-10w`.
3. In Firebase eine Android-App hinzufügen.
4. Package Name exakt eintragen:

```text
habit.tracker.steda
```

## 2. App-Werte eintragen

Öffne im Android-Studio-Projekt:

```text
app/src/main/res/values/firebase_config.xml
```

Ersetze dort die Platzhalter:

```xml
<string name="firebase_project_id">PASTE_FIREBASE_PROJECT_ID_HERE</string>
<string name="firebase_application_id">PASTE_FIREBASE_APP_ID_HERE</string>
<string name="firebase_api_key">PASTE_FIREBASE_API_KEY_HERE</string>
```

Die Werte findest du in der Firebase Console bei:

```text
Project settings > Deine Android App
```

Du kannst zusätzlich `google-services.json` herunterladen, diese Projektversion initialisiert Firebase aber bewusst über die Werte in `firebase_config.xml`, damit das Projekt auch ohne Google-Services-Plugin baubar bleibt.

## 3. Anonymous Authentication aktivieren

In Firebase Console:

```text
Build > Authentication > Sign-in method > Anonymous > Enable
```

## 4. Firestore aktivieren

In Firebase Console:

```text
Build > Firestore Database > Create database
```

Für den ersten Test kannst du im Testmodus starten. Für eine einfache Challenge-Struktur kannst du später diese Regeln verwenden:

```js
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /challenges/{challengeId}/participants/{userId} {
      allow read: if request.auth != null;
      allow create, update, delete: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

Hinweis: Der Gruppen-Code ist praktisch für die Sortierung in Gruppen, aber kein echtes Passwort. Wer den Gruppen-Code kennt und authentifiziert ist, kann die Gruppenübersicht lesen.

## 5. App testen

1. Gradle Sync in Android Studio durchführen.
2. App auf dem Handy starten.
3. Im Setup Name + Gruppen-Code eintragen.
4. Auf mehreren Geräten denselben Gruppen-Code nutzen.
5. Im Reiter `Tribe` sollten die Teilnehmer erscheinen.

## Datenstruktur in Firestore

```text
challenges/{gruppenCode}/participants/{anonymousUserId}
```

Pro Teilnehmer werden unter anderem gespeichert:

- Anzeigename
- heutige Quote
- Wochenquote
- Gesamtquote
- Streak
- Anzahl erreichter Wochenziele
- optional heutige Habit-Details

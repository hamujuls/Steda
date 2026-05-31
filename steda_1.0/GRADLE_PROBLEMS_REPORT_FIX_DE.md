# Gradle Problems Report Fix

Der hochgeladene `problems-report.html` zeigt hauptsächlich Warnungen, keine eigentlichen Compile-Fehler.

Geändert in dieser Version:

- `app/build.gradle` nutzt jetzt die neue Groovy-Gradle-Schreibweise mit `=`:
  - `namespace = ...`
  - `compileSdk = ...`
  - `minSdk = ...`
  - `targetSdk = ...`
  - `versionCode = ...`
  - `versionName = ...`
- `gradle.properties` enthält weiterhin:
  - `android.useAndroidX=true`
  - `android.enableJetifier=true`
- `org.gradle.configuration-cache=false` wurde ergänzt, damit Android Studio nicht ständig einen verwirrenden Configuration-Cache-Problems-Report erzeugt.

Wenn der Build trotzdem fehlschlägt, bitte nicht den `problems-report.html` schicken, sondern im Build-Fenster die erste rote Fehlermeldung kopieren oder einen Screenshot vom oberen roten Fehler schicken.

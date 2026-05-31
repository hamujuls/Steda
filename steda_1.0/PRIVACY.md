# Datenschutzerklärung für Steda

**Stand: 31. Mai 2026**

Diese Datenschutzerklärung gilt für die Android-App **Steda** (Paketname `habit.tracker.steda`,
nachfolgend „die App"). Verantwortlich für die Datenverarbeitung im Sinne der DSGVO ist der
Entwickler der App, erreichbar unter:

**Kontakt:** j.hackermueller@gmail.com

---

## 1. Überblick

Steda ist ein Habit-Tracker. Die App ist **datensparsam** aufgebaut und funktioniert
standardmäßig **vollständig offline**. Eine Online-Funktion („Tribe Sync" inkl. Gruppen-Chat)
ist **optional** und wird nur aktiv, wenn du sie selbst einschaltest.

Es gibt **keine Werbung**, **kein Tracking zu Werbezwecken** und **keine Analyse-/Werbe-SDKs**.

---

## 2. Lokaler Modus (Standard)

Wenn du die App im lokalen Modus nutzt (Standard nach der Installation):

- Alle deine Daten – Gewohnheiten, tägliche Einträge, Statistiken, Reflexionen –
  werden **ausschließlich lokal auf deinem Gerät** gespeichert.
- Es findet **keine Übertragung** dieser Daten an uns oder an Dritte statt.
- Du kannst deine Daten jederzeit als JSON-Datei **exportieren und importieren**
  (eigenes Backup, das nur du kontrollierst).

---

## 3. Optionaler Online-Modus „Tribe Sync" (nur wenn von dir aktiviert)

Aktivierst du in den Einstellungen die Online-Speicherung bzw. den Tribe Sync, nutzt die App
Dienste von **Google Firebase** (Firebase Authentication und Cloud Firestore). In diesem Fall
werden folgende Daten verarbeitet:

**a) Anonyme Anmeldung (Firebase Anonymous Authentication)**
- Es wird eine **anonyme, zufällige Nutzer-ID** erzeugt.
- **Kein Klarname, keine E-Mail-Adresse und kein Passwort erforderlich.**

**b) In Cloud Firestore gespeicherte Daten (innerhalb deiner Gruppe / deines Gruppen-Codes):**
- der von dir **frei gewählte Anzeigename** (du musst keinen echten Namen verwenden),
- **Fortschritts-Kennzahlen**: heutige Quote, Wochenquote, Gesamtquote, Streak,
  erreichte Wochenziele, Anzahl aktiver Habits,
- **optional** die Namen deiner heutigen Gewohnheiten (je nach Sichtbarkeits-/Datenschutz-Einstellung),
- **Chat-Nachrichten**, die du im Gruppen-Chat sendest.

**Zweck:** Diese Daten dienen ausschließlich dazu, die gemeinsame Gruppenübersicht
(„Rangliste") und den Gruppen-Chat für alle Mitglieder mit demselben Gruppen-Code bereitzustellen.

**Sichtbarkeit:** Wer den Gruppen-Code kennt und in der App angemeldet ist, kann die
Gruppenübersicht und den Chat dieser Gruppe sehen. Der Gruppen-Code ist eine Sortierhilfe,
**kein sicheres Passwort**. Teile ihn daher nur mit Personen, denen du vertraust.

**Rechtsgrundlage:** Art. 6 Abs. 1 lit. a DSGVO (deine Einwilligung durch Aktivierung des
Online-Modus) bzw. lit. b (Bereitstellung der von dir gewünschten Funktion).

---

## 4. Auftragsverarbeiter / Drittanbieter

Im Online-Modus werden Daten über **Google Firebase** (Google Ireland Limited / Google LLC)
verarbeitet und auf deren Servern gespeichert. Google handelt dabei als Auftragsverarbeiter.
Die Übertragung erfolgt **verschlüsselt (HTTPS/TLS)**.

Weitere Informationen: https://firebase.google.com/support/privacy

Eine **Weitergabe oder ein Verkauf deiner Daten an sonstige Dritte findet nicht statt.**

---

## 5. Speicherdauer

- **Lokale Daten:** bleiben auf deinem Gerät, bis du sie löschst (z. B. über
  „App zurücksetzen") oder die App deinstallierst.
- **Online-Daten:** bleiben in der jeweiligen Gruppe gespeichert, bis sie gelöscht werden
  (siehe Abschnitt 6).

---

## 6. Deine Rechte und Löschung deiner Daten

Du hast nach der DSGVO das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung der
Verarbeitung und Datenübertragbarkeit sowie das Recht, deine Einwilligung zu widerrufen.

**Daten löschen:**
- **Lokal:** „App zurücksetzen" in den Einstellungen entfernt deine lokalen Daten;
  die Deinstallation der App entfernt ebenfalls alle lokal gespeicherten Daten.
- **Online:** Deaktiviere den Tribe Sync und/oder fordere die Löschung deiner Online-Daten
  (Teilnehmereintrag, Chat-Nachrichten) per E-Mail an: **j.hackermueller@gmail.com**.
  Wir löschen die zugeordneten Daten anschließend.

Es gibt **keine Benutzerkonten mit Login** – die Anmeldung ist anonym.

---

## 7. Kinder

Die App richtet sich **nicht an Kinder unter 13 Jahren**. Da der optionale Gruppen-Chat
nutzergenerierte Inhalte ermöglicht, sollte die Online-Funktion nur von Personen ab 13 Jahren
genutzt werden.

---

## 8. Berechtigungen

Die App benötigt für den Online-Modus **Internetzugriff**. Für die Export-/Import-Funktion
greift sie über die System-Dateiauswahl auf die von dir ausgewählte Datei zu. Es werden keine
sensiblen Berechtigungen (Standort, Kontakte, Kamera o. Ä.) verwendet.

---

## 9. Änderungen dieser Datenschutzerklärung

Wir können diese Datenschutzerklärung anpassen, wenn sich die App oder rechtliche Vorgaben
ändern. Die jeweils aktuelle Fassung ist über den in der App bzw. im Play-Store-Eintrag
verlinkten Ort abrufbar.

---

# Privacy Policy for Steda (English)

**Last updated: 31 May 2026**

This Privacy Policy applies to the Android app **Steda** (package `habit.tracker.steda`).
Contact: **j.hackermueller@gmail.com**

**Overview.** Steda is a habit tracker. By default it works **fully offline**. An optional online
feature („Tribe Sync", incl. group chat) is only active if you enable it yourself. The app contains
**no ads** and **no advertising/analytics tracking SDKs**.

**Local mode (default).** All your data (habits, daily entries, statistics, reflections) is stored
**only on your device** and is **not transmitted** to us or any third party. You can export/import
your data as a JSON file (a backup only you control).

**Optional online mode „Tribe Sync".** If you enable it, the app uses **Google Firebase**
(Authentication + Cloud Firestore). An **anonymous** user ID is created (no real name, email or
password required). Stored within your group: your **chosen display name**, **progress metrics**
(daily/weekly/overall quota, streak, weekly goals, active habits), **optionally** today's habit names,
and **chat messages** you send. Purpose: providing the shared group leaderboard and group chat to
members who share the same group code. Anyone who knows the group code and is signed in can see that
group's overview and chat; the group code is a sorting key, **not a secure password**.

**Processors.** Online data is processed by **Google Firebase** and transmitted **encrypted (HTTPS/TLS)**.
Your data is **not sold or shared** with other third parties. See https://firebase.google.com/support/privacy

**Retention & deletion.** Local data stays until you delete it („Reset app") or uninstall.
To delete online data, disable Tribe Sync and/or request deletion via **j.hackermueller@gmail.com**.
There are **no login accounts** – authentication is anonymous.

**Children.** The app is **not directed at children under 13**. The optional chat allows
user-generated content and should be used only by people aged 13+.

**Permissions.** Internet access (online mode only) and file access via the system file picker for
export/import. No sensitive permissions (location, contacts, camera, etc.).

**Changes.** This policy may be updated; the current version is available at the location linked in
the app and the Play Store listing.

# Letzte Änderungen

## Lizenz-Karte in den Einstellungen
- Neue Karte **„Lizenz"** (oberhalb von „Mitentwickeln"): „Diese Software steht unter der Lizenz **CC NC-SA 4.0**" mit Kurzerklärung – teilen/weiterentwickeln erlaubt, solange nicht kommerziell und unter derselben Lizenz.

## Entwickler-Kontakt & Mitentwickeln in den Einstellungen
- Ganz unten in den Einstellungen gibt es zwei neue Karten:
  - **Entwickler-Kontakt** mit Button „✉ Entwickler kontaktieren" → öffnet die Mail-App an `j.hackermueller@gmail.com` (Betreff „Steda – Feedback").
  - **Mitentwickeln** mit Button „GitHub Repository öffnen" → öffnet `https://github.com/hamujuls/Steda` im Browser.

## „App zurücksetzen" startet wieder bei der Datenschutz-/Speicherauswahl
- Nach dem Zurücksetzen landet man jetzt – genau wie beim allerersten App-Start – wieder auf der Auswahlseite „Datenschutzhinweis / Nur am Telefon", statt direkt im Setup. So kann man die Speicherart (Online-Sync vs. lokal) erneut festlegen.

## Setup-Seite optisch überarbeitet
- **Hero-Header** wie auf der Übersicht: Kapitälchen-Label („Ersteinrichtung" / „Profil & Challenge"), großer Titel („Willkommen in Steda" / „Einstellungen"), grünes Häkchen-Icon im Kreis und ein erklärender Untertitel – statt der schlichten Abschnittsüberschrift.
- **Schritt-Überschriften** mit grünen Vektor-Icons gliedern das Formular: „Zeitraum", „Tribe Sync (optional)" bzw. „Speicherung: Nur am Telefon" und „Deine Habits".
- **Nummerierte Habit-Karten**: jede Karte hat ein grünes Nummern-Badge (1, 2, …) neben dem „Habit"-Label.
- **Datumsauswahl** als dezenter neutraler Button mit 📅-Icon statt vollflächig grünem Button.
- Gilt sowohl für das Ersteinrichtungs-Setup als auch für die eingebettete Ansicht in den Einstellungen.

## Fix: Doppelte Person in der Rangliste
- Teilnehmer werden jetzt unter der stabilen App-Benutzer-ID statt der wechselnden Firebase-Anmelde-ID gespeichert – dadurch entstehen bei Neuanmeldung/Neuinstallation keine neuen „Geister"-Einträge mehr.
- Beim Sync wird ein altes, unter der Firebase-ID angelegtes Dokument automatisch entfernt.
- Die Rangliste führt doppelte Einträge desselben Namens zusammen (neuester Stand gewinnt) – bestehende Doppelungen verschwinden so sofort.

## App-Icon & Splash poliert
- **Neues App-Icon**: kräftiger grüner Diagonal-Verlauf mit sauberem weißem Häkchen (statt blassem Grau) – als adaptives Icon (Android 8+) und neu gerenderte PNGs für alle Dichten (Android < 8), eckig und rund.
- **Splash-Screen** (Android 12+): dunkler App-Hintergrund `#0C0E12` mit gebrandetem Icon (grüner Ring + weißes Häkchen) statt schwarzem Standard.
- **Window-Hintergrund** auf App-Farbe gesetzt – nahtloser Start ohne schwarzes Aufblitzen.

## Navigation verschlankt – 4 statt 7 Tabs
- Bottom-Navigation auf 4 Tabs reduziert: **Übersicht · Tracken · Woche · Tribe** (größere Icons + Labels).
- **Woche** vereint Statistik + Reflexion über einen Umschalter oben (`Statistik | Reflexion`).
- **Tribe** vereint Rangliste + Chat über einen Umschalter (`Rangliste | Chat`).
- **Einstellungen** sind jetzt über ein ⚙-Icon oben rechts auf der Übersicht erreichbar.
- Zurück-Taste: aus Reflexion → Statistik, aus Chat → Rangliste, aus Einstellungen → Übersicht.

## Optische Überarbeitung – Teil 2
- **Feste Bottom-Navigation** mit echten Vektor-Icons (Übersicht, Tracken, Woche, Reflexion, Tribe, Chat, Einst.) statt der scrollenden Tab-Leiste oben.
- **Ring-/Donut-Diagramm** im Hero-Dashboard (animiert), Wochenbalken darunter zeigt „Diese Woche".
- **Animierte Fortschrittsbalken** und **hochzählende Prozentwerte** beim Öffnen.
- **Heatmap als Contribution-Grid**: Wochentag-Kopf (Mo–So), heutiges Feld umrandet, Tippen springt zum Tag im Tracken-Reiter; zukünftige Tage gedimmt.
- **Tribe-Podium**: Top 3 mit 🥇🥈🥉 und Gold-/Silber-/Bronze-Rahmen.
- **Streak-Kachel** mit 🔥-Icon und Farbakzent ab 3 Tagen.
- **Status-Chips** poppen beim Setzen animiert auf.
- **Metric-Kacheln** färben sich je nach Erfüllungsgrad.
- **Chat**: Datums-Trenner (Heute/Gestern), Sprechblasen-Ecken, Avatar-Initial bei fremden Nachrichten.
- **Leere Zustände** mit großem Icon in Kreis statt nur Text.

## Optische Überarbeitung
- Status- und Navigationsleiste haben jetzt exakt die Hintergrundfarbe der App – keine sichtbare Naht oben/unten mehr.
- Akzentfarbe und Farbpalette vereinheitlicht (frischeres Grün, ausgewogeneres Blau/Rot/Gelb).
- Touch-Feedback (Ripple-Effekt) auf Buttons, Navigations-Tabs, Status-Chips, Senden-Button und kleinen Aktionsbuttons.
- Heatmap-Legende mit farbigen Punkten statt reinem Fließtext.
- Status-Chips (✓ ✗ –) etwas größer und besser anklickbar.
- Header-Trennlinie dezenter.

## Frühere Änderungen
- Startdatum wird in der Übersicht nicht mehr angezeigt.
- Setup ist übersichtlicher: Startdatum, Tribe Sync und jedes Habit mit Wochenziel stehen jeweils in eigenen Karten/Feldern.
- Tribe-Sync-Einstellungen wurden aus dem Einstellungsmenü entfernt, weil sie über `Setup bearbeiten` erreichbar sind.
- Standard-Sichtbarkeit im Tribe Sync bleibt: `Habit-Details für heute anzeigen`.

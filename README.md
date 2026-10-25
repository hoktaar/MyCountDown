# MyCountDown

Android-App (Kotlin + Jetpack Compose) für Countdowns zu beliebigen Events.

- Countdowns mit Name, Emoji und Datum anlegen, bearbeiten, löschen
- Vorlagen: Urlaub, 1. Advent, Weihnachten, Silvester, Winter-/Sommerreifen, Geburtstag
- Wiederholung: einmalig, jährlich, automatisch 1. Advent, automatisch Ostern
- Eigene Kategorien (Name + Emoji) anlegen, zuweisen und danach filtern
- Lokale Speicherung, sortiert nach dem nächsten Termin
- Tägliche Erinnerung um 9 Uhr (7 Tage, 1 Tag und am Tag selbst)
- Homescreen-Widget mit dem nächsten Countdown

## Bauen
Mit Android Studio öffnen und starten, oder `gradle :app:assembleDebug`
(Android SDK nötig). Bei jedem Push baut GitHub Actions die Debug-APK.

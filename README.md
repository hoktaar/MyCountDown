# MyCountDown

Android-App (Kotlin + Jetpack Compose) für Countdowns zu beliebigen Events.

- Countdowns mit Name, Emoji und Datum anlegen, bearbeiten, löschen
- Vorlagen: Urlaub, 1. Advent, Weihnachten, Silvester, Winter-/Sommerreifen, Geburtstag
- Wiederholung: einmalig, jährlich, automatisch 1. Advent, automatisch Ostern
- Eigene Kategorien (Name + Emoji) anlegen, zuweisen und danach filtern
- Pro Event ein Ort (Suche und Anzeige in Google Maps) und ein Hintergrundbild, auch in Vorlagen speicherbar
- Lokale Speicherung, sortiert nach dem nächsten Termin
- Tägliche Erinnerung um 9 Uhr (7 Tage, 1 Tag und am Tag selbst)
- Homescreen-Widget mit dem nächsten Countdown

## Bauen
Mit Android Studio öffnen und starten, oder `gradle :app:assembleDebug`
(Android SDK nötig). Bei jedem Push baut GitHub Actions die Debug-APK.

## Online-Bildsuche (Pexels)
Die Bildsuche nutzt die [Pexels-API](https://www.pexels.com/api/). Den kostenlosen Key
trägst du lokal in `local.properties` ein (steht nicht im Repository):

    PEXELS_API_KEY=dein_key

Für den GitHub-Actions-Build legst du ihn als Repository-Secret `PEXELS_API_KEY` an.
Ohne Key funktioniert die App normal, nur die Suche zeigt einen Hinweis.
Achtung: Der Key steckt in der gebauten APK.

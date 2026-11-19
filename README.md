# rittau Karte

Android-App, die deine Visitenkarte per NFC an andere Handys überträgt – im rittau Corporate Design.

Dein Handy tut dabei so, als wäre es ein NFC-Tag (NFC Forum Type 4, Host Card Emulation). Das andere Handy braucht **keine App**: Es liest die Karte wie einen NFC-Sticker.

| Modus | Android empfängt | iPhone empfängt |
|---|---|---|
| **Kontakt** (vCard) | „Kontakt speichern“ | nichts Sichtbares |
| **Link** | öffnet die Website | öffnet die Website (ab iPhone XS) |

## Download

**Immer die neueste Version:** https://github.com/UschiMarcel/rittau-karte/releases/latest/download/rittau-karte.apk

Auf dem Handy öffnen, installieren („Installation aus unbekannten Quellen“ für den Browser erlauben).

## Updates

1. Änderung am Code machen und auf `main` pushen.
2. GitHub Actions baut automatisch eine neue Version (Versionsnummer zählt hoch) und veröffentlicht sie als Release.
3. In der App unter **04/ Update** auf „Nach Update suchen“ tippen – das öffnet die neueste Version im Browser. Ist die Nummer höher als die installierte, `rittau-karte.apk` laden und über die alte installieren, deine Daten bleiben.

Die App selbst geht nicht ins Internet (keine Internet-Berechtigung) – das macht sie für Play Protect unverdächtiger.

Wichtig: Jede Version wird mit demselben Schlüssel signiert. Er liegt **nicht** im Repo, sondern als GitHub-Secret (`KEYSTORE_BASE64` + `KEYSTORE_PASSWORD`, unter Settings → Secrets and variables → Actions). Bewahre eine Kopie sicher auf (z. B. im Passwort-Manager) – geht er verloren, lässt sich die App nicht mehr updaten, nur neu installieren. Der Build prüft den Fingerabdruck (`CERT_SHA256` im Workflow).

## Lokal bauen

Android Studio → Ordner öffnen → Run. Oder `gradle assembleRelease` (Gradle 8.7+, JDK 17).

## Benutzen

- App öffnen, Bildschirm an und entsperrt lassen.
- Handys Rücken an Rücken halten, 1–2 Sekunden.
- Unter **03/ Deine Daten** Name, E-Mail, Telefon usw. eintragen.

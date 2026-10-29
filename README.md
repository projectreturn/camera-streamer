# camera streamer 4.3 für Android TV / Google TV

Native Kamera-App für Android TV / Google TV mit Live-Raster, Vollbildplayer, Kamera-Verwaltung, Fotos und lokalen Videoaufnahmen. Android 6.0 oder neuer wird benötigt. Keine Cloud-Anmeldung erforderlich.

Die Aufnahmegalerie ist in [Bedienung 4.2](docs/Bedienung-camera-streamer-4.2.md) beschrieben. Die übrige Bedienung und die Funktionsgrenzen stehen in [Bedienung 4.1](docs/Bedienung-camera-streamer-4.1.md).

## Installation und erster Start

Die APK auf dem Android-TV-Gerät installieren, beispielsweise mit adbLink. Beim ersten Start das lokale Netz nach Kameras durchsuchen oder eine Kamera manuell ergänzen. Anschließend unter Einstellungen → Kameras den jeweiligen Kamera-Account hinterlegen. Bei Tapo ist hierfür der lokale Kamera-Account erforderlich, nicht das Cloud-Konto.

RTSP-Pfade sind beispielsweise `rtsp://IP:554/stream1` und `rtsp://IP:554/stream2`. Zugangsdaten ausschließlich in der App eingeben; keine persönlichen Kamera-URLs oder Sicherungen in Git ablegen.

Die bereitgestellte APK ist ein Debug-/Testbuild. Selbst gebaute APKs können einen anderen Signaturschlüssel haben und dann eine vorhandene Installation nicht aktualisieren. Vor einer gegebenenfalls erforderlichen Neuinstallation eine verschlüsselte Sicherung erstellen; Deinstallation entfernt App-Daten.

## Neu in 4.3

Tonumwandlung bei Originalaufnahmen: automatisch AAC übernehmen und andere unterstützte Formate nach AAC umwandeln, immer nach AAC umwandeln oder ohne Ton. H.264/H.265-Video wird unverändert gespeichert. G.711 A-Law/µ-Law und PCM werden direkt unterstützt; weitere komprimierte Formate nutzen Receiver-Decoder. Mono und Stereo, begrenzte Warteschlangen und explizite Fehlerhinweise. Details: [Bedienung 4.3](docs/Bedienung-camera-streamer-4.3.md).

## Neu in 4.2

- TV-Kachel ohne kleinen Zusatztext.
- „Aufnahmen“ als Galerie mit auswählbaren Foto-/Video-Vorschaubildern, asynchroner Dekodierung und begrenztem Bildcache.
- Eigener Vollbild-Medienplayer mit Iconleiste, Play/Pause, Zeitleiste, 10-Sekunden-Spulen, Bildwechsel, Ton und Zoom/Pan über P+/P−, OK und Pfeiltasten.
- Bestätigtes Löschen per langem OK in der Galerie; keine Teilen-Funktion.

## Neu in 4.1

- Fünf fernbedienbare Einstellungsreiter: Kameras, Ansicht & Ton, Bedienung, Aufnahmen, System.
- Rasterton: stumm, Fokus folgen oder feste sichtbare Kamera. Höchstens ein Stream ist hörbar; unsichtbare Kameras werden nicht nur für Ton geöffnet.
- Lautsprecher-Direktzugriff in der Hauptansicht.
- Explizite, zeitlich begrenzte Browser-Übertragung verschlüsselter Sicherungen im Heimnetz, ohne TV-Dateimanager. Das Passwort verlässt den Receiver nicht. Für USB bleibt die optionale Dokumentenauswahl vorgesehen.
- Gerätetests für alle Reiter, exklusive Rasterton-Wiedergabe, unsichtbare Tonkameras und verschlüsselte Browser-Dateiübertragung.

## Stand 4.0

- Kompaktes Live-Raster mit 1, 2 horizontal/vertikal, 4, 6, 9 oder 16 Kameras, horizontalen Seiten und optionalem automatischem Wechsel.
- Eigene Ansichten mit Kameraauswahl und Raster; Start in letzter Ansicht, Favoriten oder einer bestimmten Kamera; optionaler Vollbild-Kamerawechsel.
- Kompakte Kameraverwaltung, dauerhafte Reihenfolge per langem OK auf Bearbeiten, verschlüsselte Konten und URLs pro Kamera.
- RTSP, ONVIF-Profilimport und Scan, HLS sowie MJPEG. Haupt-/Nebenstream und Bildformat pro Kamera konfigurierbar.
- Sauberes Vollbild; OK-Bedienleiste mit fokusspezifischen Hinweisen. Unabhängige i-Streamdaten, Farbtasten, Lautstärke/Stummschaltung und PTZ nur bei passender Kamera.
- Digitalzoom bis 5×, Verschieben per Pfeiltasten, langes OK speichert Startposition; zusätzlich mehrere benannte Bildpositionen.
- Gestaffelte Wiederverbindung je Kamera und Überwachung stehender Bilder, verständliche Fehlerkategorien.
- Original-MP4-Aufnahme ohne erneute Videokompression (H.264/H.265, AAC falls vorhanden), alternativ Vorschau 640×360/5fps ohne Ton. Dauerlimit 5–60 Minuten. Kein Hintergrundrekorder.
- Medienbibliothek, Fotos, Download-Export; freier Speicher, optionales Sammlungslimit und ausdrücklich bestätigte automatische Altmedienlöschung.
- Passwortverschlüsselte portable Sicherung (AES-256-GCM, PBKDF2-HMAC-SHA256/210000), optional einschließlich Konten. Direktes Speichern auf Receiver/Downloads; externe Dateien über Dokumentenauswahl. Wiederherstellung nach Bestätigung, Kameras werden über ID zusammengeführt.
- Eigenes adaptives App-Icon, monochrome Variante und Android-TV-Banner. Paketkennung unverändert: de.local.tapoviewer.

## Sicherheit und Grenzen

Konten und vollständige Stream-URLs sind durch Android Keystore/AES-GCM geschützt. Sicherungspasswörter werden nicht gespeichert. Tonumwandlung erfordert eine verwendbare Mono-/Stereo-Quelle und gegebenenfalls einen passenden Receiver-Decoder; Kameras ohne Tonspur und MJPEG-Vorschauen bleiben stumm. Qualität und Auflösung bestimmt die Kamera; Hauptstream ist keine feste Auflösungszusage. Die Hardware begrenzt gleichzeitige Decoder. Originalaufnahme folgt dem empfangenen Stream, nicht dem digitalen Zoom. Kameraseitig eingebrannte Zeitstempel bleiben sichtbar. Nach Wiederherstellung ist automatische Löschung deaktiviert.

## Bauen

Java 17 und Android SDK 36 werden benötigt. Gradle-Wrapper ist enthalten.

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest assembleDebugAndroidTest
```

Die APK liegt unter app/build/outputs/apk/debug/app-debug.apk. Gerätetests setzen einen verbundenen Android-TV-Receiver voraus; Live-Tests verwenden konfigurierte Kameras, erstellen eindeutig markierte Testmedien und entfernen nur diese Testmedien. Datenmodelltests verwenden isolierte Präferenzen.

Für lokale SDK-Pfade eine nicht versionierte `local.properties` mit `sdk.dir` verwenden oder `ANDROID_HOME` setzen. Produktive Signaturschlüssel sind nicht Bestandteil dieses Projekts. Die Testmedien in `app/src/androidTest/assets` sind synthetisch erzeugt.

## Prüfstand und Einschränkungen

Version 4.3 wurde gebaut und mit Lint, 13 lokalen Tests sowie zehn gezielten Gerätetests für Tonumwandlung und Player-Steuerung geprüft. Ein durchgehender 60-Minuten-Aufnahmetest steht noch aus. Weitere Receiver und Kameramodelle können sich hinsichtlich Codec-Unterstützung und Fernbedienung unterscheiden. Gerätetests nur auf einem dafür vorgesehenen Gerät ausführen.

## Lizenz

Für den eigenen Anwendungscode wurde bislang keine Open-Source-Lizenz festgelegt. Abhängigkeiten unterliegen ihren jeweiligen Lizenzen.

## Fernbedienung

Rot Aufnahme, Gelb Foto, Blau Haupt-/Nebenstream, Grün Medien, i technische Daten, OK Bedienleiste. P+/P− zoomt, Pfeile verschieben, langes OK speichert den Ausschnitt, Zurück stellt Normalzoom wieder her. Langes OK auf dem Rasterbutton öffnet gespeicherte Ansichten.

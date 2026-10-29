# camera streamer 4.1

## Neue Einstellungsreiter

Die Einstellungen sind jetzt in fünf Bereiche aufgeteilt. Links/Rechts wählt einen Reiter, nach unten geht es zu dessen Einstellungen. Ein ausgewählter Reiter bleibt markiert; längere Bereiche lassen sich nach unten scrollen.

| Reiter | Inhalt |
|---|---|
| Kameras | Kamera hinzufügen und scannen, Kameras verwalten, gespeicherte Ansichten |
| Ansicht & Ton | Raster, Startansicht, Tonkamera, automatische Seiten- und Vollbildwechsel |
| Bedienung | Button-Hinweise, Anzeigedauer, Tastenbelegung, Zoom und Positionen |
| Aufnahmen | Medienbibliothek, Original-/Vorschauaufnahme, Dauerlimit, Speicher und Altmedienlöschung |
| System | Wiederverbindung, Bildschirm-Wachhalten, Sicherung und App-Version |

Account, Haupt-/Nebenstream, Bildformat und Kameralautstärke bleiben bei der jeweiligen Kamera unter „Kameras verwalten“. Langes OK auf Bearbeiten verschiebt die Kamera in der Reihenfolge. Langes OK auf dem Rasterbutton öffnet gespeicherte Ansichten.

## Ton im Kameraraster

Der neue Lautsprecher oben in der Hauptansicht und Einstellungen → Ansicht & Ton → Tonkamera bieten dieselben Modi:

- **Stumm:** Alle Rasterstreams bleiben lautlos; dies ist die Voreinstellung.
- **Ton folgt der markierten Kamera:** Die mit der Fernbedienung markierte Kamera ist hörbar. Bei einem Wechsel des Kamerafokus wird die vorherige Kamera zuerst stummgeschaltet.
- **Nur eine bestimmte Kamera:** Ausschließlich diese Kamera ist hörbar, sofern sie auf der gerade sichtbaren Rasterseite liegt. Beim Wegblättern wird der Ton stumm. Es wird kein versteckter Stream nur für Ton gestartet.

Es ist höchstens ein Rasterstream hörbar. Die Lautstärke stammt aus den Einstellungen der jeweiligen Kamera. Eine Kamera ohne gesendete Tonspur bleibt lautlos; MJPEG besitzt keine Tonspur und wird nicht als feste Tonkamera angeboten. Beim Öffnen des Vollbilds endet der Rasterton; dort gelten die separaten Toneinstellungen der geöffneten Kamera.

## Sicherungen ohne TV-Dateimanager übertragen

Unter System → Sicherung & Wiederherstellung ist neben den bisherigen Receiver-Sicherungen eine Übertragung über das Heimnetz verfügbar:

1. Am Fernseher das Sicherungspasswort eingeben (mindestens zehn Zeichen).
2. Zum Export „Sicherung auf Handy / PC herunterladen“ wählen. Optional vorher Kamera-Zugangsdaten einschließen.
3. Die angezeigte Adresse im Browser eines Handys oder PCs im selben Heimnetz öffnen und die verschlüsselte .csb-Datei herunterladen.
4. Zum Import „Sicherung vom Handy / PC übertragen“ wählen, die angezeigte Adresse im Browser öffnen und eine .csb-Datei senden.
5. Nach Passwort- und Dateiprüfung die Wiederherstellung ausdrücklich am Fernseher bestätigen.

Die Verbindung startet ausschließlich auf Knopfdruck, besitzt eine zufällige Sitzungsadresse und endet beim Schließen des Dialogs, Verlassen der Sicherungsseite oder spätestens nach zehn Minuten. Uploads sind auf vier Megabyte begrenzt. Das Passwort wird nicht im Browser eingegeben oder übertragen. Die Verbindung selbst verwendet lokales HTTP; die Sicherungsdatei ist bereits mit AES-256-GCM passwortverschlüsselt. Nur in einem vertrauenswürdigen Heimnetz benutzen.

Für diesen Browserweg wird kein TV-Dateimanager benötigt. Zum direkten Auswählen einer Datei auf USB oder in anderen Dokumentenquellen bleibt alternativ die Android-Dokumentenauswahl verfügbar, sofern eine passende Dateimanager-App installiert ist. Receiver-Sicherung und Download-Kopie sind weiterhin möglich.

## Aufnahmen und verbleibende Geräteabhängigkeiten

Originalaufnahmen speichern das empfangene H.264-/H.265-Video und vorhandenen unterstützten AAC-Ton ohne erneute Videokompression. Andere Audiocodecs werden nicht umgewandelt. Für MJPEG bleibt die Vorschauaufnahme ohne Ton vorgesehen. Das Dauerlimit ist auf 5, 15, 30 oder 60 Minuten einstellbar. Die Aufnahme endet beim Verlassen des Players oder Neuverbinden. Dies ist kein Hintergrund-Dauerrekorder.

PTZ wird nur bei einem geeigneten ONVIF-Kameraprofil angeboten. Die Zahl gleichzeitig flüssiger Streams und die Unterstützung bestimmter Codecs hängen vom Receiver ab. Solche Hardwaregrenzen werden nicht durch eine Einstellung aufgehoben.

Die übrigen Funktionen aus Version 4.0 bleiben erhalten: sauberes Vollbild, Farbtasten, technische i-Ansicht, gespeicherte und benannte Zoompositionen, eigene Ansichten, Kamera-Reihenfolge, Speichergrenzen und verschlüsselte Accounts. Automatische Medienlöschung ist weiterhin standardmäßig aus und benötigt eine ausdrückliche Bestätigung.

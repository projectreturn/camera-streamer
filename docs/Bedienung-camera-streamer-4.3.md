# camera streamer 4.3 – Tonumwandlung bei Aufnahmen

Unter Einstellungen → Aufnahmen → Ton bei Originalaufnahmen gibt es drei Modi:

| Modus | Verhalten |
|---|---|
| Automatisch | Vorhandenes AAC wird unverändert übernommen. Andere unterstützte Tonspuren werden nach AAC umgewandelt. |
| Ton immer nach AAC umwandeln | Auch vorhandenes AAC wird dekodiert und neu als AAC gespeichert. |
| Ohne Ton | Die Originalaufnahme enthält nur das Video. Die Live-Wiedergabe wird dadurch nicht stummgeschaltet. |

Standard ist „Automatisch“. Die Tonumwandlung verändert das H.264-/H.265-Video nicht. Eine Stummschaltung des Livebilds verhindert die Tonaufnahme nicht; entscheidend ist die oben gewählte Aufnahmeeinstellung.

## Unterstützte Tonquellen

- G.711 A-Law und µ-Law werden direkt nach PCM und anschließend nach AAC umgewandelt.
- PCM mit 8, 16, 24 oder 32 Bit sowie Float-PCM wird nach AAC umgewandelt.
- Für weitere komprimierte Formate verwendet die App die installierten Receiver-Decoder. MP3 und AAC wurden als solche Quellen geprüft.
- Unterstützt werden Mono und Stereo mit bekannter Abtastrate. AAC-Ausgabe erfolgt als AAC-LC in derselben MP4-Datei wie das Originalvideo.

Tonspuren werden anhand ihrer Zeitstempel zum Video ausgerichtet. AAC-Encoder-Vorlauf vor den ersten wirklichen PCM-Daten wird nicht als zusätzliche Aufnahme gespeichert. Die Datenwarteschlangen sind begrenzt; ein zu langsamer Speicher oder Encoder führt zu einer sichtbaren Fehlermeldung.

## Grenzen und Fehlerbehandlung

Eine Kamera muss tatsächlich eine verwendbare Tonspur senden. Fehlender Kameraton wird nicht künstlich erzeugt. Bei nicht unterstütztem Decoder, Kanalformat oder Encoder erscheint eine Meldung. Soweit eine gültige Videodatei abgeschlossen werden kann, bleibt sie mit einem deutlichen Hinweis erhalten; ansonsten wird nur die fehlgeschlagene neue Aufnahme verworfen. Bereits gespeicherte Dateien bleiben unangetastet.

Die Vorschauaufnahme, insbesondere bei MJPEG, bleibt ohne Ton. Die Umwandlung läuft nur während der Originalaufnahme und endet beim Stoppen beziehungsweise Verlassen des Players. Es handelt sich nicht um einen Hintergrund-Dauerrekorder.

Die Galerie mit Vorschaubildern, die große Foto-/Videoansicht und die neue TV-Kachel aus Version 4.2 bleiben unverändert.

# MarinefordBuilder

Paper-Plugin (26.2) mit eingebautem Webserver (Port 8200), BlueMap-Ebene, Bauplan, Layer-Anleitung,
3D-Ansicht, Materialliste, Fortschritts-Scan und Team-To-dos.

## Einrichten
1. Repo pushen, GitHub Actions baut die JAR (Artifact "MarinefordBuilder").
2. JAR nach plugins/, Server neu starten, http://localhost:8200 oeffnen.
3. Im Spiel an die Mitte der Plaza stellen und `/marineford anchor` eingeben (oder in der Webseite unter Einstellungen).

## Befehle (Permission marineford.admin)
- /marineford anchor  - Anker auf deine Position setzen, Scan startet
- /marineford scan    - Fortschritt neu einlesen
- /marineford reload  - plan.json neu laden
- /marineford         - Status

## Plan anpassen
plugins/MarinefordBuilder/plan.json: Bauteile (box, cylinder, dome) mit Position relativ zum Anker.
Nach Aenderungen `/marineford reload`.

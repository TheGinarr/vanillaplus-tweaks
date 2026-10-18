# Änderungen

## 3.1.0

- **Neu: Befehlssperre** (`command-block`, standardmäßig aus). Spieler ohne OP dürfen nur die Befehle, die in der `config.yml` je Befehl auf `true` stehen; alle anderen werden abgelehnt und aus der Tab-Vervollständigung entfernt. Befehle mit Namensraum (`/minecraft:msg`) zählen wie ohne. Ausnahme: OPs und Recht `vptweaks.commands.bypass`. Mit `/vptweaks reload` sofort wirksam.

## 3.0.2

- **Neu: `/afk`** für alle Spieler (Recht `vptweaks.afk`, standardmäßig erlaubt). Markiert sofort als AFK, zusätzlich zur automatischen Erkennung. Nochmal `/afk` oder Bewegen beendet es. Im Chat erscheint „… ist jetzt AFK“ bzw. „… ist wieder da“ (abschaltbar mit `afk.command-announce`).

## 3.0.1

- **AFK-Anzeige:** Wer AFK ist, steht im Tab jetzt mit durchgestrichenem Namen und dem Zusatz „AFK“. Das Aussehen lässt sich mit `afk.tab-name` einstellen. Alte AFK-Teams (vom Datapack „AFK Display“ und älteren Versionen) werden beim Start entfernt, damit niemand grau „hängen bleibt“.
- **Mehrspieler-Schlaf:** Die Chat-Meldung zeigt jetzt, ob die Nacht übersprungen wird oder wie viele Spieler noch schlafen müssen. Gerechnet wird wie in Minecraft selbst.

## 3.0.0

- Erste öffentliche Version.

## Vorgängerversionen 1.x und 2.x

Vor 3.0.0 lief das Plugin unter einem anderen Namen nur auf einem privaten Server. Diese Versionen werden **nicht veröffentlicht**: Sie enthalten Daten aus Datapacks, deren Lizenz eine Weitergabe nicht erlaubt (MasterCutter: CC-BY-ND-4.0, Gardener's Dream: All Rights Reserved), und in 1.4.0 und 2.0.x stecken Vanilla-Tweaks-Datapacks unverändert in der Jar.

- **2.1.0 – 2.2.0:** Alle Datapacks sind Plugin-Code, es steckt kein Datapack mehr in der Jar. Neu: Gardener's Dream als Plugin-Code, Tier- und Spielerköpfe, Steinsägen-Rezepte, Bauen & Deko (Rahmen, Rüstungsständer, Blöcke drehen, Doppeltüren, Dorfbewohner an der Leine), Wegsteine und Todespunkt.
- **2.0.x:** Datapacks mit Befehlsschleifen sind Plugin-Code. Nur reine Daten-Packs (Beute-Tabellen, Rezepte) stecken noch in der Jar.
- **1.4.0:** Ein Plugin für alles. Die Gameplay-Datapacks stecken unverändert in der Jar und werden automatisch geladen.
- **1.3.0:** Erste Module als Plugin-Code: AFK-Anzeige, Mehrspieler-Schlaf, schneller Laubzerfall, Rechtsklick-Ernte, Trampelschutz, Spender pflanzen, mehr Knochenmehl, Dorfbewohner folgen.
- **1.2.0 und früher:** Zeitzone, Elytra-Abklingzeit, Nether-/End-Termine, Platzhalter für die Tab-Liste.

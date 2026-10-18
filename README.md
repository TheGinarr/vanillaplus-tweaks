# VanillaPlusTweaks

![Paper 26.3](https://img.shields.io/badge/Paper-26.3-blue) ![Java 25](https://img.shields.io/badge/Java-25-orange) ![Version 3.0.2](https://img.shields.io/badge/Version-3.0.2-brightgreen)

Paper-Plugin für **Minecraft 26.3**, das beliebte „Vanilla+“-Datapacks in einem Plugin vereint. Statt jeden Tick Befehle auszuführen, reagiert es nur auf Ereignisse im Spiel und spart so Rechenzeit. Jede Funktion ist ein eigenes Modul und lässt sich in der `config.yml` einzeln einstellen oder abschalten.

## Download

**[VanillaPlusTweaks-3.0.2.jar](download/VanillaPlusTweaks-3.0.2.jar)** (auf der Seite rechts oben auf „Download raw file“ klicken)

## Voraussetzungen

| | |
|---|---|
| **Server** | Paper 26.3 (getestet mit Build 202). Spigot, Bukkit und andere Minecraft-Versionen werden nicht unterstützt. |
| **Java** | Java 25 (wird bereits von Paper 26.3 vorausgesetzt) |
| **Abhängigkeiten** | keine. [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) ist optional und nur für die Platzhalter nötig. |
| **Datapacks** | Die Datapacks, die das Plugin ersetzt (siehe [Credits](#credits)), vorher entfernen, sonst laufen Funktionen doppelt. |

## Installation

1. `VanillaPlusTweaks-3.0.2.jar` in den Ordner `plugins` des Servers legen.
2. Den Server starten. Dabei entsteht `plugins/VanillaPlusTweaks/config.yml`.
3. Die Einstellungen nach Bedarf anpassen und den Server neu starten.

Im Auslieferungszustand sind alle Module aktiv, Nether und End sind offen und die Weltgrenze ist aus.

## Funktionen

| Bereich | Module |
|---|---|
| **Allgemein** | Zeitzone, Nether/End erst ab festem Termin, Abklingzeit für Elytra-Raketen, Weltgrenze je Dimension |
| **Datapack-Ersatz** | AFK-Anzeige, Mehrspieler-Schlaf, schneller Laubzerfall, Beton und Schlamm im Wasserkessel, Tiere stumm schalten, alle Rezepte freischalten, leere Boote entfernen, XP in Flaschen, Netherportale in beliebiger Form, Sand von Wüstenzombies, gepanzerte Elytra, Steinsägen-Rezepte, klassische Angel-Schätze, knospender Amethyst mit Behutsamkeit, Tier- und Spielerköpfe |
| **Farmen** | Rechtsklick-Ernte, Schutz vor Zertrampeln, Spender pflanzen, mehr Knochenmehl, Dorfbewohner folgen dem Smaragdblock |
| **Bauen & Deko** | unsichtbare und gesperrte Rahmen, Rüstungsständer-Posen, Blöcke drehen, Doppeltüren, Dorfbewohner an der Leine |
| **Orientierung** | Wegsteine (Leitstein-Kompass, Reichweite über eine Leuchtfeuer-Pyramide), Todespunkt im Chat |

## Befehle

| Befehl | Recht | Wirkung |
|---|---|---|
| `/afk` | `vptweaks.afk` (alle) | sich selbst sofort als AFK melden, nochmal `/afk` oder bewegen: zurück |
| `/vptweaks status` | `vptweaks.admin` (OP) | aktive Module und Freischalt-Termine anzeigen |
| `/vptweaks reload` | `vptweaks.admin` (OP) | Termine, Elytra-Abklingzeit und Weltgrenze neu laden |
| `/vptweaks boote` | `vptweaks.admin` (OP) | leere Boote sofort entfernen |

Weitere Rechte (standardmäßig niemand): `vptweaks.elytra.bypass` (keine Elytra-Abklingzeit), `vptweaks.dimension.bypass` (gesperrte Nether-/End-Portale benutzen).

## Platzhalter

Mit PlaceholderAPI, z. B. für TAB oder Scoreboards:

| Platzhalter | Inhalt |
|---|---|
| `%vptweaks_cpu%` | CPU-Last des Servers (100 = ein voller Kern) |
| `%vptweaks_deaths%` | Anzahl Tode des Spielers |
| `%vptweaks_nether%`, `%vptweaks_end%` | Freischalt-Termin, z. B. „ab 24.10. um 20:00 Uhr“, oder „offen“ |

## Datenschutz

- Das Plugin baut **keine Verbindungen ins Internet** auf und sammelt keine Nutzungsstatistiken (keine Telemetrie, kein bStats).
- Es verarbeitet nur, was Minecraft ohnehin auf dem Server hat: Spielername, Position und die Tode-Statistik. Das geschieht im Arbeitsspeicher des Servers, z. B. für die AFK-Erkennung.
- Gespeichert wird nur in der Spielwelt (an Köpfen und Topfpflanzen). In die Server-Logs schreibt das Plugin keine Spielerdaten.
- Sichtbar für andere Spieler: die AFK-Markierung im Tab und die Schlaf-Meldung im Chat. Die Todeskoordinaten sieht nur der gestorbene Spieler selbst.

Wer das Plugin auf einem eigenen Server betreibt, ist für den Datenschutz auf diesem Server selbst verantwortlich.

## Selbst bauen

Benötigt JDK 21+ und einen einmal gestarteten Paper-26.3-Server mit PlaceholderAPI im `plugins`-Ordner:

```sh
./bauen.sh /pfad/zum/paper-server
```

Die Jar liegt danach unter `build/VanillaPlusTweaks.jar`. Das optionale Pflanzen-Modul (`gardeners-dream`) und die MasterCutter-Rezepte brauchen Daten aus den Original-Packs, die aus Lizenzgründen nicht enthalten sind. Die Skripte in `konverter/` erzeugen sie lokal für den eigenen Server. Eine so gebaute Jar darf nicht weitergegeben werden.

## Versionen

Alle Versionen mit Änderungen und Download stehen unter [Releases](../../releases), die Übersicht in [`CHANGELOG.md`](CHANGELOG.md).

## Credits

```
Credits:
Vanilla Tweaks: https://vanillatweaks.net/
```

- **[Vanilla Tweaks](https://vanillatweaks.net/)**: Vorbild für die nachgebauten Datapacks (AFK Display, Multiplayer Sleep, Fast Leaf Decay, Cauldron Concrete, Cauldron Mud, Silence Mobs, Unlock All Recipes, Kill Empty Boats, XP Bottling, Custom Nether Portals, Husks Drop Sand, Armored Elytra, Classic Fishing Loot, More Mob Heads, Painting Picker, Player Head Drops, Silk Touch Budding Amethyst, Wood Stripper). Kopf-Texturen sowie die Painting-Picker- und Wood-Stripper-Rezepte stammen aus diesen Packs.
- **[Gardener's Dream](https://modrinth.com/datapack/gardeners-dream)** von Gurkis: Vorbild für das Pflanzen-Modul. Daten nicht enthalten.
- **[MasterCutter](https://modrinth.com/project/DuUMFIfX)** von nico4play: Vorbild für die zusätzlichen Steinsägen-Rezepte. Daten nicht enthalten.

Siehe auch [`res/credits.txt`](res/credits.txt). VanillaPlusTweaks ist kostenlos und wird nicht verkauft.

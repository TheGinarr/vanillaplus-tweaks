#!/usr/bin/env python3
"""
Sammelt alle Steinsaegen-Rezepte aus den Datapacks MasterCutter, Painting Picker, Wood Stripper
und Gardener's Dream in einer lesbaren Liste fuer das Plugin (rezepte.yml).

Jedes Rezept:
  name:      eindeutiger Name (Pack/Pfad der Rezeptdatei)
  zutat:     Liste von Items oder ein Block-/Item-Tag (#minecraft:...)
  ergebnis:  Item-Text wie im /give-Befehl, z. B. minecraft:painting[minecraft:painting/variant="minecraft:wither"]
  anzahl:    wie viele Items herauskommen

Aufruf: rezepte_konvertieren.py <datapacks-ordner> <ziel.yml>
"""

import glob
import json
import os
import sys

import yaml

# Pack-Ordner im Plugin -> Kurzname fuer die Rezept-Namen
PACKS = {
    "mastercutter": "mastercutter",
    "painting_picker": "painting_picker",
    "wood_stripper": "wood_stripper",
    "gardeners_dream": "gardeners_dream",
}


def snbt(wert):
    if isinstance(wert, bool):
        return "true" if wert else "false"
    if isinstance(wert, (int, float)):
        return str(wert)
    if isinstance(wert, str):
        return json.dumps(wert, ensure_ascii=False)
    if isinstance(wert, list):
        return "[" + ",".join(snbt(x) for x in wert) + "]"
    if isinstance(wert, dict):
        return "{" + ",".join(json.dumps(k) + ":" + snbt(v) for k, v in wert.items()) + "}"
    raise TypeError(type(wert))


def ergebnis_text(ergebnis):
    """Gibt (item_text, anzahl) zurueck."""
    if isinstance(ergebnis, str):
        return ergebnis, 1

    item = ergebnis["id"]
    anzahl = ergebnis.get("count", 1)
    komponenten = ergebnis.get("components", {})
    if not komponenten:
        return item, anzahl

    teile = []
    for schluessel, wert in komponenten.items():
        if schluessel.startswith("!"):
            teile.append(schluessel)          # Komponente entfernen, z. B. !minecraft:equippable
        else:
            teile.append(schluessel + "=" + snbt(wert))
    return item + "[" + ",".join(teile) + "]", anzahl


def main(datapacks, ziel):
    rezepte = []

    for ordner, kurzname in PACKS.items():
        dateien = sorted(glob.glob(os.path.join(datapacks, ordner, "data", "*", "recipe", "**", "*.json"), recursive=True))
        for datei in dateien:
            daten = json.load(open(datei))
            assert daten["type"] == "minecraft:stonecutting", datei

            zutat = daten["ingredient"]
            if isinstance(zutat, str):
                zutat = [zutat]

            item, anzahl = ergebnis_text(daten["result"])
            pfad = os.path.relpath(datei, os.path.join(datapacks, ordner, "data"))
            name = kurzname + "/" + pfad.replace("/recipe/", "/")[:-5]

            rezepte.append({"name": name, "zutat": zutat, "ergebnis": item, "anzahl": anzahl})

    with open(ziel, "w", encoding="utf-8") as ausgabe:
        ausgabe.write("# Erzeugt mit rezepte_konvertieren.py aus MasterCutter, Painting Picker,\n")
        ausgabe.write("# Wood Stripper und Gardener's Dream. Alle Rezepte sind Steinsaegen-Rezepte.\n\n")
        yaml.safe_dump({"steinsaege": rezepte}, ausgabe, allow_unicode=True, sort_keys=False, width=100000)

    print(len(rezepte), "Rezepte")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

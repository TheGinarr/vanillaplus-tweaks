#!/usr/bin/env python3
"""
Erzeugt vanilla_tags.json fuer den Garten-Vergleichstest aus der Paper-Server-Jar.

Die Datei wird nicht mitgeliefert, weil sie Daten aus Minecraft enthaelt. Sie ist schnell erzeugt:
  python3 vanilla_tags_erzeugen.py <server>/versions/26.3/paper-26.3.jar vanilla_tags.json

Ausgabe: Tag -> Liste aller Eintraege (Item-Tag, sonst Block-Tag; verschachtelte Tags aufgeloest).
"""

import json
import sys
import zipfile

TAGS = ["leaves", "saplings", "flowers", "coral_plants", "coral_blocks", "corals", "wall_corals"]


def lade(jar, art, name, cache):
    schluessel = (art, name)
    if schluessel in cache:
        return cache[schluessel]
    pfad = f"data/minecraft/tags/{art}/{name}.json"
    if pfad not in jar.namelist():
        # Manche Tags (z. B. Korallen) gibt es nur als Block-Tag
        art = "block"
        pfad = f"data/minecraft/tags/block/{name}.json"
    werte = json.loads(jar.read(pfad))["values"]
    ergebnis = []
    for wert in werte:
        wert = wert if isinstance(wert, str) else wert["id"]
        if wert.startswith("#"):
            ergebnis += lade(jar, art, wert[1:].split(":", 1)[1], cache)
        else:
            ergebnis.append(wert)
    cache[schluessel] = ergebnis
    return ergebnis


def main(jar_pfad, ziel):
    with zipfile.ZipFile(jar_pfad) as jar:
        cache = {}
        ausgabe = {f"minecraft:{name}": lade(jar, "item", name, cache) for name in TAGS}
    with open(ziel, "w", encoding="utf-8") as datei:
        json.dump(ausgabe, datei, indent=1)
    print(f"{ziel}: {len(ausgabe)} Tags")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

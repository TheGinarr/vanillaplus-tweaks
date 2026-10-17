#!/usr/bin/env python3
"""
Liest aus Gardener's Dream die Pflanzen-Tabellen heraus (welches Item wie im Topf aussieht)
und schreibt sie lesbar nach gaertner.yml.

Uebernommen werden nur die DATEN pro Pflanze:
  - Block-Display: Blockzustand, Verschiebung, Groesse, Drehung, Art (normal/alt/hoch ...)
  - Sonderfaelle pro Pflanze auf Koepfen / Ackerland / Seerosen

Der ALLGEMEINE Ablauf (Hoehen fuer Koepfe, Wandkoepfe, Fass, Stufe ...) ist im Plugin
von Hand geschrieben und wird mit dem Vergleichstest gegen das Original geprueft.

Aufruf: gaertner_konvertieren.py <gardeners_dream/data> <ziel.yml>
"""

import json
import os
import re
import sys

import yaml

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "pruefung"))
from mcfunction_referenz import snbt, zerlege  # noqa: E402

# Topf-Groesse -> Funktionsdateien (Haupthand / Nebenhand)
DATEIEN = {
    "klein": ("spawnplant", "spawn_offhand/small"),
    "gross": ("spawnbigplant", "spawn_offhand/big"),
    "breit": ("spawnwideplant", "spawn_offhand/wide"),
}

GROESSEN_TAG = {"klein": "smallpp", "gross": "bigpp", "breit": "widepp"}

# Art-Markierungen im Datapack -> lesbarer Name
ARTEN = {"pp_init": "normal", "pp_init_alt": "alt", "pp_init_alt2": "alt2", "pp_init_high": "hoch", "pp_new": "neu"}


def zeilen(ordner, name):
    pfad = os.path.join(ordner, "gardenersdream", "function", name + ".mcfunction")
    for zeile in open(pfad, encoding="utf-8"):
        zeile = zeile.strip()
        if zeile and not zeile.startswith("#"):
            yield zeile


def zahlen(liste):
    return Flach(round(float(x), 6) for x in liste)


def drehung(wert):
    """Quaternion [x,y,z,w] oder Achse-Winkel {angle, axis} -> lesbar."""
    if isinstance(wert, list):
        return {"quaternion": zahlen(wert)}
    return {"winkel": round(float(wert["angle"]), 6), "achse": zahlen(wert["axis"])}


def display_daten(nbt, groesse):
    tags = list(nbt.get("Tags", []))
    assert "potplant" in tags and GROESSEN_TAG[groesse] in tags, tags

    art = [ARTEN[t] for t in tags if t in ARTEN]
    assert len(art) == 1, tags

    daten = {"art": art[0]}
    extra = [t for t in tags if t not in ARTEN and t not in ("potplant", GROESSEN_TAG[groesse])]
    if extra:
        daten["markierungen"] = extra

    block = nbt.get("block_state")
    if isinstance(block, str):
        daten["block"] = block
    elif isinstance(block, dict):
        eigenschaften = ",".join(f"{k}={v}" for k, v in block.get("properties", {}).items())
        daten["block"] = block["id"] + (f"[{eigenschaften}]" if eigenschaften else "")

    t = nbt["transformation"]
    daten["verschiebung"] = zahlen(t["translation"])
    daten["groesse"] = zahlen(t["scale"])
    daten["drehung_links"] = drehung(t["left_rotation"])
    daten["drehung_rechts"] = drehung(t["right_rotation"])
    return daten


SUMMON = re.compile(
    r'^execute align xyz positioned ~\.5 ~ ~\.5 '
    r'(?:if entity @s\[nbt=\{SelectedItem:\{id:"(?P<item>[a-z0-9_:]+)"\}\}\]|if items entity @s weapon\.offhand (?P<neben>[a-z0-9_:]+)) '
    r'run summon block_display ~ ~0\.24 ~ (?P<nbt>\{.*\})$')

SONDER_MERGE = re.compile(
    r'^execute align xyz positioned ~\.5 ~ ~\.5 if block ~ ~ ~ (?P<block>player_head|player_wall_head) '
    r'if entity @s\[nbt=\{SelectedItem:\{id:"(?P<item>[a-z0-9_:]+)"\}\}\] run execute as '
    r'@e\[type=block_display,tag=(?P<tag>\w+),distance=\.\.0\.8\] run data merge entity @s (?P<nbt>\{.*\})$')

SONDER_HOEHE = re.compile(
    r'^execute align xyz positioned ~\.5 ~ ~\.5 if block ~ ~ ~ (?P<block>player_head|player_wall_head|farmland|lily_pad) '
    r'if entity @s\[nbt=\{SelectedItem:\{id:"(?P<item>[a-z0-9_:]+)"\}\}\] run execute as '
    r'@e\[type=block_display,tag=(?P<tag>\w+)\] at @s run tp @s ~ ~(?P<dy>-?[\d.]+) ~$')


class Flach(list):
    """Zahlen-Listen (Verschiebung, Groesse, Drehung) in einer Zeile schreiben."""


yaml.SafeDumper.add_representer(Flach, lambda d, v: d.represent_sequence("tag:yaml.org,2002:seq", v, flow_style=True))


def main(ordner, ziel):
    ergebnis = {}
    uebrig = {}

    for groesse, (haupt, neben) in DATEIEN.items():
        tabelle = {"haupthand": {}, "nebenhand": {}, "sonderfaelle": []}

        for hand, datei in (("haupthand", haupt), ("nebenhand", neben)):
            for zeile in zeilen(ordner, datei):
                m = SUMMON.match(zeile)
                if m:
                    item = m.group("item") or m.group("neben")
                    nbt = snbt(m.group("nbt"))
                    tabelle[hand].setdefault(item, []).append(display_daten(nbt, groesse))
                    continue

                m = SONDER_MERGE.match(zeile)
                if m:
                    t = snbt(m.group("nbt"))["transformation"]
                    tabelle["sonderfaelle"].append({
                        "item": m.group("item"), "auf": m.group("block"), "art": ARTEN[m.group("tag")],
                        "verschiebung": zahlen(t["translation"]), "groesse": zahlen(t["scale"])})
                    continue

                m = SONDER_HOEHE.match(zeile)
                if m:
                    tabelle["sonderfaelle"].append({
                        "item": m.group("item"), "auf": m.group("block"), "art": ARTEN[m.group("tag")],
                        "hoehe": round(float(m.group("dy")), 6)})
                    continue

                uebrig.setdefault(datei, []).append(zeile)

        # Standard-Display, wenn fuer ein Item keine eigene Zeile existiert
        for zeile in zeilen(ordner, haupt):
            if "pp_init" in zeile and "unless items entity @s weapon.mainhand #gardenersdream:overlay_plants" in zeile and "summon" in zeile:
                nbt = snbt(zerlege(zeile)[-1])
                tabelle["standard"] = display_daten(nbt, groesse)
                if "pp_default_plant" in nbt["Tags"]:
                    tabelle["standard"].setdefault("markierungen", []).append("pp_default_plant")
                    tabelle["standard"]["markierungen"] = sorted(set(tabelle["standard"]["markierungen"]))

        ergebnis[groesse] = tabelle

    # Item-Listen (Tags) uebernehmen
    listen = {}
    for art in ("item", "block"):
        pfad = os.path.join(ordner, "gardenersdream", "tags", art)
        for datei in sorted(os.listdir(pfad)):
            werte = json.load(open(os.path.join(pfad, datei)))["values"]
            listen[art + "/" + datei[:-5]] = [w["id"] if isinstance(w, dict) else w for w in werte]

    with open(ziel, "w", encoding="utf-8") as ausgabe:
        ausgabe.write("# Erzeugt aus Gardener's Dream mit gaertner_konvertieren.py\n")
        ausgabe.write("# Pflanzen pro Topf-Groesse: klein (Blumentopf, Kopf), gross (verzierter Topf), breit (Kessel, Fass ...)\n")
        ausgabe.write("# verschiebung/groesse = Transformation des Block-Displays; art = Hoehen-Gruppe fuer Koepfe\n\n")
        yaml.safe_dump({"pflanzen": ergebnis, "listen": listen}, ausgabe, allow_unicode=True, sort_keys=False, width=200)

    for datei, rest in uebrig.items():
        print("##", datei, "- nicht als Pflanzen-Zeile erkannt (allgemeiner Ablauf):")
        for z in rest:
            print("   ", z[:200])
    for g, t in ergebnis.items():
        print(g, "Haupthand:", len(t["haupthand"]), "Nebenhand:", len(t["nebenhand"]), "Sonderfaelle:", len(t["sonderfaelle"]), "Standard:", "standard" in t)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

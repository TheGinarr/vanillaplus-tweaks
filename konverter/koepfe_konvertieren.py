#!/usr/bin/env python3
"""
Wandelt die Beute-Tabellen des Datapacks "More Mob Heads" (Vanilla Tweaks) in eine
lesbare Kopf-Liste fuer das Plugin um (koepfe.yml).

Pro Tier entsteht eine geordnete Liste von Varianten. Das Plugin nimmt die ERSTE Variante,
deren Bedingungen zum getoeteten Tier passen. So arbeitet auch das Datapack:
  - "alternatives": das erste passende Kind gewinnt
  - mehrere "set_components" an einem Item: das LETZTE passende gewinnt
    (deshalb wird diese Reihenfolge hier umgedreht)

Die Drop-Chancen des Datapacks werden NICHT uebernommen – die Chance steht in der config.yml.

Aufruf: koepfe_konvertieren.py <ordner more_mob_heads/loot_table/entities> <ziel.yml>
"""

import json
import os
import re
import sys

import yaml


# ----------------------------------------------------------------------
# Bedingungen aus dem Datapack in einfache Schluessel uebersetzen
# ----------------------------------------------------------------------

# Komponenten-Varianten: "minecraft:wolf/variant": "minecraft:ashen" -> variante: ashen
def komponenten_bedingung(komponenten):
    ergebnis = {}
    for schluessel, wert in komponenten.items():
        name = schluessel.replace("minecraft:", "")
        wert = str(wert).replace("minecraft:", "")
        if name.endswith("/variant"):
            ergebnis["variante"] = wert
        elif name == "sheep/color":
            ergebnis["farbe"] = wert
        else:
            raise ValueError("Unbekannte Komponente: " + schluessel)
    return ergebnis


# NBT-Bedingungen: nur die Formen, die im Datapack wirklich vorkommen
NBT_MUSTER = [
    (r"^\{anger_end_time:-1L\}$", lambda m: {"wuetend": False}),
    (r"^\{anger_end_time:-1L,HasNectar:([01])b\}$", lambda m: {"wuetend": False, "nektar": m.group(1) == "1"}),
    (r"^\{HasNectar:([01])b\}$", lambda m: {"nektar": m.group(1) == "1"}),
    (r"^\{IsScreamingGoat:([01])b\}$", lambda m: {"schreiend": m.group(1) == "1"}),
    (r"^\{powered:1b\}$", lambda m: {"aufgeladen": True}),
    (r"^\{MainGene:\"(\w+)\"\}$", lambda m: {"hauptgen": m.group(1)}),
    (r"^\{MainGene:\"(\w+)\",HiddenGene:\"(\w+)\"\}$", lambda m: {"hauptgen": m.group(1), "nebengen": m.group(2)}),
    (r"^\{weather_state: '(\w+)'\}$", lambda m: {"verwitterung": m.group(1)}),
    (r"^\{VillagerData:\{profession:\"minecraft:(\w+)\"\}\}$", lambda m: {"beruf": m.group(1)}),
    # Haendler-Lama: Variant 0..3 = creamy, white, brown, gray (Reihenfolge wie im Spiel)
    (r"^\{Variant:([0-3])\}$", lambda m: {"variante": ["creamy", "white", "brown", "gray"][int(m.group(1))]}),
]


def nbt_bedingung(nbt):
    for muster, umwandeln in NBT_MUSTER:
        treffer = re.match(muster, nbt)
        if treffer:
            return umwandeln(treffer)
    if "jeb_" in nbt:
        return {"name": "jeb_"}
    raise ValueError("Unbekannte NBT-Bedingung: " + nbt)


def bedingungen(bedingung):
    """Liefert (tier_bedingungen, hat_zufall). Spieler-Kill und Zufall bestimmt das Plugin selbst."""
    if bedingung is None:
        return {}, False

    art = bedingung["type"].replace("minecraft:", "")
    if art == "all_of":
        alles, zufall = {}, False
        for teil in bedingung["terms"]:
            b, z = bedingungen(teil)
            alles.update(b)
            zufall = zufall or z
        return alles, zufall
    if art == "any_of":
        # kommt nur beim jeb_-Schaf vor (zwei Schreibweisen des Namens)
        alle = [bedingungen(t)[0] for t in bedingung["terms"]]
        assert all(b == {"name": "jeb_"} for b in alle), alle
        return {"name": "jeb_"}, False
    if art == "killed_by_player":
        return {}, False
    if art in ("random_chance_with_enchanted_bonus", "random_chance"):
        return {}, True
    if art == "entity_properties":
        praedikat = dict(bedingung["predicate"])
        praedikat.pop("type_specific", None)
        ergebnis = {}
        if "components" in praedikat:
            ergebnis.update(komponenten_bedingung(praedikat.pop("components")))
        if "nbt" in praedikat:
            ergebnis.update(nbt_bedingung(praedikat.pop("nbt")))
        assert not praedikat, praedikat
        return ergebnis, False
    raise ValueError("Unbekannte Bedingung: " + art)


# ----------------------------------------------------------------------
# Kopf-Item als Item-Text (wie im /give-Befehl), z. B.
#   minecraft:player_head[minecraft:item_name="Cow Head",...]
# ----------------------------------------------------------------------

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


def item_text(komponenten):
    teile = [k + "=" + snbt(v) for k, v in komponenten.items()]
    return "minecraft:player_head[" + ",".join(teile) + "]"


# ----------------------------------------------------------------------
# Beute-Tabelle in Varianten zerlegen
# ----------------------------------------------------------------------

def varianten_aus_eintrag(eintrag, ordner, zusatz_bedingung):
    """Liefert eine geordnete Liste (bedingungen, item_text). Erste passende gewinnt."""
    art = eintrag["type"].replace("minecraft:", "")
    eigene, _ = bedingungen(eintrag.get("condition"))
    alle_bedingungen = {**zusatz_bedingung, **eigene}

    if art == "item":
        assert eintrag["name"] == "minecraft:player_head"
        modifikatoren = eintrag["modifier"]
        if isinstance(modifikatoren, dict):
            modifikatoren = [modifikatoren]

        basis = None
        bedingte = []
        for mod in modifikatoren:
            assert mod["type"].replace("minecraft:", "") == "set_components", mod
            if "condition" in mod:
                bedingte.append((bedingungen(mod["condition"])[0], mod["components"]))
            else:
                assert basis is None
                basis = mod["components"]

        ergebnis = []
        # Spaetere Modifikatoren ueberschreiben fruehere -> umgekehrt pruefen
        for b, komp in reversed(bedingte):
            ergebnis.append(({**alle_bedingungen, **b}, item_text(komp)))
        if basis is not None:
            ergebnis.append((alle_bedingungen, item_text(basis)))
        return ergebnis

    if art in ("alternatives", "group"):
        ergebnis = []
        for kind in eintrag["children"]:
            ergebnis.extend(varianten_aus_eintrag(kind, ordner, alle_bedingungen))
        return ergebnis

    if art == "loot_table":
        ziel = eintrag["value"]
        assert ziel.startswith("more_mob_heads:entities/"), ziel
        datei = os.path.join(ordner, ziel.split("entities/")[1] + ".json")
        return varianten_aus_tabelle(json.load(open(datei)), ordner, alle_bedingungen)

    raise ValueError("Unbekannter Eintrag: " + art)


def varianten_aus_tabelle(tabelle, ordner, zusatz_bedingung=None):
    zusatz_bedingung = zusatz_bedingung or {}
    pools = []
    for pool in tabelle["pools"]:
        pool_bedingung, zufall = bedingungen(pool.get("condition"))
        varianten = []
        for eintrag in pool["entries"]:
            varianten.extend(varianten_aus_eintrag(eintrag, ordner, {**zusatz_bedingung, **pool_bedingung}))
        pools.append((pool, varianten))
    # Mehrere Pools werden spaeter einzeln behandelt (Wither, Schaf)
    if len(pools) == 1:
        return pools[0][1]
    return [v for _, liste in pools for v in liste]


# ----------------------------------------------------------------------
# Hauptprogramm
# ----------------------------------------------------------------------

def main(ordner, ziel):
    koepfe = {}

    for datei in sorted(os.listdir(ordner)):
        if not datei.endswith(".json"):
            continue
        tier = datei[:-5]
        tabelle = json.load(open(os.path.join(ordner, datei)))

        if tier == "wither":
            # Pool 1: Wither-Kopf. Pool 2 (50 %): zusaetzlich einer der zwei Geschoss-Koepfe.
            haupt = varianten_aus_tabelle({"pools": [tabelle["pools"][0]]}, ordner)
            zusatz = varianten_aus_tabelle({"pools": [tabelle["pools"][1]]}, ordner)
            koepfe[tier] = {
                "varianten": [{"item": t} for _, t in haupt],
                "zusatz": {"chance": 0.5, "zufaellig-einer-von": [t for _, t in zusatz]},
            }
            continue

        if tier == "sheep":
            # Pool 1: jeb_-Schaf (nur mit Namen jeb_). Pool 2: Farbe der Wolle.
            jeb = varianten_aus_tabelle(json.load(open(os.path.join(ordner, "sheep", "jeb_sheep.json"))), ordner)
            farben = varianten_aus_tabelle({"pools": [tabelle["pools"][1]]}, ordner)
            alle = [({"name": "jeb_"}, jeb[0][1])] + farben
            koepfe[tier] = {"varianten": [dict(**({"wenn": b} if b else {}), item=t) for b, t in alle]}
            continue

        varianten = varianten_aus_tabelle(tabelle, ordner)
        koepfe[tier] = {"varianten": [dict(**({"wenn": b} if b else {}), item=t) for b, t in varianten]}

    with open(ziel, "w", encoding="utf-8") as ausgabe:
        ausgabe.write("# Erzeugt aus \"More Mob Heads\" (Vanilla Tweaks) mit koepfe_konvertieren.py\n")
        ausgabe.write("# Pro Tier: die erste Variante, deren 'wenn' passt, wird gedroppt.\n")
        ausgabe.write("# Die Drop-Chance steht in der config.yml (mob-heads).\n\n")
        yaml.safe_dump(koepfe, ausgabe, allow_unicode=True, sort_keys=False, width=100000)

    print(len(koepfe), "Tiere,", sum(len(k["varianten"]) for k in koepfe.values()), "Varianten")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

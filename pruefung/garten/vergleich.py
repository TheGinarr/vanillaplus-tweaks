#!/usr/bin/env python3
"""
Vergleicht die Garten-Logik des Plugins mit den Original-Funktionen von Gardener's Dream.

1. Erzeugt viele Testfaelle (Topf-Arten, Koepfe in allen Drehungen, Wandkoepfe, Items, vorhandene Pflanzen ...)
2. Fuehrt jeden Fall mit dem Nachbau der Original-.mcfunction-Dateien aus (mcfunction_referenz.py)
3. Fuehrt denselben Fall mit der Plugin-Logik aus (GartenVergleich.java)
4. Vergleicht: Anzeigen (Position, Markierungen, Block, Drehung, Transformation), verbrauchte Items, Drops

Aufruf: vergleich.py <gardeners_dream/data> <gaertner.yml> <vanilla_tags.json> <plugin-klassen>
"""

import itertools
import json
import math
import os
import random
import subprocess
import sys

import yaml

HIER = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HIER, ".."))
import mcfunction_referenz as ref  # noqa: E402

GD, GAERTNER_YML, TAGS, KLASSEN = sys.argv[1:5]
random.seed(4711)

FUNKTION = {
    "haupt": "gardenersdream:spawn_check",
    "neben": "gardenersdream:spawn_check_offhand",
    "drehen": "gardenersdream:plantrotate",
    "drehen_neben": "gardenersdream:plantrotate_offhand",
    "kaputt": "gardenersdream:pot_broken",
}


# ----------------------------------------------------------------------
# Testfaelle
# ----------------------------------------------------------------------

def topf_aufbauten():
    """(Bloecke relativ zum Fuss-Block, typische Fusshoehe im Block)"""
    a = []
    # kleine Toepfe: Fuesse im Topf-Block
    for b in ["flower_pot", "potted_poppy", "potted_cactus"]:
        a.append(({(0, 0, 0): b}, 0.375))
    for r in range(16):
        a.append(({(0, 0, 0): f"player_head[rotation={r}]"}, 0.5))
    for f in ["north", "south", "east", "west"]:
        a.append(({(0, 0, 0): f"player_wall_head[facing={f}]"}, 0.75))
    # grosse Toepfe
    a.append(({(0, -1, 0): "decorated_pot"}, 0.0))
    a.append(({(0, 0, 0): "decorated_pot"}, 0.0))
    a.append(({(0, -1, 0): "decorated_pot", (0, 1, 0): "stone"}, 0.0))
    # breite Toepfe
    for b in ["cauldron", "water_cauldron[level=3]", "composter[level=0]", "powder_snow_cauldron[level=1]"]:
        a.append(({(0, 0, 0): b}, 0.25))
        a.append(({(0, -1, 0): b}, 0.0))
    a.append(({(0, 0, 0): "mossy_cobblestone_slab[type=bottom]"}, 0.5))
    a.append(({(0, -1, 0): "mossy_cobblestone_slab[type=top]"}, 0.0))
    a.append(({(0, -1, 0): "barrel[facing=up,open=true]"}, 0.0))
    a.append(({(0, 0, 0): "farmland[moisture=7]"}, 0.9375))
    a.append(({(0, 0, 0): "lily_pad"}, 0.09375))
    a.append(({(0, -1, 0): "farmland[moisture=0]"}, 0.0))
    a.append(({(0, -1, 0): "decorated_pot", (0, -2, 0): "barrel[facing=up,open=true]"}, 0.0))
    # Mischfaelle: kleiner und breiter Topf gleichzeitig
    a.append(({(0, 0, 0): "flower_pot", (0, -1, 0): "cauldron"}, 0.375))
    a.append(({(0, 0, 0): "player_head[rotation=4]", (0, -1, 0): "decorated_pot"}, 0.5))
    # kein Topf
    a.append(({(0, -1, 0): "stone"}, 0.0))
    return a


def items_laden():
    daten = yaml.safe_load(open(GAERTNER_YML))
    tags = json.load(open(TAGS))

    def aufloesen(liste):
        erg = []
        for w in liste:
            if w.startswith("#"):
                erg += tags.get(w[1:], [])
            else:
                erg.append(w)
        return sorted(set(erg))

    return aufloesen(daten["listen"]["item/pot_plants"]), aufloesen(daten["listen"]["item/alt_plants"])


def vorhandene_varianten(p, gross):
    """Schon vorhandene Pflanzen im Topf (Position wie vom Datapack erzeugt)."""
    oben = [p[0], p[1] + 0.24, p[2]]
    v = [[]]
    for tag in ["smallpp", "bigpp", "widepp"]:
        v.append([{"pos": oben, "tags": [tag, "potplant"], "block": "minecraft:poppy", "yaw": 0.0}])
        v.append([{"pos": oben, "tags": [tag, "potplant", "pp_overlay"], "block": "minecraft:vine", "yaw": 0.0}])
    v.append([{"pos": oben, "tags": ["smallpp", "potplant", "pp_norotate"], "block": "minecraft:poppy", "yaw": 0.0}])
    v.append([{"pos": [p[0] + 0.85, p[1] + 0.24, p[2]], "tags": ["smallpp", "potplant"], "block": "minecraft:poppy", "yaw": 0.0}])
    return v


def faelle():
    topf_items, neben_items = items_laden()
    extra = ["minecraft:stone", None]
    ergebnis = []
    basis = (10, 64, 10)

    for bloecke, hoehe in topf_aufbauten():
        for dx, dz in [(0.5, 0.5), (0.2, 0.8), (0.95, 0.05)]:
            fuss = [basis[0] + dx, basis[1] + hoehe, basis[2] + dz]
            p = [basis[0] + 0.5, basis[1], basis[2] + 0.5]
            welt_bloecke = [[basis[0] + x, basis[1] + y, basis[2] + z, b] for (x, y, z), b in bloecke.items()]

            # Haupthand: alle Items ohne vorhandene Pflanzen, dazu Stichproben mit vorhandenen
            for item in topf_items + extra:
                ergebnis.append({"bloecke": welt_bloecke, "anzeigen": [], "aktion": "haupt",
                                 "spieler": {"pos": fuss, "haupt": item, "neben": None, "kreativ": False}})
            for vorhanden in vorhandene_varianten(p, False):
                for item in random.sample(topf_items, 6) + ["minecraft:vine", "minecraft:leaf_litter"]:
                    ergebnis.append({"bloecke": welt_bloecke, "anzeigen": vorhanden, "aktion": "haupt",
                                     "spieler": {"pos": fuss, "haupt": item, "neben": None, "kreativ": random.random() < 0.3}})
                # Nebenhand
                for item in neben_items + ["minecraft:stone"]:
                    for haupt in [None, "minecraft:poppy", "minecraft:stick"]:
                        ergebnis.append({"bloecke": welt_bloecke, "anzeigen": vorhanden, "aktion": "neben",
                                         "spieler": {"pos": fuss, "haupt": haupt, "neben": item, "kreativ": False}})
                # Drehen und kaputte Toepfe
                for aktion in ["drehen", "drehen_neben", "kaputt"]:
                    ergebnis.append({"bloecke": welt_bloecke, "anzeigen": vorhanden, "aktion": aktion,
                                     "spieler": {"pos": fuss, "haupt": "minecraft:stick", "neben": "minecraft:stick", "kreativ": False}})
    return ergebnis


# ----------------------------------------------------------------------
# Referenz (Original-Funktionen)
# ----------------------------------------------------------------------

WELT_VORLAGE = ref.Welt(GD, TAGS)


def referenz(fall):
    welt = ref.Welt.__new__(ref.Welt)
    welt.__dict__.update(WELT_VORLAGE.__dict__)
    welt.bloecke = {}
    welt.entities = []
    for x, y, z, b in fall["bloecke"]:
        welt.setze((x, y, z), b)

    anzeigen = []
    for a in fall["anzeigen"]:
        e = ref.Entity("block_display", a["pos"], {"Tags": list(a["tags"]), "block_state": a["block"],
                                                    "Rotation": [a["yaw"], 0.0],
                                                    "transformation": {"left_rotation": [0.0, 0.0, 0.0, 1.0], "right_rotation": [0.0, 0.0, 0.0, 1.0],
                                                                       "translation": [0.0, 0.0, 0.0], "scale": [1.0, 1.0, 1.0]}})
        welt.entities.append(e)
        anzeigen.append(e)

    s = fall["spieler"]
    spieler = ref.Spieler(s["pos"], s["haupt"], s["neben"], s["kreativ"])
    welt.entities.append(spieler)
    ref.fuehre_funktion(welt, FUNKTION[fall["aktion"]], ref.Kontext(spieler, s["pos"]))

    ergebnis = []
    for e in welt.entities:
        if e.typ != "block_display" or not e.lebt:
            continue
        t = e.nbt["transformation"]
        ergebnis.append({
            "pos": e.pos, "tags": sorted(e.tags), "block": block_text(e.nbt["block_state"]),
            "yaw": e.nbt["Rotation"][0],
            "t": t["translation"], "s": t["scale"], "l": quaternion(t["left_rotation"]), "r": quaternion(t["right_rotation"]),
            "alt": anzeigen.index(e) if e in anzeigen else -1})
    drops = [e.pos for e in welt.entities if e.typ == "item"]
    return {"anzeigen": ergebnis, "verbraucht": spieler.verbraucht, "drops": drops}


def block_text(b):
    if isinstance(b, str):
        return b
    eig = b.get("properties", {})
    if not eig:
        return b["id"]
    return b["id"] + "[" + ",".join(f"{k}={v}" for k, v in sorted(eig.items())) + "]"


def quaternion(d):
    if isinstance(d, list):
        return d
    w = float(d["angle"])
    a = d["axis"]
    laenge = math.sqrt(sum(x * x for x in a))
    s = math.sin(w / 2) / laenge
    return [a[0] * s, a[1] * s, a[2] * s, math.cos(w / 2)]


# ----------------------------------------------------------------------
# Vergleich
# ----------------------------------------------------------------------

def gleich(a, b, eps=1e-4):
    if isinstance(a, list):
        return isinstance(b, list) and len(a) == len(b) and all(gleich(x, y, eps) for x, y in zip(a, b))
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return abs(a - b) <= eps
    return a == b


def schluessel(anzeige):
    return (round(anzeige["pos"][0], 3), round(anzeige["pos"][1], 3), round(anzeige["pos"][2], 3), tuple(anzeige["tags"]), anzeige["block"])


def vergleiche(fall, erwartet, plugin):
    fehler = []
    e = sorted(erwartet["anzeigen"], key=schluessel)
    p = sorted(plugin["anzeigen"], key=schluessel)
    if len(e) != len(p):
        fehler.append(f"Anzahl Anzeigen: Original {len(e)}, Plugin {len(p)}")
    for a, b in zip(e, p):
        for k in ["pos", "tags", "block", "yaw", "t", "s", "l", "r", "alt"]:
            if not gleich(a[k], b[k]):
                fehler.append(f"{k}: Original {a[k]} / Plugin {b[k]}")
    if erwartet["verbraucht"] != plugin["verbraucht"]:
        fehler.append(f"verbraucht: Original {erwartet['verbraucht']} / Plugin {plugin['verbraucht']}")
    if not gleich(sorted(erwartet["drops"]), sorted(plugin["drops"])):
        fehler.append(f"drops: Original {erwartet['drops']} / Plugin {plugin['drops']}")
    return fehler


def main():
    alle = faelle()
    print(len(alle), "Testfaelle")

    daten_json = os.path.join(HIER, "gaertner.json")
    json.dump(yaml.safe_load(open(GAERTNER_YML)), open(daten_json, "w"))

    eingabe = "\n".join(json.dumps(f) for f in alle) + "\n"
    lauf = subprocess.run(["java", "-cp", KLASSEN + ":" + HIER, "GartenVergleich", daten_json, TAGS],
                          input=eingabe, capture_output=True, text=True)
    if lauf.returncode != 0:
        print(lauf.stderr[-3000:])
        sys.exit(1)
    plugin = [json.loads(z) for z in lauf.stdout.splitlines() if z.strip()]
    assert len(plugin) == len(alle), (len(plugin), len(alle))

    abweichungen = 0
    beispiele = {}
    statistik = {"anzeigen": 0, "drops": 0, "verbraucht": 0}
    for fall, ergebnis in zip(alle, plugin):
        erwartet = referenz(fall)
        statistik["anzeigen"] += len(erwartet["anzeigen"])
        statistik["drops"] += len(erwartet["drops"])
        statistik["verbraucht"] += sum(erwartet["verbraucht"].values())
        fehler = vergleiche(fall, erwartet, ergebnis)
        if fehler:
            abweichungen += 1
            art = fehler[0].split(":")[0] + "|" + fall["aktion"]
            if art not in beispiele:
                beispiele[art] = (fall, fehler)

    print("Abweichungen:", abweichungen, "von", len(alle), "| gepruefte Anzeigen/Drops/Verbrauch:", statistik)
    for art, (fall, fehler) in list(beispiele.items())[:12]:
        print("\n##", art)
        print("  Fall:", json.dumps({k: fall[k] for k in ("bloecke", "aktion", "spieler")})[:400])
        print("  vorhanden:", json.dumps(fall["anzeigen"])[:300])
        for f in fehler[:6]:
            print("   -", f[:400])


main()

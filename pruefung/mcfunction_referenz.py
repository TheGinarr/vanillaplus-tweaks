#!/usr/bin/env python3
"""
Kleiner Nachbau der Minecraft-Befehle, die Gardener's Dream beim Einpflanzen benutzt.

Zweck: Referenz fuer den Vergleichstest. Die Original-.mcfunction-Dateien werden hier Zeile
fuer Zeile "ausgefuehrt" – auf einer Mini-Welt (Bloecke, Block-Displays, ein Spieler).
Das Ergebnis (welche Displays wo, mit welcher Transformation) wird mit dem Plugin verglichen.

Unterstuetzt nur den Befehlsumfang dieser Funktionen. Unbekanntes fuehrt zu einem Fehler,
damit nichts still uebersehen wird.
"""

import json
import math
import os
import re


# ======================================================================
# SNBT lesen (Minecraft-Text-NBT), nur was in den Dateien vorkommt
# ======================================================================

class SnbtLeser:
    def __init__(self, text):
        self.t = text
        self.i = 0

    def leer(self):
        while self.i < len(self.t) and self.t[self.i] in " \t\n":
            self.i += 1

    def wert(self):
        self.leer()
        c = self.t[self.i]
        if c == "{":
            return self.compound()
        if c == "[":
            return self.liste()
        if c in "\"'":
            return self.string()
        return self.nackt()

    def compound(self):
        self.i += 1
        ergebnis = {}
        self.leer()
        if self.t[self.i] == "}":
            self.i += 1
            return ergebnis
        while True:
            self.leer()
            if self.t[self.i] in "\"'":
                schluessel = self.string()
            else:
                start = self.i
                while self.t[self.i] not in ":":
                    self.i += 1
                schluessel = self.t[start:self.i].strip()
            self.leer()
            assert self.t[self.i] == ":", self.t[self.i:self.i + 20]
            self.i += 1
            ergebnis[schluessel] = self.wert()
            self.leer()
            if self.t[self.i] == ",":
                self.i += 1
                continue
            assert self.t[self.i] == "}", self.t[self.i:self.i + 20]
            self.i += 1
            return ergebnis

    def liste(self):
        self.i += 1
        ergebnis = []
        self.leer()
        if self.t[self.i] == "]":
            self.i += 1
            return ergebnis
        while True:
            ergebnis.append(self.wert())
            self.leer()
            if self.t[self.i] == ",":
                self.i += 1
                continue
            assert self.t[self.i] == "]"
            self.i += 1
            return ergebnis

    def string(self):
        anf = self.t[self.i]
        self.i += 1
        teile = []
        while self.t[self.i] != anf:
            if self.t[self.i] == "\\":
                self.i += 1
            teile.append(self.t[self.i])
            self.i += 1
        self.i += 1
        return "".join(teile)

    def nackt(self):
        start = self.i
        while self.i < len(self.t) and self.t[self.i] not in ",}] \n":
            self.i += 1
        wort = self.t[start:self.i]
        if wort in ("true", "false"):
            return wort == "true"
        m = re.fullmatch(r"(-?\d+(?:\.\d+)?|-?\.\d+)([bBsSlLfFdD]?)", wort)
        if m:
            zahl = float(m.group(1))
            if m.group(2) in ("b", "B") or (m.group(2) in ("s", "S", "l", "L")) or ("." not in m.group(1) and not m.group(2)):
                return int(zahl)
            return zahl
        return wort


def snbt(text):
    return SnbtLeser(text).wert()


# ======================================================================
# Befehle in Teile zerlegen (Klammern und Anfuehrungszeichen beachten)
# ======================================================================

def zerlege(zeile):
    teile, aktuell, tiefe, quote = [], "", 0, None
    for c in zeile:
        if quote:
            aktuell += c
            if c == quote:
                quote = None
            continue
        if c in "\"'":
            quote = c
            aktuell += c
            continue
        if c in "{[":
            tiefe += 1
        elif c in "}]":
            tiefe -= 1
        if c == " " and tiefe == 0:
            if aktuell:
                teile.append(aktuell)
            aktuell = ""
        else:
            aktuell += c
    if aktuell:
        teile.append(aktuell)
    return teile


# ======================================================================
# Mini-Welt
# ======================================================================

class Entity:
    zaehler = 0

    def __init__(self, typ, pos, nbt=None):
        Entity.zaehler += 1
        self.nr = Entity.zaehler
        self.typ = typ
        self.pos = list(pos)
        self.nbt = nbt or {}
        self.tags = set(self.nbt.pop("Tags", []))
        self.lebt = True
        if typ == "block_display":
            self.nbt.setdefault("block_state", "minecraft:air")
            self.nbt.setdefault("Rotation", [0.0, 0.0])
            self.nbt.setdefault("transformation", {})

    def __repr__(self):
        return f"<{self.typ} {self.pos} {sorted(self.tags)} {self.nbt}>"


class Spieler(Entity):
    def __init__(self, pos, haupthand, nebenhand, kreativ=False):
        super().__init__("player", pos)
        self.haupthand = haupthand          # Item-ID oder None
        self.nebenhand = nebenhand
        self.kreativ = kreativ
        self.verbraucht = {"mainhand": 0, "offhand": 0}


class Welt:
    def __init__(self, gd_ordner, tags_datei):
        self.ordner = gd_ordner
        self.bloecke = {}                   # (x,y,z) -> (id, props)
        self.entities = []
        self.vanilla_tags = json.load(open(tags_datei))
        self.gd_tags = {}
        for art in ("block", "item"):
            pfad = os.path.join(gd_ordner, "gardenersdream", "tags", art)
            for datei in os.listdir(pfad):
                werte = json.load(open(os.path.join(pfad, datei)))["values"]
                self.gd_tags[(art, "gardenersdream:" + datei[:-5])] = [w["id"] if isinstance(w, dict) else w for w in werte]
        self.funktionen_cache = {}

    # --- Tags ----------------------------------------------------------
    def tag_inhalt(self, art, name):
        name = name.lstrip("#")
        if (art, name) in self.gd_tags:
            werte = self.gd_tags[(art, name)]
        elif name in self.vanilla_tags:
            werte = self.vanilla_tags[name]
        elif name.startswith("minecraft:coral") or name in ("minecraft:corals", "minecraft:wall_corals"):
            # Im Datapack mit "required": false eingetragen – als Item-Tag gibt es sie nicht
            werte = []
        else:
            raise KeyError("Tag unbekannt: " + name)
        ergebnis = set()
        for w in werte:
            if w.startswith("#"):
                ergebnis |= self.tag_inhalt(art, w)
            else:
                ergebnis.add(w)
        return ergebnis

    # --- Bloecke -------------------------------------------------------
    def setze(self, pos, block):
        bid, props = block_lesen(block)
        self.bloecke[tuple(pos)] = (bid, props)

    def block(self, pos):
        return self.bloecke.get(tuple(int(math.floor(v)) for v in pos), ("minecraft:air", {}))

    def block_passt(self, pos, praedikat):
        bid, props = self.block(pos)
        m = re.fullmatch(r"(#?[a-z0-9_:/]+)(\[(.*)\])?", praedikat)
        name = m.group(1)
        if name.startswith("#"):
            if bid not in self.tag_inhalt("block", name):
                return False
        else:
            if not name.startswith("minecraft:"):
                name = "minecraft:" + name
            if bid != name:
                return False
        if m.group(3):
            for paar in m.group(3).split(","):
                k, v = paar.split("=")
                if props.get(k) != v:
                    return False
        return True

    def item_passt(self, item, praedikat):
        if item is None:
            return False
        if praedikat.startswith("#"):
            return item in self.tag_inhalt("item", praedikat)
        if not praedikat.startswith("minecraft:"):
            praedikat = "minecraft:" + praedikat
        return item == praedikat

    # --- Funktionen ----------------------------------------------------
    def funktion(self, name):
        if name not in self.funktionen_cache:
            ns, pfad = name.split(":")
            datei = os.path.join(self.ordner, ns, "function", pfad + ".mcfunction")
            zeilen = []
            for z in open(datei, encoding="utf-8"):
                z = z.strip()
                if z and not z.startswith("#"):
                    zeilen.append(z)
            self.funktionen_cache[name] = zeilen
        return self.funktionen_cache[name]


def block_lesen(text):
    m = re.fullmatch(r"([a-z0-9_:]+)(\[(.*)\])?", text)
    bid = m.group(1)
    if not bid.startswith("minecraft:"):
        bid = "minecraft:" + bid
    props = {}
    if m.group(3):
        for paar in m.group(3).split(","):
            k, v = paar.split("=")
            props[k] = v
    return bid, props


# ======================================================================
# NBT-Vergleich ("teilweise passt", wie in Minecraft)
# ======================================================================

def nbt_passt(ist, soll):
    if isinstance(soll, dict):
        if not isinstance(ist, dict):
            return False
        return all(k in ist and nbt_passt(ist[k], v) for k, v in soll.items())
    if isinstance(soll, list):
        return isinstance(ist, list) and all(any(nbt_passt(i, s) for i in ist) for s in soll)
    if isinstance(soll, float) or isinstance(ist, float):
        return abs(float(ist) - float(soll)) < 1e-9
    return ist == soll


def entity_nbt(welt, e):
    """NBT so, wie Minecraft es fuer Vergleiche zeigt."""
    if isinstance(e, Spieler):
        return {"SelectedItem": {"id": e.haupthand} if e.haupthand else {}}
    return e.nbt


# ======================================================================
# Ausfuehren
# ======================================================================

class Abbruch(Exception):
    pass


class Kontext:
    def __init__(self, ausfuehrer, pos):
        self.ausfuehrer = ausfuehrer
        self.pos = list(pos)

    def kopie(self):
        return Kontext(self.ausfuehrer, self.pos)


def koordinaten(kontext, x, y, z):
    ergebnis = []
    for wert, basis in zip((x, y, z), kontext.pos):
        assert wert.startswith("~"), wert
        rest = wert[1:]
        ergebnis.append(basis + (float(rest) if rest else 0.0))
    return ergebnis


def selektor(welt, kontext, text):
    m = re.fullmatch(r"@([sepa])(\[(.*)\])?", text)
    art, argumente = m.group(1), m.group(3)
    args = []
    if argumente:
        for teil in zerlege_args(argumente):
            k, v = teil.split("=", 1)
            args.append((k, v))

    if art == "s":
        kandidaten = [kontext.ausfuehrer]
    elif art == "p":
        kandidaten = [e for e in welt.entities if isinstance(e, Spieler) and e.lebt]
    else:
        kandidaten = [e for e in welt.entities if e.lebt]

    limit, sortierung = None, None
    for k, v in args:
        if k == "type":
            kandidaten = [e for e in kandidaten if e.typ == v.replace("minecraft:", "")]
        elif k == "tag":
            if v.startswith("!"):
                kandidaten = [e for e in kandidaten if v[1:] not in e.tags]
            else:
                kandidaten = [e for e in kandidaten if v in e.tags]
        elif k == "distance":
            assert v.startswith("..")
            maximum = float(v[2:])
            kandidaten = [e for e in kandidaten if abstand(e.pos, kontext.pos) <= maximum + 1e-9]
        elif k == "limit":
            limit = int(v)
        elif k == "sort":
            sortierung = v
        elif k == "nbt":
            soll = snbt(v)
            kandidaten = [e for e in kandidaten if nbt_passt(entity_nbt(welt, e), soll)]
        elif k == "gamemode":
            kandidaten = [e for e in kandidaten if isinstance(e, Spieler) and e.kreativ == (v == "creative")]
        elif k == "x_rotation":
            kandidaten = []
        else:
            raise ValueError("Selektor-Argument: " + k)

    if art == "p" or sortierung == "nearest":
        kandidaten = sorted(kandidaten, key=lambda e: abstand(e.pos, kontext.pos))
        if art == "p" and limit is None:
            limit = 1
    if limit is not None:
        kandidaten = kandidaten[:limit]
    return kandidaten


def zerlege_args(text):
    teile, aktuell, tiefe = [], "", 0
    for c in text:
        if c in "{[":
            tiefe += 1
        elif c in "}]":
            tiefe -= 1
        if c == "," and tiefe == 0:
            teile.append(aktuell)
            aktuell = ""
        else:
            aktuell += c
    if aktuell:
        teile.append(aktuell)
    return teile


def abstand(a, b):
    return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))


def fuehre_funktion(welt, name, kontext):
    try:
        for zeile in welt.funktion(name):
            fuehre_aus(welt, zerlege(zeile), kontext)
    except Abbruch:
        pass


def fuehre_aus(welt, teile, kontext):
    befehl = teile[0]

    if befehl == "execute":
        execute(welt, teile[1:], [kontext.kopie()])
        return

    if befehl == "function":
        fuehre_funktion(welt, teile[1], kontext)
        return

    if befehl == "return":
        raise Abbruch()

    if befehl == "summon":
        typ = teile[1].replace("minecraft:", "")
        pos = koordinaten(kontext, *teile[2:5])
        nbt = snbt(teile[5]) if len(teile) > 5 else {}
        welt.entities.append(Entity(typ, pos, nbt))
        return

    if befehl == "tp":
        ziele = selektor(welt, kontext, teile[1])
        ziel_pos = koordinaten(kontext, *teile[2:5])
        for e in ziele:
            e.pos = list(ziel_pos)
            if len(teile) == 7:                       # tp @s ~ ~ ~ ~15 ~ (Drehung relativ)
                e.nbt["Rotation"][0] += float(teile[5][1:] or 0)
        return

    if befehl == "data":
        daten(welt, teile, kontext)
        return

    if befehl == "tag":
        for e in selektor(welt, kontext, teile[1]):
            if teile[2] == "add":
                e.tags.add(teile[3])
            else:
                e.tags.discard(teile[3])
        return

    if befehl == "kill":
        for e in selektor(welt, kontext, teile[1]):
            e.lebt = False
        return

    if befehl == "item":
        # item modify entity @s weapon.mainhand gardenersdream:remove_one
        assert teile[1] == "modify" and teile[5] == "gardenersdream:remove_one", teile
        slot = teile[4].split(".")[1]
        kontext.ausfuehrer.verbraucht[slot] += 1
        return

    if befehl in ("playsound", "particle", "advancement", "msg", "title", "scoreboard"):
        return

    raise ValueError("Befehl unbekannt: " + " ".join(teile))


def execute(welt, teile, kontexte):
    i = 0
    while i < len(teile):
        unter = teile[i]
        neu = []

        if unter == "run":
            for k in kontexte:
                fuehre_aus(welt, teile[i + 1:], k)
            return

        if unter == "align":
            assert teile[i + 1] == "xyz"
            for k in kontexte:
                k.pos = [math.floor(v) for v in k.pos]
            i += 2
            continue

        if unter == "positioned":
            for k in kontexte:
                k.pos = koordinaten(k, *teile[i + 1:i + 4])
            i += 4
            continue

        if unter == "as":
            for k in kontexte:
                for e in selektor(welt, k, teile[i + 1]):
                    neu.append(Kontext(e, k.pos))
            kontexte = neu
            i += 2
            continue

        if unter == "at":
            for k in kontexte:
                for e in selektor(welt, k, teile[i + 1]):
                    neu.append(Kontext(k.ausfuehrer, e.pos))
            kontexte = neu
            i += 2
            continue

        if unter in ("if", "unless"):
            soll = unter == "if"
            art = teile[i + 1]
            if art == "block":
                pos_text, praedikat, laenge = teile[i + 2:i + 5], teile[i + 5], 6
                kontexte = [k for k in kontexte if welt.block_passt(koordinaten(k, *pos_text), praedikat) == soll]
            elif art == "entity":
                kontexte = [k for k in kontexte if bool(selektor(welt, k, teile[i + 2])) == soll]
                laenge = 3
            elif art == "items":
                assert teile[i + 2] == "entity" and teile[i + 3] == "@s"
                slot, praedikat = teile[i + 4], teile[i + 5]
                laenge = 6

                def hat(k):
                    item = k.ausfuehrer.haupthand if slot == "weapon.mainhand" else k.ausfuehrer.nebenhand
                    return welt.item_passt(item, praedikat)
                kontexte = [k for k in kontexte if hat(k) == soll]
            elif art == "data":
                assert teile[i + 2] == "entity" and teile[i + 3] == "@s"
                pfad = teile[i + 4]
                laenge = 5

                def daten_da(k):
                    nbt = entity_nbt(welt, k.ausfuehrer)
                    if pfad.startswith("{"):
                        return nbt_passt(nbt, snbt(pfad))
                    wert = nbt
                    for teil in pfad.split("."):
                        if not isinstance(wert, dict) or teil not in wert:
                            return False
                        wert = wert[teil]
                    return True
                kontexte = [k for k in kontexte if daten_da(k) == soll]
            elif art == "predicate":
                raise ValueError("predicate nicht unterstuetzt")
            else:
                raise ValueError("if " + art)
            i += laenge
            continue

        raise ValueError("execute " + unter)


def daten(welt, teile, kontext):
    # data merge entity <sel> {..}
    if teile[1] == "merge":
        for e in selektor(welt, kontext, teile[3]):
            neu = snbt(teile[4])
            for k, v in neu.items():
                if k == "transformation":
                    e.nbt["transformation"] = {**e.nbt["transformation"], **v}
                elif k in ("start_interpolation", "interpolation_duration"):
                    pass
                else:
                    e.nbt[k] = v
        return

    # data modify entity <sel> <pfad> set from entity <sel> <pfad>  |  set value <wert>
    assert teile[1] == "modify" and teile[2] == "entity", teile
    ziele = selektor(welt, kontext, teile[3])
    pfad = teile[4]
    assert teile[5] == "set"
    if teile[6] == "value":
        wert = snbt(teile[7])
        vorhanden = True
    else:
        quellen = selektor(welt, kontext, teile[8])
        quellpfad = teile[9]
        vorhanden, wert = False, None
        if quellen:
            q = quellen[0]
            if isinstance(q, Spieler):
                if quellpfad == "SelectedItem.id":
                    vorhanden, wert = q.haupthand is not None, q.haupthand
                elif quellpfad == "equipment.offhand.id":
                    vorhanden, wert = q.nebenhand is not None, q.nebenhand
                elif quellpfad.startswith("Inventory["):
                    vorhanden = False
                else:
                    raise ValueError(quellpfad)
            else:
                wert = q.nbt
                vorhanden = True
                for teil in quellpfad.split("."):
                    if isinstance(wert, dict) and teil in wert:
                        wert = wert[teil]
                    else:
                        vorhanden = False
                        break
    if not vorhanden:
        return
    for e in ziele:
        ziel = e.nbt
        stuecke = pfad.split(".")
        for teil in stuecke[:-1]:
            ziel = ziel.setdefault(teil, {})
        ziel[stuecke[-1]] = wert

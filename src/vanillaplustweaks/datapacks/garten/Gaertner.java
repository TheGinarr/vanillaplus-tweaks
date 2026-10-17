package vanillaplustweaks.datapacks.garten;

import vanillaplustweaks.datapacks.garten.GartenDaten.Eintrag;
import vanillaplustweaks.datapacks.garten.GartenDaten.Sonderfall;
import vanillaplustweaks.datapacks.garten.GartenDaten.Tabelle;
import vanillaplustweaks.datapacks.garten.GartenDaten.Topf;
import vanillaplustweaks.datapacks.garten.GartenWelt.Block;
import vanillaplustweaks.datapacks.garten.GartenWelt.Pflanze;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Die Logik von Gardener's Dream: Pflanzen in Toepfe setzen, drehen und bei kaputtem Topf entfernen.
 *
 * Aufbau und Reihenfolge folgen den Original-Funktionen des Datapacks (spawn_check, spawnplant,
 * spawnbigplant, spawnwideplant, spawn_offhand/*, plantrotate, pot_broken). Die Abstaende
 * ("innerhalb von 0,8 Bloecken") und Hoehen sind dieselben, damit die Pflanzen genau gleich aussehen.
 * Ein Vergleichstest prueft das gegen das Original.
 *
 * Wichtige Begriffe:
 *   Fuesse   = Position des Spielers
 *   P        = Block der Fuesse, mittig, am Blockboden ("align xyz positioned ~.5 ~ ~.5")
 *   Anzeige  = Block-Display, das die Pflanze zeigt
 */
public final class Gaertner {

    /** Ein Spieler aus Sicht der Garten-Logik. */
    public interface Gartenspieler {

        Vek fuesse();

        /** Item-ID in der Haupthand, oder null */
        String haupthand();

        /** Item-ID in der Nebenhand, oder null */
        String nebenhand();

        boolean kreativ();

        /** Ein Item aus der Hand verbrauchen. */
        void verbrauche(boolean nebenhand);

        /** Nachricht "Diese Pflanze kann nicht gedreht werden". */
        void nichtDrehbar();
    }


    /** Rueckmeldungen fuer Klang und Partikel (im Test leer). */
    public interface Effekte {

        void einpflanzKlang(Gartenspieler spieler, boolean grosserTopf);

        void erdPartikel(Vek blockEcke);

        void drehKlang(Gartenspieler spieler);
    }


    private static final double HOEHE_UEBER_TOPF = 0.24;

    /** Kopf-Hoehen pro Art: auf dem Boden-Kopf / am Wand-Kopf */
    private static final Map<String, Double> KOPF_HOEHE = Map.of(
            "pp_init", 0.17, "pp_init_alt", 0.1, "pp_init_alt2", 0.17, "pp_init_high", 0.4);
    private static final Map<String, Double> WANDKOPF_HOEHE = Map.of(
            "pp_init", 0.41, "pp_init_alt", 0.34, "pp_init_alt2", 0.41, "pp_init_high", 0.64);
    private static final List<String> ARTEN = List.of("pp_init", "pp_init_alt", "pp_init_alt2", "pp_init_high");

    private final GartenDaten daten;
    private final Set<String> blaetter;


    /**
     * @param blaetter Items des Tags #minecraft:leaves (auf Koepfen etwas schmaler, gegen Flackern)
     */
    public Gaertner(GartenDaten daten, Set<String> blaetter) {
        this.daten = daten;
        this.blaetter = blaetter;
    }


    // ==================================================================
    // Ausloeser – wann darf der Spieler pflanzen / drehen?
    // ==================================================================

    public boolean istTopfPflanze(String item) {
        return item != null && daten.topfPflanzen.contains(item);
    }


    public boolean istNebenhandPflanze(String item) {
        return item != null && daten.nebenhandPflanzen.contains(item);
    }


    public boolean istTopf(String blockId) {
        return daten.alleToepfe.contains(blockId);
    }


    public boolean istKleinerTopf(String blockId) {
        return daten.kleineToepfe.contains(blockId);
    }


    // ==================================================================
    // Einpflanzen mit der Haupthand (spawn_check)
    // ==================================================================

    public void einpflanzen(GartenWelt welt, Gartenspieler spieler, Effekte effekte) {
        Vek p = spieler.fuesse().blockMitteUnten();
        boolean ueberlagernd = daten.ueberlagerndePflanzen.contains(spieler.haupthand());
        Predicate<Pflanze> gleicheSorte = ueberlagernd ? hat("pp_overlay") : hatNicht("pp_overlay");

        // Vorhandene Pflanze derselben Sorte wird ersetzt (sie faellt als Item heraus)
        for (Topf topf : Topf.values()) {
            Pflanze alte = erste(welt, hat(topf.markierung).and(gleicheSorte), p, 0.8);
            if (alte != null) {
                abbauen(welt, alte);
            }
        }

        // Kleiner Topf: der Block, in dem die Fuesse stehen (Blumentopf, Kopf)
        if (istIn(welt, p.plus(0, 0.5, 0), daten.kleineToepfe) && keine(welt, hat(Topf.KLEIN.markierung).and(gleicheSorte), p, 0.9)) {
            pflanzeKlein(welt, spieler, effekte, ueberlagernd);
        }

        // Grosser Topf (verzierter Topf): darunter mit Luft darueber, oder in Fusshoehe
        if (istIn(welt, p.plus(0, -0.5, 0), daten.grosseToepfe) && istLuft(welt, p.plus(0, 0.5, 0))
                && keine(welt, hat(Topf.GROSS.markierung).and(gleicheSorte), p, 0.9)) {
            pflanzeGross(welt, spieler, effekte, ueberlagernd);
        }
        if (istIn(welt, p, daten.grosseToepfe) && istLuft(welt, p.plus(0, 1, 0))
                && keine(welt, hat(Topf.GROSS.markierung).and(gleicheSorte), p, 0.9)) {
            pflanzeGross(welt, spieler, effekte, ueberlagernd);
        }

        // Breiter Topf (Kessel, Komposter, Fass, Ackerland, Seerose ...)
        if (istIn(welt, p, daten.breiteToepfe) && istLuft(welt, p.plus(0, 1, 0))
                && keine(welt, hat(Topf.BREIT.markierung).and(gleicheSorte), p, 0.9)) {
            pflanzeBreit(welt, spieler, effekte, ueberlagernd);
        }
        if (istIn(welt, p.plus(0, -0.5, 0), daten.breiteToepfe) && istLuft(welt, p.plus(0, 1, 0))
                && keine(welt, hat(Topf.BREIT.markierung).and(gleicheSorte), p, 0.9)) {
            pflanzeBreit(welt, spieler, effekte, ueberlagernd);
        }
    }


    // ------------------------------------------------------------------
    // Kleiner Topf (spawnplant)
    // ------------------------------------------------------------------

    private void pflanzeKlein(GartenWelt welt, Gartenspieler spieler, Effekte effekte, boolean ueberlagernd) {
        Vek p = spieler.fuesse().blockMitteUnten();
        Tabelle tabelle = daten.tabelle(Topf.KLEIN);
        String item = spieler.haupthand();
        Block hier = blockBei(welt, p);

        // 1. Anzeige aus der Tabelle ...
        Eintrag eintrag = item == null ? null : tabelle.haupthand().get(item);
        if (eintrag != null) {
            erschaffe(welt, p, Topf.KLEIN, eintrag);
        }

        // ... mit Sonder-Groesse auf Koepfen (Ranken, Moos, Flechten)
        for (Sonderfall sonder : tabelle.sonderfaelle()) {
            if (sonder.verschiebung() != null && sonder.item().equals(item) && sonder.auf().equals(hier.id())) {
                for (Pflanze anzeige : alle(welt, hat(sonder.art()), p, 0.8)) {
                    anzeige.setzeVerschiebungUndGroesse(sonder.verschiebung(), sonder.groesse());
                }
            }
        }

        // 2. Sonst die Standard-Darstellung (Abstand hier vom Spieler selbst gemessen, wie im Datapack)
        if (!ueberlagernd && keine(welt, hat("potplant").and(hatNicht("pp_overlay")), spieler.fuesse(), 1.0)) {
            erschaffe(welt, p, Topf.KLEIN, tabelle.standard());
        }

        // 3. Anzeigen ohne eigenen Block zeigen das Item aus der Hand
        bloeckeAusHandSetzen(welt, item);

        // 4. Koepfe als Topf: hoeher setzen, an der Wand verschieben, wie der Kopf drehen
        for (Pflanze neu : alle(welt, hatEine(ARTEN), null, 0)) {
            neu.markiere("pp_rotating");
        }

        if (hier.id().equals("minecraft:player_head")) {
            for (String art : ARTEN) {
                anArtVersetzen(welt, art, p, 0, KOPF_HOEHE.get(art), 0);
            }
            hoehenSonderfaelle(welt, tabelle, item, hier);
        }

        if (hier.id().equals("minecraft:player_wall_head")) {
            String richtung = hier.eigenschaft("facing");
            if ("north".equals(richtung)) anArtVersetzen(welt, "pp_rotating", p, 0, 0, 0.25);
            if ("south".equals(richtung)) anArtVersetzen(welt, "pp_rotating", p, 0, 0, -0.25);
            if ("east".equals(richtung)) anArtVersetzen(welt, "pp_rotating", p, -0.25, 0, 0);
            if ("west".equals(richtung)) anArtVersetzen(welt, "pp_rotating", p, 0.25, 0, 0);

            for (String art : ARTEN) {
                anArtVersetzen(welt, art, p, 0, WANDKOPF_HOEHE.get(art), 0);
            }
            hoehenSonderfaelle(welt, tabelle, item, hier);
        }

        boolean aufKopf = hier.id().equals("minecraft:player_head") || hier.id().equals("minecraft:player_wall_head");
        if (aufKopf && item != null && blaetter.contains(item)) {
            // Blaetter etwas schmaler, damit sie nicht mit dem Kopf flackern
            for (Pflanze anzeige : alle(welt, hat("pp_rotating"), p, 0.8)) {
                anzeige.setzeVerschiebungUndGroesse(new float[] {-0.245f, 0f, -0.245f}, new float[] {0.49f, 0.9f, 0.49f});
            }
        }

        if (hier.id().equals("minecraft:player_head")) {
            Float drehung = kopfDrehung(hier.eigenschaft("rotation"));
            if (drehung != null) {
                for (Pflanze anzeige : alle(welt, hat("pp_rotating"), p, 0.8)) {
                    anzeige.setzeGierung(drehung);
                }
            }
        }

        entmarkiereAlle(welt, "pp_rotating");

        // 5. Seerose und Ackerland
        if (hier.id().equals("minecraft:lily_pad")) {
            for (Pflanze anzeige : alle(welt, hat("pp_init"), p, 0.8)) {
                verschiebe(anzeige, 0, -0.215, 0);
            }
        }
        if (hier.id().equals("minecraft:farmland")) {
            for (Pflanze anzeige : alle(welt, hat("pp_init"), p, 0.8)) {
                verschiebe(anzeige, 0, 0.7, 0);
            }
        }

        if (!hier.id().equals("minecraft:lily_pad")) {
            effekte.erdPartikel(p.blockEcke());
        }

        for (String art : ARTEN) {
            entmarkiereAlle(welt, art);
        }
        verbrauchen(spieler, false);
        effekte.einpflanzKlang(spieler, false);
    }


    /** Kopf-Drehung 0..15 -> Gierung der Pflanze (4-fach symmetrisch, nur der 22,5-Grad-Rest zaehlt). */
    static Float kopfDrehung(String rotation) {
        if (rotation == null) {
            return null;
        }
        return switch (Integer.parseInt(rotation) % 4) {
            case 1 -> 22.5f;
            case 2 -> 45.0f;
            case 3 -> 67.5f;
            default -> null;
        };
    }


    /** Zusaetzliche Hoehen fuer einzelne Items auf Koepfen (Rote Bete, Kuerbis- und Melonenkerne). */
    private void hoehenSonderfaelle(GartenWelt welt, Tabelle tabelle, String item, Block hier) {
        for (Sonderfall sonder : tabelle.sonderfaelle()) {
            if (sonder.hoehe() != null && sonder.item().equals(item) && sonder.auf().equals(hier.id())) {
                for (Pflanze anzeige : alle(welt, hat(sonder.art()), null, 0)) {
                    verschiebe(anzeige, 0, sonder.hoehe(), 0);
                }
            }
        }
    }


    // ------------------------------------------------------------------
    // Grosser Topf (spawnbigplant)
    // ------------------------------------------------------------------

    private void pflanzeGross(GartenWelt welt, Gartenspieler spieler, Effekte effekte, boolean ueberlagernd) {
        Vek p = spieler.fuesse().blockMitteUnten();
        Tabelle tabelle = daten.tabelle(Topf.GROSS);
        String item = spieler.haupthand();

        Eintrag eintrag = item == null ? null : tabelle.haupthand().get(item);
        if (eintrag != null) {
            erschaffe(welt, p, Topf.GROSS, eintrag);
        }
        if (!ueberlagernd && keine(welt, hat("potplant").and(hatNicht("pp_overlay")), p, 0.8)) {
            erschaffe(welt, p, Topf.GROSS, tabelle.standard());
        }

        // Steht der Spieler nicht direkt auf einem verzierten Topf: etwas tiefer setzen
        if (!istBlock(welt, p.plus(0, -1, 0), "minecraft:decorated_pot")) {
            setzeAlleAuf(welt, hat("potplant"), p, 0.8, p.plus(0, -0.1, 0));
        }

        bloeckeAusHandSetzen(welt, item);

        entmarkiereAlle(welt, "pp_init");
        entmarkiereAlle(welt, "pp_init_alt");
        verbrauchen(spieler, false);
        effekte.einpflanzKlang(spieler, true);
    }


    // ------------------------------------------------------------------
    // Breiter Topf (spawnwideplant)
    // ------------------------------------------------------------------

    private void pflanzeBreit(GartenWelt welt, Gartenspieler spieler, Effekte effekte, boolean ueberlagernd) {
        Vek p = spieler.fuesse().blockMitteUnten();
        Tabelle tabelle = daten.tabelle(Topf.BREIT);
        String item = spieler.haupthand();
        Block hier = blockBei(welt, p);

        Eintrag eintrag = item == null ? null : tabelle.haupthand().get(item);
        if (eintrag != null) {
            erschaffe(welt, p, Topf.BREIT, eintrag);
        }
        if (!ueberlagernd && keine(welt, hat("potplant").and(hatNicht("pp_overlay")), p, 0.8)) {
            erschaffe(welt, p, Topf.BREIT, tabelle.standard());
        }

        if (!istBlock(welt, p.plus(0, -1, 0), "minecraft:decorated_pot")) {
            setzeAlleAuf(welt, hat("potplant"), p, 0.8, p.plus(0, -0.1, 0));
        }

        bloeckeAusHandSetzen(welt, item);

        breiteHoehen(welt, p, hat("potplant"));

        // Feinabstimmung einzelner Items auf Seerose / Ackerland
        for (Sonderfall sonder : tabelle.sonderfaelle()) {
            if (sonder.hoehe() != null && sonder.item().equals(item) && sonder.auf().equals(hier.id())) {
                for (Pflanze anzeige : alle(welt, hat(sonder.art()), null, 0)) {
                    verschiebe(anzeige, 0, sonder.hoehe(), 0);
                }
            }
        }

        entmarkiereAlle(welt, "pp_init");
        entmarkiereAlle(welt, "pp_init_alt");
        verbrauchen(spieler, false);
        effekte.einpflanzKlang(spieler, true);
    }


    /** Hoehe je nach Untergrund: halber Topf (Stufe), Fass, Ackerland, Seerose. */
    private void breiteHoehen(GartenWelt welt, Vek p, Predicate<Pflanze> welche) {
        if (istBlockMit(welt, p, "minecraft:mossy_cobblestone_slab", "type", "bottom")) {
            setzeAlleAuf(welt, welche, p, 0.8, p.plus(0, 0.51, 0));
        }
        if (istBlockMit(welt, p.plus(0, -1, 0), "minecraft:mossy_cobblestone_slab", "type", "top")) {
            setzeAlleAuf(welt, welche, p, 0.8, p.plus(0, 0.01, 0));
        }
        if (istBlock(welt, p.plus(0, -1, 0), "minecraft:barrel")) {
            setzeAlleAuf(welt, welche, p, 0.8, p.plus(0, 0.01, 0));
        }
        if (istBlock(welt, p, "minecraft:farmland")) {
            setzeAlleAuf(welt, welche, p, 0.8, p.plus(0, 0.92, 0));
        }
        if (istBlock(welt, p, "minecraft:lily_pad")) {
            setzeAlleAuf(welt, welche, p, 0.8, p.plus(0, 0.02, 0));
        }
    }


    // ==================================================================
    // Einpflanzen mit der Nebenhand (spawn_check_offhand) – Tropfblatt und Mangroven-Keimling haengend
    // ==================================================================

    public void einpflanzenNebenhand(GartenWelt welt, Gartenspieler spieler, Effekte effekte) {
        if (istTopfPflanze(spieler.haupthand())) {
            return; // die Haupthand hat Vorrang
        }

        Vek p = spieler.fuesse().blockMitteUnten();
        Predicate<Pflanze> normal = hatNicht("pp_overlay");

        for (Topf topf : Topf.values()) {
            Pflanze alte = erste(welt, hat(topf.markierung).and(normal), p, 0.8);
            if (alte != null) {
                abbauen(welt, alte);
            }
        }

        if (istIn(welt, p.plus(0, 0.5, 0), daten.kleineToepfe) && keine(welt, hat(Topf.KLEIN.markierung).and(normal), p, 0.9)) {
            nebenhandKlein(welt, spieler, effekte);
        }
        if (istIn(welt, p.plus(0, -0.5, 0), daten.grosseToepfe) && istLuft(welt, p.plus(0, 0.5, 0))
                && keine(welt, hat(Topf.GROSS.markierung).and(normal), p, 0.9)) {
            nebenhandGross(welt, spieler, effekte);
        }
        if (istIn(welt, p, daten.grosseToepfe) && istLuft(welt, p.plus(0, 1, 0))
                && keine(welt, hat(Topf.GROSS.markierung).and(normal), p, 0.9)) {
            nebenhandGross(welt, spieler, effekte);
        }
        if (istIn(welt, p, daten.breiteToepfe) && istLuft(welt, p.plus(0, 1, 0))
                && keine(welt, hat(Topf.BREIT.markierung).and(normal), p, 0.9)) {
            nebenhandBreit(welt, spieler, effekte);
        }
        if (istIn(welt, p.plus(0, -0.5, 0), daten.breiteToepfe) && istLuft(welt, p.plus(0, 1, 0))
                && keine(welt, hat(Topf.BREIT.markierung).and(normal), p, 0.9)) {
            nebenhandBreit(welt, spieler, effekte);
        }
    }


    private void nebenhandKlein(GartenWelt welt, Gartenspieler spieler, Effekte effekte) {
        Vek p = spieler.fuesse().blockMitteUnten();
        nebenhandErschaffen(welt, spieler, p, Topf.KLEIN);
        Block hier = blockBei(welt, p);

        if (hier.id().equals("minecraft:player_head")) {
            for (Pflanze anzeige : alle(welt, hat("pp_new"), p, 0.9)) verschiebe(anzeige, 0, 0.17, 0);
        }
        if (hier.id().equals("minecraft:player_wall_head")) {
            for (Pflanze anzeige : alle(welt, hat("pp_new"), p, 0.9)) verschiebe(anzeige, 0, 0.41, 0);

            String richtung = hier.eigenschaft("facing");
            double dx = "east".equals(richtung) ? -0.25 : "west".equals(richtung) ? 0.25 : 0;
            double dz = "north".equals(richtung) ? 0.25 : "south".equals(richtung) ? -0.25 : 0;
            for (Pflanze anzeige : alle(welt, hat("pp_new"), p, 0.9)) verschiebe(anzeige, dx, 0, dz);
        }

        verbrauchen(spieler, true);
        effekte.einpflanzKlang(spieler, false);
        entmarkiereAlle(welt, "pp_new");
    }


    private void nebenhandGross(GartenWelt welt, Gartenspieler spieler, Effekte effekte) {
        Vek p = spieler.fuesse().blockMitteUnten();
        nebenhandErschaffen(welt, spieler, p, Topf.GROSS);

        if (!istBlock(welt, p.plus(0, -1, 0), "minecraft:decorated_pot")) {
            setzeAlleAuf(welt, hat("pp_new"), p, 0.8, p.plus(0, -0.1, 0));
        }

        verbrauchen(spieler, true);
        effekte.einpflanzKlang(spieler, false);
        entmarkiereAlle(welt, "pp_new");
    }


    private void nebenhandBreit(GartenWelt welt, Gartenspieler spieler, Effekte effekte) {
        Vek p = spieler.fuesse().blockMitteUnten();
        nebenhandErschaffen(welt, spieler, p, Topf.BREIT);

        if (!istBlock(welt, p.plus(0, -1, 0), "minecraft:decorated_pot")) {
            setzeAlleAuf(welt, hat("pp_new"), p, 0.8, p.plus(0, -0.1, 0));
        }
        breiteHoehen(welt, p, hat("pp_new"));

        verbrauchen(spieler, true);
        effekte.einpflanzKlang(spieler, false);
        entmarkiereAlle(welt, "pp_new");
    }


    private void nebenhandErschaffen(GartenWelt welt, Gartenspieler spieler, Vek p, Topf topf) {
        String item = spieler.nebenhand();
        Eintrag eintrag = item == null ? null : daten.tabelle(topf).nebenhand().get(item);
        if (eintrag != null) {
            erschaffe(welt, p, topf, eintrag);
        }
    }


    // ==================================================================
    // Drehen mit einem Stock (plantrotate) – 15 Grad, in der Nebenhand 7,5 Grad
    // ==================================================================

    public void drehen(GartenWelt welt, Gartenspieler spieler, float grad, Effekte effekte) {
        Vek p = spieler.fuesse().blockMitteUnten();

        if (keine(welt, hat("pp_norotate"), p, 0.9)) {
            for (Pflanze anzeige : alle(welt, hat("potplant").and(hatNicht("pp_overlay")), p, 0.9)) {
                anzeige.setzeGierung(anzeige.gierung() + grad);
            }
        } else {
            spieler.nichtDrehbar();
        }
        effekte.drehKlang(spieler);
    }


    // ==================================================================
    // Kaputte Toepfe (pot_broken)
    // ==================================================================

    /** Steht der Topf unter dieser Pflanze noch? Sonst muss sie entfernt werden. */
    public boolean topfFehlt(GartenWelt welt, Pflanze anzeige) {
        Vek pos = anzeige.position();
        Set<String> markierungen = anzeige.markierungen();

        if (markierungen.contains("smallpp")) {
            return !istIn(welt, pos, daten.kleineToepfe);
        }
        if (markierungen.contains("bigpp")) {
            return !istIn(welt, pos.plus(0, -0.3, 0), daten.grosseToepfe);
        }
        if (markierungen.contains("widepp")) {
            return !istIn(welt, pos.plus(0, -0.3, 0), daten.breiteToepfe)
                    && !istIn(welt, pos, daten.breiteToepfe)
                    && !istIn(welt, pos.plus(0, 0.5, 0), daten.breiteToepfe);
        }
        return false;
    }


    /**
     * Alle Pflanzen, die in dem Topf-Block x/y/z stehen – also genau die, die verschwinden wuerden,
     * wenn dieser Block abgebaut wird (gleiche Regel wie in topfFehlt).
     */
    public List<Pflanze> pflanzenImTopf(GartenWelt welt, int x, int y, int z) {
        List<Pflanze> ergebnis = new ArrayList<>();
        for (Pflanze anzeige : welt.pflanzen()) {
            Vek pos = anzeige.position();
            Set<String> markierungen = anzeige.markierungen();

            boolean drin;
            if (markierungen.contains("smallpp")) {
                drin = istBlock(pos, x, y, z);
            } else if (markierungen.contains("bigpp")) {
                drin = istBlock(pos.plus(0, -0.3, 0), x, y, z);
            } else if (markierungen.contains("widepp")) {
                drin = istBlock(pos.plus(0, -0.3, 0), x, y, z) || istBlock(pos, x, y, z) || istBlock(pos.plus(0, 0.5, 0), x, y, z);
            } else {
                drin = false;
            }

            if (drin) {
                ergebnis.add(anzeige);
            }
        }
        return ergebnis;
    }


    private static boolean istBlock(Vek pos, int x, int y, int z) {
        return pos.blockX() == x && pos.blockY() == y && pos.blockZ() == z;
    }


    /** Pflanze entfernen und als Item fallen lassen (plant_break). */
    public void abbauen(GartenWelt welt, Pflanze anzeige) {
        welt.lasseFallen(anzeige.position().blockMitteUnten().plus(0, 0.3, 0), anzeige);
        welt.entferne(anzeige);
    }


    // ==================================================================
    // Hilfsmethoden – entsprechen den Selektoren im Datapack
    // ==================================================================

    private void erschaffe(GartenWelt welt, Vek p, Topf topf, Eintrag eintrag) {
        Set<String> markierungen = new LinkedHashSet<>();
        markierungen.add(topf.markierung);
        markierungen.add("potplant");
        markierungen.add(eintrag.art());
        markierungen.addAll(eintrag.zusatz());

        welt.erschaffe(p.plus(0, HOEHE_UEBER_TOPF, 0), markierungen, eintrag.block(), eintrag.darstellung());
    }


    /** Alle frischen Anzeigen ohne Block bekommen den Block des Items in der Hand. */
    private static void bloeckeAusHandSetzen(GartenWelt welt, String item) {
        if (item == null) {
            return;
        }
        for (Pflanze anzeige : alle(welt, hat("pp_init"), null, 0)) {
            if (anzeige.blockId().equals("minecraft:air")) {
                anzeige.setzeBlockVonItem(item);
            }
        }
    }


    /**
     * "execute at @e[tag=ART,distance=..0.8] run tp @e[tag=ART,distance=..0.2] ~dx ~dy ~dz":
     * jede passende Anzeige nahe P wird um den Versatz verschoben (Positionen vorher festhalten).
     */
    private static void anArtVersetzen(GartenWelt welt, String art, Vek p, double dx, double dy, double dz) {
        List<Vek> orte = new ArrayList<>();
        for (Pflanze anzeige : alle(welt, hat(art), p, 0.8)) {
            orte.add(anzeige.position());
        }
        for (Vek ort : orte) {
            for (Pflanze anzeige : alle(welt, hat(art), ort, 0.2)) {
                anzeige.setzePosition(ort.plus(dx, dy, dz));
            }
        }
    }


    private static void setzeAlleAuf(GartenWelt welt, Predicate<Pflanze> welche, Vek mitte, double abstand, Vek ziel) {
        for (Pflanze anzeige : alle(welt, welche, mitte, abstand)) {
            anzeige.setzePosition(ziel);
        }
    }


    private static void verschiebe(Pflanze anzeige, double dx, double dy, double dz) {
        anzeige.setzePosition(anzeige.position().plus(dx, dy, dz));
    }


    private static void entmarkiereAlle(GartenWelt welt, String markierung) {
        for (Pflanze anzeige : alle(welt, hat(markierung), null, 0)) {
            anzeige.entmarkiere(markierung);
        }
    }


    private static void verbrauchen(Gartenspieler spieler, boolean nebenhand) {
        if (!spieler.kreativ()) {
            spieler.verbrauche(nebenhand);
        }
    }


    /** Alle passenden Anzeigen; mitte == null bedeutet: ohne Abstandsgrenze. */
    private static List<Pflanze> alle(GartenWelt welt, Predicate<Pflanze> filter, Vek mitte, double abstand) {
        List<Pflanze> ergebnis = new ArrayList<>();
        for (Pflanze anzeige : welt.pflanzen()) {
            if (filter.test(anzeige) && (mitte == null || anzeige.position().abstand(mitte) <= abstand + 1e-9)) {
                ergebnis.add(anzeige);
            }
        }
        return ergebnis;
    }


    private static Pflanze erste(GartenWelt welt, Predicate<Pflanze> filter, Vek mitte, double abstand) {
        List<Pflanze> treffer = alle(welt, filter, mitte, abstand);
        return treffer.isEmpty() ? null : treffer.get(0);
    }


    private static boolean keine(GartenWelt welt, Predicate<Pflanze> filter, Vek mitte, double abstand) {
        return alle(welt, filter, mitte, abstand).isEmpty();
    }


    private static Predicate<Pflanze> hat(String markierung) {
        return anzeige -> anzeige.markierungen().contains(markierung);
    }


    private static Predicate<Pflanze> hatNicht(String markierung) {
        return anzeige -> !anzeige.markierungen().contains(markierung);
    }


    private static Predicate<Pflanze> hatEine(List<String> markierungen) {
        Set<String> menge = new HashSet<>(markierungen);
        return anzeige -> anzeige.markierungen().stream().anyMatch(menge::contains);
    }


    private static Block blockBei(GartenWelt welt, Vek pos) {
        return welt.block(pos.blockX(), pos.blockY(), pos.blockZ());
    }


    private static boolean istIn(GartenWelt welt, Vek pos, Set<String> bloecke) {
        return bloecke.contains(blockBei(welt, pos).id());
    }


    private static boolean istLuft(GartenWelt welt, Vek pos) {
        return blockBei(welt, pos).id().equals("minecraft:air");
    }


    private static boolean istBlock(GartenWelt welt, Vek pos, String id) {
        return blockBei(welt, pos).id().equals(id);
    }


    private static boolean istBlockMit(GartenWelt welt, Vek pos, String id, String eigenschaft, String wert) {
        Block block = blockBei(welt, pos);
        return block.id().equals(id) && wert.equals(block.eigenschaft(eigenschaft));
    }
}

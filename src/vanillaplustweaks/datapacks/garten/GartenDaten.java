package vanillaplustweaks.datapacks.garten;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Die Pflanzen-Tabellen aus "daten/gaertner.yml" (erzeugt aus Gardener's Dream) –
 * ohne Bukkit, damit sie auch im Vergleichstest ausserhalb des Servers geladen werden koennen.
 *
 * Fuer jede Topf-Groesse steht dort, wie ein Item im Topf aussieht (Verschiebung, Groesse,
 * Drehung, evtl. ein anderer Block-Zustand). Items ohne eigenen Eintrag bekommen die Standard-Darstellung.
 */
public final class GartenDaten {

    /** Die drei Topf-Groessen des Datapacks und ihre Markierung an der Anzeige. */
    public enum Topf {
        KLEIN("klein", "smallpp"),
        GROSS("gross", "bigpp"),
        BREIT("breit", "widepp");

        public final String schluessel;
        public final String markierung;

        Topf(String schluessel, String markierung) {
            this.schluessel = schluessel;
            this.markierung = markierung;
        }
    }


    /**
     * Eine Darstellung aus der Tabelle.
     *
     * @param art          Hoehen-Gruppe als Markierung (pp_init, pp_init_alt, pp_init_alt2, pp_init_high, pp_new)
     * @param zusatz       weitere Markierungen (pp_overlay, pp_customdrop, pp_default_plant ...)
     * @param block        eigener Block-Zustand, oder null = Block des Items in der Hand
     */
    public record Eintrag(String art, List<String> zusatz, String block, Darstellung darstellung) {
    }


    /**
     * Sonderfall fuer ein bestimmtes Item auf einem bestimmten Block:
     * entweder neue Verschiebung/Groesse, oder eine zusaetzliche Hoehe.
     */
    public record Sonderfall(String item, String auf, String art, float[] verschiebung, float[] groesse, Double hoehe) {
    }


    public record Tabelle(Map<String, Eintrag> haupthand, Map<String, Eintrag> nebenhand, Eintrag standard, List<Sonderfall> sonderfaelle) {
    }


    /** Art-Name in der YAML -> Markierung im Datapack */
    private static final Map<String, String> ARTEN = Map.of(
            "normal", "pp_init",
            "alt", "pp_init_alt",
            "alt2", "pp_init_alt2",
            "hoch", "pp_init_high",
            "neu", "pp_new");


    private final Map<Topf, Tabelle> tabellen = new EnumMap<>(Topf.class);

    public final Set<String> topfPflanzen;
    public final Set<String> nebenhandPflanzen;
    public final Set<String> ueberlagerndePflanzen;
    public final Set<String> erden;
    public final Set<String> kleineToepfe;
    public final Set<String> grosseToepfe;
    public final Set<String> breiteToepfe;
    public final Set<String> alleToepfe;


    /**
     * @param daten        Inhalt der gaertner.yml als verschachtelte Maps/Listen
     * @param itemTag      liefert zu "minecraft:leaves" die Items des Item-Tags (leer, wenn es ihn nicht gibt)
     * @param blockTag     dasselbe fuer Block-Tags
     */
    public GartenDaten(Map<String, ?> daten, Function<String, Set<String>> itemTag, Function<String, Set<String>> blockTag) {
        Map<?, ?> pflanzen = (Map<?, ?>) daten.get("pflanzen");
        for (Topf topf : Topf.values()) {
            tabellen.put(topf, tabelle((Map<?, ?>) pflanzen.get(topf.schluessel)));
        }

        Map<?, ?> listen = (Map<?, ?>) daten.get("listen");
        topfPflanzen = aufloesen(listen, "item/pot_plants", itemTag);
        nebenhandPflanzen = aufloesen(listen, "item/alt_plants", itemTag);
        ueberlagerndePflanzen = aufloesen(listen, "item/overlay_plants", itemTag);
        erden = aufloesen(listen, "item/pot_soils", itemTag);

        Map<String, Set<String>> eigeneBlockListen = new HashMap<>();
        Function<String, Set<String>> blockAufloeser = tag -> eigeneBlockListen.containsKey(tag) ? eigeneBlockListen.get(tag) : blockTag.apply(tag);

        kleineToepfe = aufloesen(listen, "block/small_pots", blockAufloeser);
        grosseToepfe = aufloesen(listen, "block/big_pots", blockAufloeser);
        breiteToepfe = aufloesen(listen, "block/wide_pots", blockAufloeser);
        eigeneBlockListen.put("gardenersdream:small_pots", kleineToepfe);
        eigeneBlockListen.put("gardenersdream:big_pots", grosseToepfe);
        eigeneBlockListen.put("gardenersdream:wide_pots", breiteToepfe);
        alleToepfe = aufloesen(listen, "block/all_pots", blockAufloeser);
    }


    public Tabelle tabelle(Topf topf) {
        return tabellen.get(topf);
    }


    // ------------------------------------------------------------------
    // Einlesen
    // ------------------------------------------------------------------

    private static Tabelle tabelle(Map<?, ?> yaml) {
        Map<String, Eintrag> haupthand = eintraege((Map<?, ?>) yaml.get("haupthand"));
        Map<String, Eintrag> nebenhand = eintraege((Map<?, ?>) yaml.get("nebenhand"));
        Eintrag standard = eintrag((Map<?, ?>) yaml.get("standard"));

        List<Sonderfall> sonderfaelle = new ArrayList<>();
        Object liste = yaml.get("sonderfaelle");
        if (liste instanceof List<?> eintraege) {
            for (Object o : eintraege) {
                Map<?, ?> s = (Map<?, ?>) o;
                Object hoehe = s.get("hoehe");
                sonderfaelle.add(new Sonderfall(
                        String.valueOf(s.get("item")),
                        mitNamensraum(String.valueOf(s.get("auf"))),
                        ARTEN.get(String.valueOf(s.get("art"))),
                        s.containsKey("verschiebung") ? zahlen(s.get("verschiebung")) : null,
                        s.containsKey("groesse") ? zahlen(s.get("groesse")) : null,
                        hoehe instanceof Number zahl ? zahl.doubleValue() : null));
            }
        }
        return new Tabelle(haupthand, nebenhand, standard, sonderfaelle);
    }


    private static Map<String, Eintrag> eintraege(Map<?, ?> yaml) {
        Map<String, Eintrag> ergebnis = new HashMap<>();
        if (yaml == null) {
            return ergebnis;
        }
        for (Map.Entry<?, ?> e : yaml.entrySet()) {
            List<?> darstellungen = (List<?>) e.getValue();
            // Im Datapack gibt es pro Item genau eine Darstellung
            ergebnis.put(String.valueOf(e.getKey()), eintrag((Map<?, ?>) darstellungen.get(0)));
        }
        return ergebnis;
    }


    private static Eintrag eintrag(Map<?, ?> yaml) {
        List<String> zusatz = new ArrayList<>();
        if (yaml.get("markierungen") instanceof List<?> liste) {
            for (Object o : liste) {
                zusatz.add(String.valueOf(o));
            }
        }

        Object block = yaml.get("block");
        Darstellung darstellung = new Darstellung(
                zahlen(yaml.get("verschiebung")),
                zahlen(yaml.get("groesse")),
                drehung((Map<?, ?>) yaml.get("drehung_links")),
                drehung((Map<?, ?>) yaml.get("drehung_rechts")));

        return new Eintrag(ARTEN.get(String.valueOf(yaml.get("art"))), zusatz, block == null ? null : String.valueOf(block), darstellung);
    }


    /** Drehung als Quaternion; Achse+Winkel wird wie in Minecraft umgerechnet. */
    static float[] drehung(Map<?, ?> yaml) {
        if (yaml == null) {
            return Darstellung.KEINE_DREHUNG.clone();
        }
        if (yaml.containsKey("quaternion")) {
            return zahlen(yaml.get("quaternion"));
        }

        double winkel = ((Number) yaml.get("winkel")).doubleValue();
        float[] achse = zahlen(yaml.get("achse"));
        double laenge = Math.sqrt(achse[0] * achse[0] + achse[1] * achse[1] + achse[2] * achse[2]);
        double sinus = Math.sin(winkel / 2) / laenge;

        return new float[] {
                (float) (achse[0] * sinus),
                (float) (achse[1] * sinus),
                (float) (achse[2] * sinus),
                (float) Math.cos(winkel / 2)};
    }


    private static float[] zahlen(Object liste) {
        List<?> werte = (List<?>) liste;
        float[] ergebnis = new float[werte.size()];
        for (int i = 0; i < ergebnis.length; i++) {
            ergebnis[i] = ((Number) werte.get(i)).floatValue();
        }
        return ergebnis;
    }


    /** Liste mit Eintraegen wie "minecraft:vine" oder "#minecraft:leaves" in eine Menge von IDs aufloesen. */
    private static Set<String> aufloesen(Map<?, ?> listen, String name, Function<String, Set<String>> tagAufloeser) {
        Set<String> ergebnis = new HashSet<>();
        for (Object o : (List<?>) listen.get(name)) {
            String eintrag = String.valueOf(o);
            if (eintrag.startsWith("#")) {
                ergebnis.addAll(tagAufloeser.apply(eintrag.substring(1)));
            } else {
                ergebnis.add(eintrag);
            }
        }
        return ergebnis;
    }


    private static String mitNamensraum(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }
}

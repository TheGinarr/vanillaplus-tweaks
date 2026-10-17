import vanillaplustweaks.datapacks.garten.Darstellung;
import vanillaplustweaks.datapacks.garten.GartenDaten;
import vanillaplustweaks.datapacks.garten.GartenWelt;
import vanillaplustweaks.datapacks.garten.Gaertner;
import vanillaplustweaks.datapacks.garten.Vek;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Vergleichstest fuer die Garten-Logik (Gardener's Dream).
 *
 * Liest Testfaelle als JSON-Zeilen von stdin, fuehrt die Plugin-Logik auf einer Mini-Welt aus
 * und schreibt das Ergebnis als JSON-Zeilen. Ein Python-Skript macht dasselbe mit den
 * Original-Funktionen des Datapacks und vergleicht.
 *
 * Aufruf: java -cp <plugin-klassen>:. GartenVergleich gaertner.json tags.json < faelle.jsonl
 */
public class GartenVergleich {

    // ------------------------------------------------------------------
    // Mini-Welt
    // ------------------------------------------------------------------

    static final class Anzeige implements GartenWelt.Pflanze {
        final int nr;
        Vek pos;
        final Set<String> tags = new LinkedHashSet<>();
        String block;
        float yaw;
        float[] t, s, l, r;
        boolean lebt = true;

        Anzeige(int nr) { this.nr = nr; }

        public Vek position() { return pos; }
        public void setzePosition(Vek p) { pos = p; }
        public Set<String> markierungen() { return tags; }
        public void markiere(String m) { tags.add(m); }
        public void entmarkiere(String m) { tags.remove(m); }
        public String blockId() { return block == null ? "minecraft:air" : block.replaceAll("\\[.*", ""); }
        public void setzeBlockVonItem(String item) { block = item; }
        public float gierung() { return yaw; }
        public void setzeGierung(float g) { yaw = g; }
        public void setzeVerschiebungUndGroesse(float[] v, float[] g) { t = v.clone(); s = g.clone(); }
    }

    static final class Welt implements GartenWelt {
        final Map<String, Block> bloecke = new HashMap<>();
        final List<Anzeige> anzeigen = new ArrayList<>();
        final List<Vek> drops = new ArrayList<>();
        int naechste = 0;

        public Block block(int x, int y, int z) {
            return bloecke.getOrDefault(x + "," + y + "," + z, Block.LUFT);
        }

        public List<? extends Pflanze> pflanzen() {
            List<Anzeige> lebend = new ArrayList<>();
            for (Anzeige a : anzeigen) if (a.lebt) lebend.add(a);
            return lebend;
        }

        public Pflanze erschaffe(Vek p, Set<String> m, String block, Darstellung d) {
            Anzeige a = new Anzeige(naechste++);
            a.pos = p;
            a.tags.addAll(m);
            a.block = block;
            a.t = d.verschiebung().clone();
            a.s = d.groesse().clone();
            a.l = d.drehungLinks().clone();
            a.r = d.drehungRechts().clone();
            anzeigen.add(a);
            return a;
        }

        public void entferne(Pflanze p) { ((Anzeige) p).lebt = false; }

        public void lasseFallen(Vek p, Pflanze von) { drops.add(p); }
    }

    static final class Spieler implements Gaertner.Gartenspieler {
        Vek pos;
        String haupt, neben;
        boolean kreativ;
        int haupt_verbraucht, neben_verbraucht;
        int nichtDrehbar;

        public Vek fuesse() { return pos; }
        public String haupthand() { return haupt; }
        public String nebenhand() { return neben; }
        public boolean kreativ() { return kreativ; }
        public void verbrauche(boolean n) { if (n) neben_verbraucht++; else haupt_verbraucht++; }
        public void nichtDrehbar() { nichtDrehbar++; }
    }

    static final Gaertner.Effekte KEINE_EFFEKTE = new Gaertner.Effekte() {
        public void einpflanzKlang(Gaertner.Gartenspieler s, boolean g) { }
        public void erdPartikel(Vek v) { }
        public void drehKlang(Gaertner.Gartenspieler s) { }
    };


    // ------------------------------------------------------------------
    // Ablauf
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Map<String, Object> daten = (Map<String, Object>) Json.lies(Files.readString(Path.of(args[0])));
        Map<String, Object> tags = (Map<String, Object>) Json.lies(Files.readString(Path.of(args[1])));

        java.util.function.Function<String, Set<String>> tag = name -> {
            Object werte = tags.get(name);
            Set<String> ergebnis = new HashSet<>();
            if (werte instanceof List<?> liste) for (Object o : liste) ergebnis.add((String) o);
            return ergebnis;
        };

        GartenDaten gartenDaten = new GartenDaten(daten, tag, tag);
        Gaertner gaertner = new Gaertner(gartenDaten, tag.apply("minecraft:leaves"));

        BufferedReader eingabe = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder ausgabe = new StringBuilder();
        String zeile;
        while ((zeile = eingabe.readLine()) != null) {
            if (zeile.isBlank()) continue;
            Map<String, Object> fall = (Map<String, Object>) Json.lies(zeile);
            ausgabe.append(fuehreAus(gaertner, fall)).append('\n');
        }
        System.out.print(ausgabe);
    }


    @SuppressWarnings("unchecked")
    static String fuehreAus(Gaertner gaertner, Map<String, Object> fall) {
        Welt welt = new Welt();
        for (Object o : (List<Object>) fall.get("bloecke")) {
            List<Object> b = (List<Object>) o;
            welt.bloecke.put(zahl(b.get(0)) + "," + zahl(b.get(1)) + "," + zahl(b.get(2)), block((String) b.get(3)));
        }
        for (Object o : (List<Object>) fall.get("anzeigen")) {
            Map<String, Object> a = (Map<String, Object>) o;
            Anzeige anzeige = (Anzeige) welt.erschaffe(vek(a.get("pos")), new LinkedHashSet<>((List<String>) a.get("tags")),
                    (String) a.get("block"), new Darstellung(new float[] {0, 0, 0}, new float[] {1, 1, 1},
                            Darstellung.KEINE_DREHUNG.clone(), Darstellung.KEINE_DREHUNG.clone()));
            anzeige.yaw = ((Number) a.get("yaw")).floatValue();
        }
        int vorher = welt.anzeigen.size();

        Map<String, Object> s = (Map<String, Object>) fall.get("spieler");
        Spieler spieler = new Spieler();
        spieler.pos = vek(s.get("pos"));
        spieler.haupt = (String) s.get("haupt");
        spieler.neben = (String) s.get("neben");
        spieler.kreativ = Boolean.TRUE.equals(s.get("kreativ"));

        switch ((String) fall.get("aktion")) {
            case "haupt" -> gaertner.einpflanzen(welt, spieler, KEINE_EFFEKTE);
            case "neben" -> gaertner.einpflanzenNebenhand(welt, spieler, KEINE_EFFEKTE);
            case "drehen" -> gaertner.drehen(welt, spieler, 15f, KEINE_EFFEKTE);
            case "drehen_neben" -> gaertner.drehen(welt, spieler, 7.5f, KEINE_EFFEKTE);
            case "kaputt" -> {
                for (GartenWelt.Pflanze p : new ArrayList<>(welt.pflanzen())) {
                    if (gaertner.topfFehlt(welt, p)) gaertner.abbauen(welt, p);
                }
            }
            default -> throw new IllegalArgumentException("aktion");
        }

        // Ergebnis
        StringBuilder sb = new StringBuilder("{\"anzeigen\":[");
        boolean erste = true;
        for (Anzeige a : welt.anzeigen) {
            if (!a.lebt) continue;
            if (!erste) sb.append(',');
            erste = false;
            sb.append("{\"pos\":").append(liste(a.pos.x(), a.pos.y(), a.pos.z()))
                    .append(",\"tags\":").append(texte(new TreeSet<>(a.tags)))
                    .append(",\"block\":").append(a.block == null ? "\"minecraft:air\"" : Json.text(normalBlock(a.block)))
                    .append(",\"yaw\":").append(a.yaw)
                    .append(",\"t\":").append(liste(a.t)).append(",\"s\":").append(liste(a.s))
                    .append(",\"l\":").append(liste(a.l)).append(",\"r\":").append(liste(a.r));
            sb.append(",\"alt\":").append(a.nr < vorher ? a.nr : -1).append('}');
        }
        sb.append("],\"verbraucht\":{\"mainhand\":").append(spieler.haupt_verbraucht)
                .append(",\"offhand\":").append(spieler.neben_verbraucht).append("}");
        sb.append(",\"drops\":[");
        for (int i = 0; i < welt.drops.size(); i++) {
            if (i > 0) sb.append(',');
            Vek d = welt.drops.get(i);
            sb.append(liste(d.x(), d.y(), d.z()));
        }
        sb.append("],\"nicht_drehbar\":").append(spieler.nichtDrehbar).append('}');
        return sb.toString();
    }


    static String normalBlock(String text) {
        GartenWelt.Block b = block(text);
        if (b.eigenschaften().isEmpty()) return b.id();
        StringBuilder sb = new StringBuilder(b.id()).append('[');
        boolean erste = true;
        for (Map.Entry<String, String> e : new TreeMap<>(b.eigenschaften()).entrySet()) {
            if (!erste) sb.append(',');
            erste = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.append(']').toString();
    }

    static GartenWelt.Block block(String text) {
        String id = text.replaceAll("\\[.*", "");
        if (!id.contains(":")) id = "minecraft:" + id;
        Map<String, String> props = new HashMap<>();
        int i = text.indexOf('[');
        if (i >= 0) {
            for (String paar : text.substring(i + 1, text.length() - 1).split(",")) {
                String[] kv = paar.split("=");
                props.put(kv[0], kv[1]);
            }
        }
        return new GartenWelt.Block(id, props);
    }

    @SuppressWarnings("unchecked")
    static Vek vek(Object o) {
        List<Object> l = (List<Object>) o;
        return new Vek(((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue(), ((Number) l.get(2)).doubleValue());
    }

    static int zahl(Object o) { return ((Number) o).intValue(); }

    static String liste(double... w) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < w.length; i++) { if (i > 0) sb.append(','); sb.append(w[i]); }
        return sb.append(']').toString();
    }

    static String liste(float[] w) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < w.length; i++) { if (i > 0) sb.append(','); sb.append(w[i]); }
        return sb.append(']').toString();
    }

    static String texte(Collection<String> w) {
        StringBuilder sb = new StringBuilder("[");
        boolean erste = true;
        for (String s : w) { if (!erste) sb.append(','); erste = false; sb.append(Json.text(s)); }
        return sb.append(']').toString();
    }


    // ------------------------------------------------------------------
    // Kleiner JSON-Leser (nur fuer den Test)
    // ------------------------------------------------------------------

    static final class Json {
        final String t;
        int i;

        Json(String t) { this.t = t; }

        static Object lies(String text) { Json j = new Json(text); Object o = j.wert(); return o; }

        static String text(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

        void leer() { while (i < t.length() && Character.isWhitespace(t.charAt(i))) i++; }

        Object wert() {
            leer();
            char c = t.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                leer();
                if (t.charAt(i) == '}') { i++; return m; }
                while (true) {
                    leer();
                    String k = (String) wert();
                    leer(); i++; // :
                    m.put(k, wert());
                    leer();
                    if (t.charAt(i++) == '}') return m;
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                leer();
                if (t.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(wert());
                    leer();
                    if (t.charAt(i++) == ']') return l;
                }
            }
            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (t.charAt(i) != '"') {
                    if (t.charAt(i) == '\\') { i++; char e = t.charAt(i); sb.append(e == 'n' ? '\n' : e == 'u' ? (char) Integer.parseInt(t.substring(i + 1, i + 5), 16) : e); if (e == 'u') i += 4; }
                    else sb.append(t.charAt(i));
                    i++;
                }
                i++;
                return sb.toString();
            }
            if (t.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (t.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (t.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < t.length() && "+-0123456789.eE".indexOf(t.charAt(i)) >= 0) i++;
            String zahl = t.substring(start, i);
            if (zahl.contains(".") || zahl.contains("e") || zahl.contains("E")) return Double.parseDouble(zahl);
            return Long.parseLong(zahl);
        }
    }
}

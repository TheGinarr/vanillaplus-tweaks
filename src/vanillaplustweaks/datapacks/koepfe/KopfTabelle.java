package vanillaplustweaks.datapacks.koepfe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Die Kopf-Liste aus "daten/koepfe.yml" – ohne Bukkit, damit sie sich auch ausserhalb
 * des Servers pruefen laesst.
 *
 * Pro Tier gibt es eine geordnete Liste von Varianten. Gewaehlt wird die ERSTE Variante,
 * deren Bedingungen ("wenn") alle zum Tier passen. Eine Variante ohne Bedingung passt immer.
 *
 * @param <K> Typ des fertigen Kopfes (im Plugin ein ItemStack, im Test ein Text)
 */
public final class KopfTabelle<K> {

    /** Eine Variante: Bedingungen (z. B. variante=cold) und der Kopf, der dann droppt. */
    public record Variante<K>(Map<String, String> wenn, K kopf) {
    }

    /** Zusaetzlicher Kopf mit eigener Chance (nur beim Wither: einer von zwei Geschoss-Koepfen). */
    public record Zusatz<K>(double chance, List<K> zufaelligEinerVon) {
    }

    private final Map<String, List<Variante<K>>> variantenProTier = new HashMap<>();
    private final Map<String, Zusatz<K>> zusatzProTier = new HashMap<>();


    /**
     * Baut die Tabelle aus den gelesenen YAML-Daten.
     *
     * @param daten     Inhalt der koepfe.yml (Tier -> {varianten: [...], zusatz: {...}})
     * @param kopfBauen macht aus dem Item-Text den fertigen Kopf
     */
    public static <K> KopfTabelle<K> aus(Map<String, ?> daten, Function<String, K> kopfBauen) {
        KopfTabelle<K> tabelle = new KopfTabelle<>();

        for (Map.Entry<String, ?> tierEintrag : daten.entrySet()) {
            Map<?, ?> tier = (Map<?, ?>) tierEintrag.getValue();

            List<Variante<K>> varianten = new ArrayList<>();
            for (Object v : (List<?>) tier.get("varianten")) {
                Map<?, ?> variante = (Map<?, ?>) v;
                varianten.add(new Variante<>(bedingungen(variante.get("wenn")), kopfBauen.apply(String.valueOf(variante.get("item")))));
            }
            tabelle.variantenProTier.put(tierEintrag.getKey(), varianten);

            if (tier.get("zusatz") instanceof Map<?, ?> zusatz) {
                List<K> auswahl = new ArrayList<>();
                for (Object item : (List<?>) zusatz.get("zufaellig-einer-von")) {
                    auswahl.add(kopfBauen.apply(String.valueOf(item)));
                }
                double chance = ((Number) zusatz.get("chance")).doubleValue();
                tabelle.zusatzProTier.put(tierEintrag.getKey(), new Zusatz<>(chance, auswahl));
            }
        }
        return tabelle;
    }


    private static Map<String, String> bedingungen(Object wenn) {
        Map<String, String> ergebnis = new HashMap<>();
        if (wenn instanceof Map<?, ?> karte) {
            for (Map.Entry<?, ?> eintrag : karte.entrySet()) {
                // Ja/Nein-Werte als "true"/"false", Texte wie sie sind
                ergebnis.put(String.valueOf(eintrag.getKey()), String.valueOf(eintrag.getValue()));
            }
        }
        return ergebnis;
    }


    public boolean kennt(String tier) {
        return variantenProTier.containsKey(tier);
    }


    public int anzahlTiere() {
        return variantenProTier.size();
    }


    /**
     * Der passende Kopf fuer ein Tier – oder null, wenn keine Variante passt.
     *
     * @param eigenschaft liefert zu einem Bedingungs-Namen (z. B. "variante") den Wert des Tiers, oder null
     */
    public K kopfFuer(String tier, Function<String, String> eigenschaft) {
        for (Variante<K> variante : variantenProTier.getOrDefault(tier, List.of())) {
            if (passt(variante.wenn(), eigenschaft)) {
                return variante.kopf();
            }
        }
        return null;
    }


    public Zusatz<K> zusatzFuer(String tier) {
        return zusatzProTier.get(tier);
    }


    private static boolean passt(Map<String, String> wenn, Function<String, String> eigenschaft) {
        for (Map.Entry<String, String> bedingung : wenn.entrySet()) {
            if (!bedingung.getValue().equals(eigenschaft.apply(bedingung.getKey()))) {
                return false;
            }
        }
        return true;
    }
}

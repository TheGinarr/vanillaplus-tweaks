package vanillaplustweaks.datapacks;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Die Verzauberungs-Regeln der gepanzerten Elytra – genau wie im Datapack "Armored Elytra".
 *
 * Bewusst ohne Minecraft-Klassen geschrieben: So laesst sich die Logik unabhaengig vom Server
 * testen. Der Schluessel K ist im Plugin die Verzauberung, im Test einfach ein Text.
 */
public final class ElytraVerzauberungen {

    private ElytraVerzauberungen() {
        // nur statische Methoden
    }


    /**
     * Zusammenbauen: Elytra und Brustplatte werden vereint.
     * Hat beide dieselbe Verzauberung, gewinnt die hoehere Stufe. Alle anderen werden uebernommen.
     */
    public static <K> Map<K, Integer> zusammenfuehren(Map<K, Integer> elytra, Map<K, Integer> brustplatte) {
        Map<K, Integer> ergebnis = new LinkedHashMap<>(brustplatte);

        for (Map.Entry<K, Integer> eintrag : elytra.entrySet()) {
            ergebnis.merge(eintrag.getKey(), eintrag.getValue(), Math::max);
        }
        return ergebnis;
    }


    /** Ergebnis beim Zerlegen: welche Verzauberungen bekommt welches Teil zurueck? */
    public record Aufteilung<K>(Map<K, Integer> elytra, Map<K, Integer> brustplatte) {
    }


    /**
     * Zerlegen: Die gepanzerte Elytra kann nach dem Zusammenbau weiter verzaubert worden sein
     * (Amboss, Buecher). Die aktuellen Verzauberungen werden deshalb so verteilt:
     *
     *   1. Verzauberungen, die BEIDE Teile vor dem Zusammenbau hatten:
     *      Das Teil mit der niedrigeren Stufe bekommt seine alte Stufe zurueck.
     *      (Bei Gleichstand gilt das fuer die Brustplatte.)
     *      Das andere Teil bekommt im zweiten Schritt die aktuelle Stufe.
     *   2. Jede aktuelle Verzauberung, die noch zur Brustplatte gehoert, geht an die Brustplatte.
     *      Alles andere – auch spaeter hinzugekommene Verzauberungen – geht an die Elytra.
     *
     * @param aktuell         Verzauberungen der gepanzerten Elytra jetzt
     * @param elytraVorher    Verzauberungen der Elytra beim Zusammenbau
     * @param brustVorher     Verzauberungen der Brustplatte beim Zusammenbau
     */
    public static <K> Aufteilung<K> aufteilen(Map<K, Integer> aktuell,
                                              Map<K, Integer> elytraVorher,
                                              Map<K, Integer> brustVorher) {

        Map<K, Integer> elytraErgebnis = new LinkedHashMap<>();
        Map<K, Integer> brustErgebnis = new LinkedHashMap<>();

        // Was von der Brustplatte im zweiten Schritt noch zugeordnet werden darf
        Map<K, Integer> brustOffen = new HashMap<>(brustVorher);

        // Schritt 1: gemeinsame Verzauberungen
        if (!elytraVorher.isEmpty() && !brustVorher.isEmpty()) {
            for (Map.Entry<K, Integer> elytraEintrag : elytraVorher.entrySet()) {
                K verzauberung = elytraEintrag.getKey();
                Integer brustStufe = brustOffen.get(verzauberung);
                if (brustStufe == null) {
                    continue;
                }

                int elytraStufe = elytraEintrag.getValue();
                if (brustStufe > elytraStufe) {
                    // Elytra war schwaecher -> behaelt ihre alte Stufe
                    elytraErgebnis.put(verzauberung, elytraStufe);
                } else {
                    // Brustplatte war schwaecher (oder gleich) -> behaelt ihre alte Stufe
                    brustErgebnis.put(verzauberung, brustStufe);
                    brustOffen.remove(verzauberung);
                }
            }
        }

        // Schritt 2: aktuelle Verzauberungen verteilen
        for (Map.Entry<K, Integer> eintrag : aktuell.entrySet()) {
            K verzauberung = eintrag.getKey();

            if (brustOffen.remove(verzauberung) != null) {
                brustErgebnis.put(verzauberung, eintrag.getValue());
            } else {
                elytraErgebnis.put(verzauberung, eintrag.getValue());
            }
        }

        return new Aufteilung<>(elytraErgebnis, brustErgebnis);
    }
}

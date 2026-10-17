package vanillaplustweaks.datapacks.koepfe;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bukkit liest verschachtelte YAML-Abschnitte als "ConfigurationSection" ein.
 * Fuer die Kopf-Tabelle werden daraus normale Java-Maps (Listen bleiben Listen).
 */
public final class YamlKarten {

    private YamlKarten() {
    }


    public static Map<String, Object> alsKarte(ConfigurationSection abschnitt) {
        Map<String, Object> karte = new LinkedHashMap<>();
        for (String schluessel : abschnitt.getKeys(false)) {
            Object wert = abschnitt.get(schluessel);
            if (wert instanceof ConfigurationSection unterabschnitt) {
                wert = alsKarte(unterabschnitt);
            }
            karte.put(schluessel, wert);
        }
        return karte;
    }
}

package vanillaplustweaks;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Gemeinsame Grundlage fuer alle Funktionen des Plugins.
 *
 * Jedes Modul
 *   - hat einen Abschnitt in der config.yml (z. B. "harvest:"),
 *   - kann dort mit "enabled: false" abgeschaltet werden,
 *   - liest seine Einstellungen in {@link #start()} und registriert dann seine Listener und Timer.
 *
 * Module, die auch {@link NeuLadbar} sind, uebernehmen geaenderte Werte bei "/vptweaks reload".
 * Alle anderen brauchen einen Server-Neustart.
 */
public abstract class Modul {

    protected final VanillaPlusTweaks plugin;


    protected Modul(VanillaPlusTweaks plugin) {
        this.plugin = plugin;
    }


    /**
     * Name des Config-Abschnitts, z. B. "harvest".
     * Wird auch im Log bei "Aktive Module: ..." angezeigt.
     */
    public abstract String configAbschnitt();


    /** Aktiviert das Modul: Einstellungen lesen, Listener und Timer registrieren. */
    public abstract void start();


    /** Steht in der Config "enabled: false", wird das Modul gar nicht gestartet. */
    public boolean istEingeschaltet() {
        return config().getBoolean(configAbschnitt() + ".enabled", true);
    }


    // ------------------------------------------------------------------
    // Kleine Helfer, damit der Code in den Modulen kurz und lesbar bleibt
    // ------------------------------------------------------------------

    protected FileConfiguration config() {
        return plugin.getConfig();
    }


    /** Liest eine Zahl aus dem eigenen Abschnitt, z. B. liest zahl("seconds", 150) im AFK-Modul "afk.seconds". */
    protected int zahl(String schluessel, int standard) {
        return config().getInt(configAbschnitt() + "." + schluessel, standard);
    }


    protected double kommazahl(String schluessel, double standard) {
        return config().getDouble(configAbschnitt() + "." + schluessel, standard);
    }


    protected boolean schalter(String schluessel, boolean standard) {
        return config().getBoolean(configAbschnitt() + "." + schluessel, standard);
    }


    protected String text(String schluessel, String standard) {
        return config().getString(configAbschnitt() + "." + schluessel, standard);
    }


    protected java.util.List<String> textListe(String schluessel) {
        return config().getStringList(configAbschnitt() + "." + schluessel);
    }


    /**
     * Liest eine mitgelieferte Daten-Datei aus dem Plugin (Ordner "daten" in der Jar),
     * z. B. die Rezept- oder Kopf-Liste. Fehlt sie, gibt es eine leere Liste.
     */
    protected YamlConfiguration datenDatei(String name) {
        try (InputStream strom = plugin.getResource("daten/" + name)) {
            if (strom == null) {
                warnung("Daten-Datei fehlt in der Jar: daten/" + name);
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(strom, StandardCharsets.UTF_8));
        } catch (IOException fehler) {
            warnung("Daten-Datei daten/" + name + " nicht lesbar: " + fehler);
            return new YamlConfiguration();
        }
    }


    /**
     * Schluessel mit dem frueheren Namensraum des Plugins (Einstellung "legacy-namespace"),
     * damit gespeicherte Daten aus einer Version mit anderem Plugin-Namen weiter gelesen werden.
     * Leer = kein frueherer Name -> null.
     */
    protected org.bukkit.NamespacedKey alterSchluessel(String name) {
        String namensraum = config().getString("legacy-namespace", "").trim().toLowerCase(java.util.Locale.ROOT);
        if (namensraum.isEmpty()) {
            return null;
        }
        return new org.bukkit.NamespacedKey(namensraum, name);
    }


    protected void registriereListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }


    /** Fuehrt eine Aufgabe regelmaessig aus. 20 Ticks = 1 Sekunde. */
    protected void wiederholeAlle(long ticks, Runnable aufgabe) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, aufgabe, ticks, ticks);
    }


    /** Fuehrt eine Aufgabe einmal nach der angegebenen Zahl Ticks aus. */
    protected void spaeter(long ticks, Runnable aufgabe) {
        plugin.getServer().getScheduler().runTaskLater(plugin, aufgabe, ticks);
    }


    protected void log(String nachricht) {
        plugin.getLogger().info("[" + configAbschnitt() + "] " + nachricht);
    }


    protected void warnung(String nachricht) {
        plugin.getLogger().warning("[" + configAbschnitt() + "] " + nachricht);
    }
}

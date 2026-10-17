package vanillaplustweaks.allgemein;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;
import java.util.TimeZone;
import java.util.logging.Logger;

/**
 * Stellt die Standard-Zeitzone von Java auf die Zeitzone aus der Config (Standard: Europe/Berlin).
 *
 * Warum so frueh?
 *   Paper ruft onLoad() aller Plugins auf, bevor irgendein Plugin aktiviert wird.
 *   TAB baut seine Datums- und Uhrzeitformate erst beim Aktivieren und uebernimmt
 *   damit automatisch die hier gesetzte Zeitzone – inklusive Sommer- und Winterzeit.
 *   Ein Java-Startparameter (-Duser.timezone=...) ist dadurch nicht noetig.
 */
public final class Zeitzone {

    private static ZoneId aktuelleZone = ZoneId.of("Europe/Berlin");


    private Zeitzone() {
        // nur statische Methoden
    }


    /** Wird aus VanillaPlusTweaks.onLoad() aufgerufen. */
    public static void anwenden(FileConfiguration config, Logger logger) {
        String name = config.getString("timezone", "Europe/Berlin");

        if (name == null || name.isBlank()) {
            logger.info("Keine Zeitzone eingestellt – Java-Standard bleibt unveraendert.");
            return;
        }

        try {
            aktuelleZone = ZoneId.of(name.trim());
            TimeZone.setDefault(TimeZone.getTimeZone(aktuelleZone));
            logger.info("Standard-Zeitzone gesetzt: " + aktuelleZone);
        } catch (Exception fehler) {
            logger.warning("Ungueltige Zeitzone '" + name + "' – unveraendert gelassen (" + fehler.getMessage() + ")");
        }
    }


    /** Die Zeitzone, in der auch die Freischalt-Termine fuer Nether und End gelesen werden. */
    public static ZoneId zone() {
        return aktuelleZone;
    }
}

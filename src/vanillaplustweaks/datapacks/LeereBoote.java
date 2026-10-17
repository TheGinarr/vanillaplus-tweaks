package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ersetzt das Datapack "Kill Empty Boats" (Vanilla Tweaks) und erweitert es.
 *
 * 1. Automatisch: Boote, die eine einstellbare Zeit lang leer sind und neben denen niemand steht,
 *    werden entfernt. Geprueft wird einmal pro Minute.
 * 2. Von Hand: "/vptweaks boote" entfernt sofort alle leeren Boote (wie das Datapack).
 *
 * Geschuetzt (einstellbar): Boote mit Namen, Kistenboote mit Inhalt und angeleinte Boote.
 * Die Zeit laeuft nur, solange das Boot in einem geladenen Bereich ist.
 */
public final class LeereBoote extends Modul {

    private static final long PRUEFEN_ALLE_TICKS = 20 * 60;

    /** Fuer den Befehl: wie im Datapack bleibt ein Boot stehen, wenn ein Spieler naeher als 1 Block ist. */
    private static final double BEFEHL_SCHUTZABSTAND = 1.0;

    private boolean automatisch;
    private long leerZeitMillis;
    private double spielerAbstand;
    private boolean benannteBehalten;
    private boolean kistenbooteMitInhaltBehalten;
    private boolean angeleinteBehalten;

    /** Seit wann ist welches Boot leer? */
    private final Map<UUID, Long> leerSeit = new HashMap<>();


    public LeereBoote(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "empty-boats";
    }


    @Override
    public void start() {
        automatisch = schalter("auto-remove", true);
        leerZeitMillis = zahl("empty-minutes", 10) * 60_000L;
        spielerAbstand = kommazahl("keep-if-player-within", 16);
        benannteBehalten = schalter("keep-named", true);
        kistenbooteMitInhaltBehalten = schalter("keep-chest-boats-with-items", true);
        angeleinteBehalten = schalter("keep-leashed", true);

        if (automatisch && leerZeitMillis > 0) {
            wiederholeAlle(PRUEFEN_ALLE_TICKS, this::automatischPruefen);
            log("Leere Boote werden nach " + (leerZeitMillis / 60_000) + " Minuten entfernt.");
        }
    }


    // ------------------------------------------------------------------
    // Automatisch nach Zeit
    // ------------------------------------------------------------------

    private void automatischPruefen() {
        long jetzt = System.currentTimeMillis();
        Set<UUID> nochVorhanden = new HashSet<>();
        int entfernt = 0;

        for (World welt : plugin.getServer().getWorlds()) {
            for (Boat boot : welt.getEntitiesByClass(Boat.class)) {
                UUID id = boot.getUniqueId();

                if (!darfWeg(boot, spielerAbstand)) {
                    leerSeit.remove(id); // wird benutzt oder ist geschuetzt -> Zeit von vorn
                    continue;
                }

                nochVorhanden.add(id);
                long seit = leerSeit.computeIfAbsent(id, unbenutzt -> jetzt);

                if (jetzt - seit >= leerZeitMillis) {
                    boot.remove();
                    leerSeit.remove(id);
                    nochVorhanden.remove(id);
                    entfernt++;
                }
            }
        }

        // Boote, die abgebaut wurden oder nicht mehr geladen sind, vergessen
        leerSeit.keySet().retainAll(nochVorhanden);

        if (entfernt > 0) {
            log(entfernt + " leere Boote entfernt.");
        }
    }


    // ------------------------------------------------------------------
    // Von Hand: /vptweaks boote
    // ------------------------------------------------------------------

    /** Entfernt sofort alle leeren Boote in allen Welten und gibt die Anzahl zurueck. */
    public int leereBooteEntfernen() {
        int entfernt = 0;

        for (World welt : plugin.getServer().getWorlds()) {
            for (Boat boot : welt.getEntitiesByClass(Boat.class)) {
                if (darfWeg(boot, BEFEHL_SCHUTZABSTAND)) {
                    leerSeit.remove(boot.getUniqueId());
                    boot.remove();
                    entfernt++;
                }
            }
        }
        return entfernt;
    }


    // ------------------------------------------------------------------
    // Regeln
    // ------------------------------------------------------------------

    /** Leer, nicht geschuetzt und kein Spieler in der Naehe. */
    private boolean darfWeg(Boat boot, double abstand) {
        if (!boot.getPassengers().isEmpty()) {
            return false; // jemand sitzt drin (auch Tiere zaehlen)
        }
        if (benannteBehalten && boot.customName() != null) {
            return false;
        }
        if (angeleinteBehalten && boot.isLeashed()) {
            return false;
        }
        if (kistenbooteMitInhaltBehalten && boot instanceof ChestBoat kistenboot && !kistenboot.getInventory().isEmpty()) {
            return false;
        }
        return !spielerInDerNaehe(boot, abstand);
    }


    private static boolean spielerInDerNaehe(Boat boot, double abstand) {
        if (abstand <= 0) {
            return false;
        }

        for (Entity nachbar : boot.getNearbyEntities(abstand, abstand, abstand)) {
            if (nachbar instanceof Player) {
                return true;
            }
        }
        return false;
    }
}

package vanillaplustweaks.allgemein;

import vanillaplustweaks.Modul;
import vanillaplustweaks.NeuLadbar;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sperrt Nether und End bis zu einem festen Termin.
 *
 * Bis zum Termin bricht das Modul jede Portal-Reise ab, deren Ziel die gesperrte Dimension ist
 * (Spieler ueber PlayerPortalEvent, Items und Mobs ueber EntityPortalEvent).
 * Laut Paper-Code findet bei einem abgebrochenen Portal-Event keine Teleportation statt.
 *
 * Ab dem Termin ist die Dimension automatisch offen – ohne Neustart.
 * Alle 5 Sekunden wird geprueft, ob ein Termin erreicht ist; dann gibt es eine Chat-Meldung.
 */
public final class DimensionsSperre extends Modul implements Listener, NeuLadbar {

    private static final DateTimeFormatter ANZEIGE = DateTimeFormatter.ofPattern("dd.MM. 'um' HH:mm 'Uhr'");
    private static final long MELDUNG_HOECHSTENS_ALLE_MS = 5000;

    private ZonedDateTime netherOffenAb;
    private ZonedDateTime endOffenAb;

    private boolean netherWarSchonOffen;
    private boolean endWarSchonOffen;

    /** Damit ein Spieler im Portal nicht jede Sekunde dieselbe Meldung bekommt. */
    private final Map<UUID, Long> letzteMeldung = new HashMap<>();


    public DimensionsSperre(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "dimension-unlock";
    }


    @Override
    public void start() {
        neuLaden();

        netherWarSchonOffen = istOffen(netherOffenAb);
        endWarSchonOffen = istOffen(endOffenAb);

        registriereListener(this);
        wiederholeAlle(100, this::pruefeFreischaltung);

        log("Nether: " + status(netherOffenAb) + " | End: " + status(endOffenAb));
    }


    @Override
    public void neuLaden() {
        netherOffenAb = leseTermin(text("nether", ""));
        endOffenAb = leseTermin(text("end", ""));
    }


    // ------------------------------------------------------------------
    // Status fuer Platzhalter und Befehle
    // ------------------------------------------------------------------

    /** z. B. "ab 24.10. um 20:00 Uhr" oder "offen" */
    public String netherStatus() {
        return status(netherOffenAb);
    }


    public String endStatus() {
        return status(endOffenAb);
    }


    // ------------------------------------------------------------------
    // Portale abfangen
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void beiSpielerPortal(PlayerPortalEvent event) {
        Location ziel = event.getTo();
        if (!istGesperrt(ziel)) {
            return;
        }

        Player spieler = event.getPlayer();
        if (spieler.hasPermission("vptweaks.dimension.bypass")) {
            return;
        }

        event.setCancelled(true);
        meldeSperre(spieler, ziel.getWorld().getEnvironment());
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void beiEntityPortal(EntityPortalEvent event) {
        if (istGesperrt(event.getTo())) {
            event.setCancelled(true);
        }
    }


    /** True, wenn das Portal-Ziel in einer noch gesperrten Dimension liegt. */
    public boolean istGesperrt(Location ziel) {
        if (ziel == null || ziel.getWorld() == null) {
            return false;
        }

        World.Environment dimension = ziel.getWorld().getEnvironment();

        if (dimension == World.Environment.NETHER) {
            return !istOffen(netherOffenAb);
        }
        if (dimension == World.Environment.THE_END) {
            return !istOffen(endOffenAb);
        }
        return false;
    }


    private void meldeSperre(Player spieler, World.Environment dimension) {
        long jetzt = System.currentTimeMillis();
        Long zuletzt = letzteMeldung.get(spieler.getUniqueId());

        if (zuletzt != null && jetzt - zuletzt < MELDUNG_HOECHSTENS_ALLE_MS) {
            return;
        }
        letzteMeldung.put(spieler.getUniqueId(), jetzt);

        if (dimension == World.Environment.NETHER) {
            spieler.sendMessage("§cDer Nether ist noch gesperrt – geöffnet " + netherStatus() + ".");
        } else {
            spieler.sendMessage("§cDas End ist noch gesperrt – geöffnet " + endStatus() + ".");
        }
    }


    // ------------------------------------------------------------------
    // Automatische Freischaltung
    // ------------------------------------------------------------------

    private void pruefeFreischaltung() {
        if (!netherWarSchonOffen && istOffen(netherOffenAb)) {
            netherWarSchonOffen = true;
            ankuendigen("§6§lDer Nether ist jetzt geöffnet!");
        }

        if (!endWarSchonOffen && istOffen(endOffenAb)) {
            endWarSchonOffen = true;
            ankuendigen("§5§lDas End ist jetzt geöffnet!");
        }
    }


    private void ankuendigen(String nachricht) {
        log(nachricht.replaceAll("§.", ""));

        for (Player spieler : plugin.getServer().getOnlinePlayers()) {
            spieler.sendMessage(nachricht);
        }
    }


    // ------------------------------------------------------------------
    // Termine
    // ------------------------------------------------------------------

    /**
     * Liest einen Termin im Format 2026-10-24T20:00 (Ortszeit der eingestellten Zeitzone).
     * Leer = sofort offen. Fehlerhaftes Format = sicherheitshalber gesperrt.
     */
    private ZonedDateTime leseTermin(String eingabe) {
        if (eingabe == null || eingabe.isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(eingabe.trim()).atZone(Zeitzone.zone());
        } catch (Exception fehler) {
            warnung("Ungueltiges Datum '" + eingabe + "' (Format: 2026-10-24T20:00) – Dimension bleibt GESPERRT.");
            return ZonedDateTime.now(Zeitzone.zone()).plusYears(100);
        }
    }


    private boolean istOffen(ZonedDateTime termin) {
        return termin == null || !ZonedDateTime.now(Zeitzone.zone()).isBefore(termin);
    }


    private String status(ZonedDateTime termin) {
        if (istOffen(termin)) {
            return "offen";
        }
        return "ab " + ANZEIGE.format(termin);
    }
}

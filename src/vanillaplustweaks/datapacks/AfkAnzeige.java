package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ersetzt das Datapack "AFK Display" (Vanilla Tweaks).
 *
 * Wer eine bestimmte Zeit lang nicht von der Stelle kommt, landet im grauen Team "vpt_afk"
 * und erscheint dadurch grau in der Tab-Liste. Sobald er sich bewegt, wird er wieder normal angezeigt.
 *
 * Unterschied zum Datapack: Es wird nur alle 5 Sekunden geprueft statt mit einer Befehlsschleife.
 */
public final class AfkAnzeige extends Modul {

    private static final String TEAM_NAME = "vpt_afk";

    /** Unter diesem Abstand (in Bloecken) gilt man als "nicht bewegt". */
    private static final double KLEINSTE_BEWEGUNG = 0.01;

    private long afkNachMillis;

    private final Map<UUID, Location> letztePosition = new HashMap<>();
    private final Map<UUID, Long> letzteBewegung = new HashMap<>();


    public AfkAnzeige(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "afk";
    }


    @Override
    public void start() {
        afkNachMillis = zahl("seconds", 150) * 1000L;
        wiederholeAlle(100, this::pruefen);
    }


    private void pruefen() {
        long jetzt = System.currentTimeMillis();
        Team afkTeam = afkTeam();

        for (Player spieler : plugin.getServer().getOnlinePlayers()) {
            UUID id = spieler.getUniqueId();
            Location hier = spieler.getLocation();
            Location vorher = letztePosition.put(id, hier);

            if (hatSichBewegt(vorher, hier)) {
                letzteBewegung.put(id, jetzt);
                afkTeam.removeEntry(spieler.getName());
                continue;
            }

            long stillSeit = jetzt - letzteBewegung.getOrDefault(id, jetzt);
            if (stillSeit >= afkNachMillis && !afkTeam.hasEntry(spieler.getName())) {
                afkTeam.addEntry(spieler.getName());
            }
        }
    }


    private static boolean hatSichBewegt(Location vorher, Location hier) {
        if (vorher == null || vorher.getWorld() != hier.getWorld()) {
            return true;
        }

        return Math.abs(vorher.getX() - hier.getX()) > KLEINSTE_BEWEGUNG
                || Math.abs(vorher.getY() - hier.getY()) > KLEINSTE_BEWEGUNG
                || Math.abs(vorher.getZ() - hier.getZ()) > KLEINSTE_BEWEGUNG;
    }


    /** Holt das AFK-Team vom Haupt-Scoreboard oder legt es beim ersten Mal an. */
    private Team afkTeam() {
        Scoreboard scoreboard = plugin.getServer().getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);

        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM_NAME);
            team.color(NamedTextColor.GRAY);
        }
        return team;
    }
}

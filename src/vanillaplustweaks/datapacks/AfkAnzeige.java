package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ersetzt das Datapack "AFK Display" (Vanilla Tweaks).
 *
 * Wer eine bestimmte Zeit lang nicht von der Stelle kommt, wird in der Tab-Liste markiert,
 * standardmaessig mit durchgestrichenem Namen und dem Zusatz "AFK". Sobald er sich bewegt,
 * steht dort wieder der normale Name.
 *
 * Die Markierung ist nur der Tab-Name des Spielers. Sie wird nirgends gespeichert und ist
 * nach einem Neustart oder Neu-Einloggen automatisch weg.
 *
 * Zusaetzlich kann sich jeder Spieler mit /afk sofort selbst als AFK markieren (und mit /afk
 * wieder zurueckmelden). Auch dann endet die Markierung, sobald er sich bewegt.
 *
 * Unterschied zum Datapack: Es wird nur alle 5 Sekunden geprueft statt mit einer Befehlsschleife,
 * und es gibt kein Team mehr, in dem man "haengen bleiben" kann.
 */
public final class AfkAnzeige extends Modul implements Listener {

    /**
     * Teams aus frueheren Versionen: "afkDis.afk" vom Datapack, "tb_afk" und "vpt_afk" von aelteren
     * Plugin-Versionen. Wer beim Umstieg darin stand, bliebe sonst fuer immer grau.
     */
    private static final List<String> ALTE_TEAMS = List.of("afkDis.afk", "tb_afk", "vpt_afk");

    /** Unter diesem Abstand (in Bloecken) gilt man als "nicht bewegt". */
    private static final double KLEINSTE_BEWEGUNG = 0.01;

    private long afkNachMillis;
    private String anzeige;
    private boolean befehlMelden;

    private final Map<UUID, Location> letztePosition = new HashMap<>();
    private final Map<UUID, Long> letzteBewegung = new HashMap<>();
    private final Set<UUID> afk = new HashSet<>();

    /** Wer sich selbst mit /afk gemeldet hat – nur fuer diese gibt es "ist wieder da". */
    private final Set<UUID> perBefehl = new HashSet<>();


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
        anzeige = text("tab-name", "<st><name></st> <gray>AFK</gray>");
        befehlMelden = schalter("command-announce", true);

        alteTeamsEntfernen();
        registriereListener(this);
        registriereBefehl();
        wiederholeAlle(100, this::pruefen);
    }


    // ------------------------------------------------------------------
    // Befehl /afk
    // ------------------------------------------------------------------

    private void registriereBefehl() {
        plugin.registerCommand("afk", "Markiert dich als AFK (nochmal /afk: zurueck)", new BasicCommand() {

            @Override
            public void execute(CommandSourceStack quelle, String[] argumente) {
                if (quelle.getSender() instanceof Player spieler) {
                    umschalten(spieler);
                } else {
                    quelle.getSender().sendMessage("Nur fuer Spieler.");
                }
            }

            @Override
            public String permission() {
                return "vptweaks.afk";
            }
        });
    }


    private void umschalten(Player spieler) {
        UUID id = spieler.getUniqueId();

        if (afk.contains(id)) {
            zurueck(spieler);
            return;
        }

        // Ab jetzt zaehlt nur Bewegung NACH dem Befehl
        letztePosition.put(id, spieler.getLocation());
        perBefehl.add(id);
        markieren(spieler);
        if (befehlMelden) {
            plugin.getServer().broadcast(Component.text(spieler.getName() + " ist jetzt AFK.", NamedTextColor.GRAY));
        }
    }


    private void markieren(Player spieler) {
        if (afk.add(spieler.getUniqueId())) {
            spieler.playerListName(afkName(spieler));
        }
    }


    private void zurueck(Player spieler) {
        UUID id = spieler.getUniqueId();
        letzteBewegung.put(id, System.currentTimeMillis());

        if (afk.remove(id)) {
            spieler.playerListName(null); // zurueck zum normalen Namen
            if (befehlMelden && perBefehl.remove(id)) {
                plugin.getServer().broadcast(Component.text(spieler.getName() + " ist wieder da.", NamedTextColor.GRAY));
            }
        }
        perBefehl.remove(id);
    }


    private void pruefen() {
        long jetzt = System.currentTimeMillis();

        for (Player spieler : plugin.getServer().getOnlinePlayers()) {
            UUID id = spieler.getUniqueId();
            Location hier = spieler.getLocation();
            Location vorher = letztePosition.put(id, hier);

            if (hatSichBewegt(vorher, hier)) {
                zurueck(spieler);
                continue;
            }

            long stillSeit = jetzt - letzteBewegung.getOrDefault(id, jetzt);
            if (stillSeit >= afkNachMillis) {
                markieren(spieler);
            }
        }
    }


    @EventHandler
    public void beimVerlassen(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        letztePosition.remove(id);
        letzteBewegung.remove(id);
        afk.remove(id);
        perBefehl.remove(id);
    }


    private Component afkName(Player spieler) {
        return MiniMessage.miniMessage().deserialize(anzeige, Placeholder.unparsed("name", spieler.getName()));
    }


    private static boolean hatSichBewegt(Location vorher, Location hier) {
        if (vorher == null || vorher.getWorld() != hier.getWorld()) {
            return true;
        }

        return Math.abs(vorher.getX() - hier.getX()) > KLEINSTE_BEWEGUNG
                || Math.abs(vorher.getY() - hier.getY()) > KLEINSTE_BEWEGUNG
                || Math.abs(vorher.getZ() - hier.getZ()) > KLEINSTE_BEWEGUNG;
    }


    private void alteTeamsEntfernen() {
        Scoreboard scoreboard = plugin.getServer().getScoreboardManager().getMainScoreboard();

        for (String name : ALTE_TEAMS) {
            Team team = scoreboard.getTeam(name);
            if (team != null) {
                log("Altes AFK-Team '" + name + "' entfernt (" + team.getEntries().size() + " Eintraege).");
                team.unregister();
            }
        }
    }
}

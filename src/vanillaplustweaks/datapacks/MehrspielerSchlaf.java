package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;

/**
 * Ersetzt das Datapack "Multiplayer Sleep" (Vanilla Tweaks).
 *
 * Das Datapack baute auf der Minecraft-Spielregel "players_sleeping_percentage" auf.
 * Dieses Modul setzt dieselbe Spielregel (wenn gewuenscht) und schreibt in den Chat,
 * wer schlafen geht und ob die Nacht uebersprungen wird, z. B.
 * "Steve geht schlafen – die Nacht wird übersprungen" oder "... – noch 1 Spieler muss schlafen".
 */
public final class MehrspielerSchlaf extends Modul implements Listener {

    private boolean meldungImChat;


    public MehrspielerSchlaf(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "sleep";
    }


    @Override
    public void start() {
        meldungImChat = schalter("announce", true);
        setzeSpielregel(zahl("percentage", -1));
        registriereListener(this);
    }


    /** -1 = Spielregel nicht anfassen. 0 bis 100 = Prozent der Spieler, die schlafen muessen. */
    private void setzeSpielregel(int prozent) {
        if (prozent < 0 || prozent > 100) {
            return;
        }

        for (World welt : plugin.getServer().getWorlds()) {
            welt.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, prozent);
        }
        log("Spielregel players_sleeping_percentage = " + prozent);
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimSchlafengehen(PlayerBedEnterEvent event) {
        if (!meldungImChat || event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }

        Player schlaefer = event.getPlayer();
        World welt = schlaefer.getWorld();

        // Gezaehlt wird wie in Minecraft selbst (SleepStatus): Zuschauer zaehlen nicht mit,
        // Spieler mit "sleepingIgnored" zaehlen als schlafend.
        int aktiveSpieler = 0;
        int schlafende = 1; // der Spieler selbst liegt ab jetzt im Bett

        for (Player anderer : welt.getPlayers()) {
            if (anderer.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            aktiveSpieler++;
            if (anderer != schlaefer && (anderer.isSleeping() || anderer.isSleepingIgnored())) {
                schlafende++;
            }
        }

        Integer prozent = welt.getGameRuleValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        int benoetigt = benoetigteSchlaefer(aktiveSpieler, prozent == null ? 100 : prozent);
        int fehlen = benoetigt - schlafende;

        String nachricht;
        if (benoetigt > aktiveSpieler) {
            nachricht = "§7" + schlaefer.getName() + " geht schlafen.";
        } else if (fehlen <= 0) {
            nachricht = "§7" + schlaefer.getName() + " geht schlafen §8– §7die Nacht wird übersprungen.";
        } else if (fehlen == 1) {
            nachricht = "§7" + schlaefer.getName() + " geht schlafen §8– §7noch 1 Spieler muss schlafen.";
        } else {
            nachricht = "§7" + schlaefer.getName() + " geht schlafen §8– §7noch " + fehlen + " Spieler müssen schlafen.";
        }

        for (Player anderer : welt.getPlayers()) {
            anderer.sendMessage(nachricht);
        }
    }


    /** Dieselbe Formel wie Minecraft: mindestens einer, sonst aufgerundet Prozent der aktiven Spieler. */
    private static int benoetigteSchlaefer(int aktiveSpieler, int prozent) {
        return Math.max(1, (int) Math.ceil(aktiveSpieler * prozent / 100.0f));
    }
}

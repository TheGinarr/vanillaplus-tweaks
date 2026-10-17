package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
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
 * wer schlafen geht – z. B. "Steve geht schlafen (1/3)".
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

        int spielerInDerWelt = 0;
        int schlafende = 1; // der Spieler selbst liegt ab jetzt im Bett

        for (Player anderer : welt.getPlayers()) {
            spielerInDerWelt++;
            if (anderer != schlaefer && anderer.isSleeping()) {
                schlafende++;
            }
        }

        String nachricht = "§7" + schlaefer.getName() + " geht schlafen §8(" + schlafende + "/" + spielerInDerWelt + ")";
        for (Player anderer : welt.getPlayers()) {
            anderer.sendMessage(nachricht);
        }
    }
}

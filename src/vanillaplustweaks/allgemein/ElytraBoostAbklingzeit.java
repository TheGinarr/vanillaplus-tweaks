package vanillaplustweaks.allgemein;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import vanillaplustweaks.Modul;
import vanillaplustweaks.NeuLadbar;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Bremst Elytra-Raketenboosts.
 *
 * Nach jedem Raketen-Boost im Flug bekommen Feuerwerksraketen die normale Item-Abklingzeit
 * von Minecraft (grauer Balken auf der Rakete). Dauer-Boosten geht dadurch nicht mehr.
 *
 * Paper loest PlayerElytraBoostEvent nur bei einem echten Boost im Flug aus – und erst,
 * nachdem der Server die Abklingzeit geprueft hat. Die hier gesetzte Zeit gilt also ab dem naechsten Boost.
 */
public final class ElytraBoostAbklingzeit extends Modul implements Listener, NeuLadbar {

    private int ticksOberwelt;
    private int ticksNether;
    private int ticksEnd;


    public ElytraBoostAbklingzeit(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "elytra-boost-cooldown";
    }


    @Override
    public void start() {
        neuLaden();
        registriereListener(this);
    }


    @Override
    public void neuLaden() {
        ticksOberwelt = sekundenInTicks(kommazahl("overworld-seconds", 4.0));
        ticksNether = sekundenInTicks(kommazahl("nether-seconds", 4.0));
        ticksEnd = sekundenInTicks(kommazahl("end-seconds", 3.0));

        log("Abklingzeit in Ticks – Oberwelt " + ticksOberwelt + ", Nether " + ticksNether + ", End " + ticksEnd);
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beiBoost(PlayerElytraBoostEvent event) {
        Player spieler = event.getPlayer();

        if (spieler.hasPermission("vptweaks.elytra.bypass")) {
            return;
        }

        int ticks = ticksFuer(spieler.getWorld().getEnvironment());
        if (ticks > 0) {
            spieler.setCooldown(Material.FIREWORK_ROCKET, ticks);
        }
    }


    private int ticksFuer(World.Environment dimension) {
        if (dimension == World.Environment.NETHER) {
            return ticksNether;
        }
        if (dimension == World.Environment.THE_END) {
            return ticksEnd;
        }
        return ticksOberwelt;
    }


    private static int sekundenInTicks(double sekunden) {
        if (sekunden <= 0) {
            return 0;
        }
        return (int) Math.round(sekunden * 20.0);
    }
}

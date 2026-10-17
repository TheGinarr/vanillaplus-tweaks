package vanillaplustweaks.komfort;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Knochenmehl wirkt zusaetzlich auf:
 *   - Zuckerrohr und Kaktus: wachsen um einen Block, bis zur eingestellten Hoehe
 *   - Seerosen: es faellt eine weitere Seerose heraus
 */
public final class KnochenmehlExtra extends Modul implements Listener {

    private int hoechstZuckerrohr;
    private int hoechstKaktus;
    private boolean seerosen;


    public KnochenmehlExtra(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "bonemeal-extra";
    }


    @Override
    public void start() {
        hoechstZuckerrohr = zahl("sugar-cane-max-height", 3);
        hoechstKaktus = zahl("cactus-max-height", 3);
        seerosen = schalter("lily-pad", true);

        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void mitKnochenmehl(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack knochenmehl = event.getItem();
        Block block = event.getClickedBlock();
        if (knochenmehl == null || knochenmehl.getType() != Material.BONE_MEAL || block == null) {
            return;
        }

        boolean gewachsen;
        Material art = block.getType();

        if (art == Material.SUGAR_CANE && hoechstZuckerrohr > 0) {
            gewachsen = saeuleWachsenLassen(block, hoechstZuckerrohr);
        } else if (art == Material.CACTUS && hoechstKaktus > 0) {
            gewachsen = saeuleWachsenLassen(block, hoechstKaktus);
        } else if (art == Material.LILY_PAD && seerosen) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), ItemStack.of(Material.LILY_PAD));
            gewachsen = true;
        } else {
            return; // alles andere macht Minecraft selbst
        }

        event.setCancelled(true);
        if (!gewachsen) {
            return;
        }

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            knochenmehl.subtract();
        }

        Location mitte = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, mitte, 10);
        block.getWorld().playSound(block.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f);
    }


    /**
     * Laesst Zuckerrohr oder Kaktus um einen Block nach oben wachsen –
     * nur wenn darueber Luft ist und die Hoechsthoehe noch nicht erreicht ist.
     */
    private static boolean saeuleWachsenLassen(Block angeklickt, int hoechsthoehe) {
        Material art = angeklickt.getType();

        Block unten = angeklickt;
        while (unten.getRelative(0, -1, 0).getType() == art) {
            unten = unten.getRelative(0, -1, 0);
        }

        Block oben = angeklickt;
        while (oben.getRelative(0, 1, 0).getType() == art) {
            oben = oben.getRelative(0, 1, 0);
        }

        int hoehe = oben.getY() - unten.getY() + 1;
        Block darueber = oben.getRelative(0, 1, 0);

        if (hoehe >= hoechsthoehe || darueber.getType() != Material.AIR) {
            return false;
        }

        darueber.setType(art);
        return true;
    }
}

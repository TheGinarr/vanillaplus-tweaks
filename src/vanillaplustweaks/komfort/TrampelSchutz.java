package vanillaplustweaks.komfort;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Schuetzt Ackerland vor dem Zertrampeln.
 *
 * Einstellung "mode":
 *   never            – Ackerland wird nie zertrampelt
 *   feather-falling  – nur wer Stiefel mit Federfall traegt, zertrampelt nichts
 *   vanilla          – normales Minecraft-Verhalten (Modul praktisch aus)
 *
 * "protect-from-mobs" schuetzt zusaetzlich vor Tieren und Monstern.
 */
public final class TrampelSchutz extends Modul implements Listener {

    private String modus;
    private boolean auchVorTieren;


    public TrampelSchutz(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "trample";
    }


    @Override
    public void start() {
        modus = text("mode", "never").toLowerCase();
        auchVorTieren = schalter("protect-from-mobs", true);

        if (!modus.equals("vanilla")) {
            registriereListener(this);
        }
    }


    /** Spieler springt auf Ackerland */
    @EventHandler(priority = EventPriority.HIGH)
    public void spielerTrampelt(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || !istAckerland(event.getClickedBlock())) {
            return;
        }

        if (modus.equals("never")) {
            event.setCancelled(true);
        } else if (modus.equals("feather-falling") && traegtFederfallStiefel(event.getPlayer())) {
            event.setCancelled(true);
        }
    }


    /** Tier oder Monster springt auf Ackerland */
    @EventHandler(priority = EventPriority.HIGH)
    public void tierTrampelt(EntityInteractEvent event) {
        if (auchVorTieren && istAckerland(event.getBlock())) {
            event.setCancelled(true);
        }
    }


    private static boolean istAckerland(Block block) {
        return block != null && block.getType() == Material.FARMLAND;
    }


    private static boolean traegtFederfallStiefel(Player spieler) {
        ItemStack stiefel = spieler.getInventory().getBoots();
        return stiefel != null && stiefel.containsEnchantment(Enchantment.FEATHER_FALLING);
    }
}

package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Ersetzt das Datapack "Silk Touch Budding Amethyst" (Vanilla Tweaks).
 *
 * Knospender Amethyst droppt in Minecraft nie etwas. Mit einem Werkzeug mit Behutsamkeit
 * droppt er sich selbst und kann umgesetzt werden.
 */
public final class KnospenderAmethyst extends Modul implements Listener {

    public KnospenderAmethyst(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "silk-touch-budding-amethyst";
    }


    @Override
    public void start() {
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimAbbauen(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BUDDING_AMETHYST || !event.isDropItems()) {
            return;
        }

        Player spieler = event.getPlayer();
        if (spieler.getGameMode() == GameMode.CREATIVE) {
            return; // im Kreativmodus droppt nichts – wie bei allen Bloecken
        }

        ItemStack werkzeug = spieler.getInventory().getItemInMainHand();
        if (werkzeug.getEnchantmentLevel(Enchantment.SILK_TOUCH) < 1) {
            return;
        }

        // Wie bei Beute-Tabellen: nur mit einem Werkzeug, mit dem der Block ueberhaupt etwas droppt (Spitzhacke)
        if (!block.isPreferredTool(werkzeug)) {
            return;
        }

        Location mitte = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(mitte, ItemStack.of(Material.BUDDING_AMETHYST));
    }
}

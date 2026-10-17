package vanillaplustweaks.komfort;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Spender (nicht Werfer) pflanzen Setzlinge und Saatgut auf den Block vor sich.
 * Ob der Boden passt, entscheidet Minecraft selbst (z. B. Weizen nur auf Ackerland).
 *
 * Technischer Hinweis: Bricht man den Spender-Vorgang ab, legt Paper das Item in den Spender zurueck
 * (nachgelesen im Paper-Code). Deshalb wird das Item einen Tick spaeter selbst entnommen.
 * Ist es dann nicht mehr da, wird die Pflanze wieder entfernt – so kann nichts verdoppelt werden.
 */
public final class SpenderPflanzen extends Modul implements Listener {

    /** Saatgut-Item -> Pflanzenblock. Setzlinge sind Item und Block zugleich und werden extra geprueft. */
    private static final Map<Material, Material> PFLANZE = new EnumMap<>(Material.class);

    static {
        PFLANZE.put(Material.WHEAT_SEEDS, Material.WHEAT);
        PFLANZE.put(Material.CARROT, Material.CARROTS);
        PFLANZE.put(Material.POTATO, Material.POTATOES);
        PFLANZE.put(Material.BEETROOT_SEEDS, Material.BEETROOTS);
        PFLANZE.put(Material.NETHER_WART, Material.NETHER_WART);
    }


    public SpenderPflanzen(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "dispenser-plant";
    }


    @Override
    public void start() {
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimAusgeben(BlockDispenseEvent event) {
        Block spender = event.getBlock();
        if (spender.getType() != Material.DISPENSER) {
            return;
        }

        ItemStack item = event.getItem();
        Material pflanzenTyp = pflanzeFuer(item.getType());
        if (pflanzenTyp == null) {
            return;
        }

        if (!(spender.getBlockData() instanceof Dispenser ausrichtung)) {
            return;
        }

        Block davor = spender.getRelative(ausrichtung.getFacing());
        if (davor.getType() != Material.AIR) {
            return;
        }

        BlockData pflanze = pflanzenTyp.createBlockData();
        if (!pflanze.isSupported(davor)) {
            return; // falscher Boden
        }

        // Statt das Item auszuwerfen: pflanzen
        event.setCancelled(true);
        davor.setBlockData(pflanze);
        davor.getWorld().playSound(davor.getLocation(), Sound.ITEM_CROP_PLANT, 1.0f, 1.0f);

        ItemStack einStueck = item.asOne();
        spaeter(1, () -> ausSpenderEntnehmen(spender, einStueck, davor, pflanzenTyp));
    }


    private static Material pflanzeFuer(Material item) {
        Material pflanze = PFLANZE.get(item);
        if (pflanze != null) {
            return pflanze;
        }
        if (Tag.SAPLINGS.isTagged(item) && item.isBlock()) {
            return item;
        }
        return null;
    }


    private static void ausSpenderEntnehmen(Block spender, ItemStack einStueck, Block pflanzstelle, Material pflanzenTyp) {
        BlockState zustand = spender.getState();

        boolean entnommen = zustand instanceof Container behaelter
                && behaelter.getInventory().removeItem(einStueck).isEmpty();

        if (!entnommen && pflanzstelle.getType() == pflanzenTyp) {
            pflanzstelle.setType(Material.AIR);
        }
    }
}

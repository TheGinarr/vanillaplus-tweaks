package vanillaplustweaks.komfort;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Rechtsklick auf eine reife Pflanze erntet sie und pflanzt sofort neu.
 * Fuer das Neupflanzen wird ein Saatgut aus der Ernte abgezogen.
 *
 * Nicht beim Schleichen und nicht mit Knochenmehl in der Hand – dann verhaelt sich alles wie gewohnt.
 */
public final class RechtsklickErnte extends Modul implements Listener {

    /** Pflanze -> Item, das zum Neupflanzen abgezogen wird */
    private static final Map<Material, Material> SAATGUT = new EnumMap<>(Material.class);

    static {
        SAATGUT.put(Material.WHEAT, Material.WHEAT_SEEDS);
        SAATGUT.put(Material.CARROTS, Material.CARROT);
        SAATGUT.put(Material.POTATOES, Material.POTATO);
        SAATGUT.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        SAATGUT.put(Material.NETHER_WART, Material.NETHER_WART);
        SAATGUT.put(Material.COCOA, Material.COCOA_BEANS);
    }


    public RechtsklickErnte(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "harvest";
    }


    @Override
    public void start() {
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimRechtsklick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player spieler = event.getPlayer();
        if (spieler.isSneaking()) {
            return;
        }

        ItemStack inDerHand = event.getItem();
        if (inDerHand != null && inDerHand.getType() == Material.BONE_MEAL) {
            return;
        }

        Block pflanze = event.getClickedBlock();
        if (pflanze == null || !SAATGUT.containsKey(pflanze.getType())) {
            return;
        }

        if (!(pflanze.getBlockData() instanceof Ageable wachstum) || wachstum.getAge() < wachstum.getMaximumAge()) {
            return; // noch nicht reif
        }

        event.setCancelled(true);
        ernten(pflanze, wachstum, inDerHand, spieler);
    }


    private void ernten(Block pflanze, Ageable wachstum, ItemStack werkzeug, Player spieler) {
        Material saatgut = SAATGUT.get(pflanze.getType());
        Location mitte = pflanze.getLocation().add(0.5, 0.5, 0.5);
        boolean saatgutAbgezogen = false;

        // Normale Drops berechnen lassen (beruecksichtigt z. B. Glueck auf dem Werkzeug)
        for (ItemStack drop : pflanze.getDrops(werkzeug, spieler)) {
            if (!saatgutAbgezogen && drop.getType() == saatgut && drop.getAmount() > 0) {
                drop.setAmount(drop.getAmount() - 1);
                saatgutAbgezogen = true;
            }

            if (drop.getAmount() > 0) {
                pflanze.getWorld().dropItemNaturally(mitte, drop);
            }
        }

        // Pflanze zuruecksetzen = neu gepflanzt
        wachstum.setAge(0);
        pflanze.setBlockData(wachstum);
        pflanze.getWorld().playSound(pflanze.getLocation(), Sound.BLOCK_CROP_BREAK, 1.0f, 1.0f);
    }
}

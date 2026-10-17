package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ersetzt die Datapacks "Cauldron Concrete" und "Cauldron Mud" (Vanilla Tweaks).
 *
 * Wirft man Trockenbeton in einen Kessel mit Wasser, wird der ganze Stapel sofort zu Beton.
 * Wirft man Erde, Grobe Erde oder Wurzelerde hinein, wird sie zu Schlamm.
 * Wie beim Datapack wird dabei kein Wasser verbraucht.
 *
 * Unterschied zum Datapack: Die Datapacks haben jede Sekunde ALLE Items der Welt geprueft.
 * Hier meldet Paper selbst, wenn sich ein Item in einem Kessel befindet (EntityInsideBlockEvent).
 */
public final class KesselUmwandlung extends Modul implements Listener {

    /** Welches Item wird im Wasserkessel zu welchem? */
    private final Map<Material, Material> umwandlungen = new EnumMap<>(Material.class);


    public KesselUmwandlung(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "cauldron-conversion";
    }


    @Override
    public void start() {
        if (schalter("concrete-powder", true)) {
            fuegeBetonHinzu();
        }
        if (schalter("dirt-to-mud", true)) {
            fuegeSchlammHinzu();
        }

        registriereListener(this);
    }


    private void fuegeBetonHinzu() {
        umwandlungen.put(Material.WHITE_CONCRETE_POWDER, Material.WHITE_CONCRETE);
        umwandlungen.put(Material.ORANGE_CONCRETE_POWDER, Material.ORANGE_CONCRETE);
        umwandlungen.put(Material.MAGENTA_CONCRETE_POWDER, Material.MAGENTA_CONCRETE);
        umwandlungen.put(Material.LIGHT_BLUE_CONCRETE_POWDER, Material.LIGHT_BLUE_CONCRETE);
        umwandlungen.put(Material.YELLOW_CONCRETE_POWDER, Material.YELLOW_CONCRETE);
        umwandlungen.put(Material.LIME_CONCRETE_POWDER, Material.LIME_CONCRETE);
        umwandlungen.put(Material.PINK_CONCRETE_POWDER, Material.PINK_CONCRETE);
        umwandlungen.put(Material.GRAY_CONCRETE_POWDER, Material.GRAY_CONCRETE);
        umwandlungen.put(Material.LIGHT_GRAY_CONCRETE_POWDER, Material.LIGHT_GRAY_CONCRETE);
        umwandlungen.put(Material.CYAN_CONCRETE_POWDER, Material.CYAN_CONCRETE);
        umwandlungen.put(Material.PURPLE_CONCRETE_POWDER, Material.PURPLE_CONCRETE);
        umwandlungen.put(Material.BLUE_CONCRETE_POWDER, Material.BLUE_CONCRETE);
        umwandlungen.put(Material.BROWN_CONCRETE_POWDER, Material.BROWN_CONCRETE);
        umwandlungen.put(Material.GREEN_CONCRETE_POWDER, Material.GREEN_CONCRETE);
        umwandlungen.put(Material.RED_CONCRETE_POWDER, Material.RED_CONCRETE);
        umwandlungen.put(Material.BLACK_CONCRETE_POWDER, Material.BLACK_CONCRETE);
    }


    private void fuegeSchlammHinzu() {
        umwandlungen.put(Material.DIRT, Material.MUD);
        umwandlungen.put(Material.COARSE_DIRT, Material.MUD);
        umwandlungen.put(Material.ROOTED_DIRT, Material.MUD);
    }


    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void itemImKessel(EntityInsideBlockEvent event) {
        if (!(event.getEntity() instanceof Item item)) {
            return;
        }
        if (event.getBlock().getType() != Material.WATER_CAULDRON) {
            return;
        }

        ItemStack stapel = item.getItemStack();
        Material ergebnis = umwandlungen.get(stapel.getType());
        if (ergebnis == null) {
            return;
        }

        // Wie beim Datapack: nur die Sorte wird getauscht, Anzahl und uebrige Daten bleiben
        item.setItemStack(stapel.withType(ergebnis));
    }
}

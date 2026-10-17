package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * Ersetzt das Datapack "Husks Drop Sand" (Vanilla Tweaks).
 *
 * Wuestenzombies (Husk), ausgedoerrte Skelette (Parched) und Kamel-Husks lassen zusaetzlich Sand fallen.
 * Mengen wie im Datapack:
 *   Husk / Parched:  0–2 Sand, mit Pluenderung je Stufe bis zu +1
 *   Kamel-Husk:      0–3 Sand, mit Pluenderung je Stufe bis zu +2
 *
 * Unterschied zum Datapack: Das Datapack musste die kompletten Beute-Tabellen von Minecraft ersetzen
 * (mit einer Kopie der alten Original-Tabellen). Hier wird nur Sand dazugelegt – die normalen Drops
 * bleiben immer auf dem aktuellen Stand von Minecraft.
 */
public final class WuestenzombieSand extends Modul implements Listener {

    private final Random zufall = new Random();


    public WuestenzombieSand(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "husks-drop-sand";
    }


    @Override
    public void start() {
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimTod(EntityDeathEvent event) {
        String art = configNameFuer(event.getEntityType());
        if (art == null) {
            return;
        }

        int menge = zufallInklusive(zahl(art + ".min", 0), zahl(art + ".max", 2));
        menge += pluenderungsBonus(event.getEntity().getKiller(), kommazahl(art + ".looting-bonus-per-level", 1));

        if (menge > 0) {
            event.getDrops().add(ItemStack.of(Material.SAND, menge));
        }
    }


    private static String configNameFuer(EntityType typ) {
        if (typ == EntityType.HUSK) {
            return "husk";
        }
        if (typ == EntityType.PARCHED) {
            return "parched";
        }
        if (typ == EntityType.CAMEL_HUSK) {
            return "camel-husk";
        }
        return null;
    }


    /**
     * Wie Minecrafts "enchanted_count_increase": pro Pluenderungsstufe eine Zufallszahl
     * zwischen 0 und dem Hoechstwert, am Ende gerundet.
     */
    private int pluenderungsBonus(Player toeter, double hoechstProStufe) {
        if (toeter == null || hoechstProStufe <= 0) {
            return 0;
        }

        int stufe = toeter.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        if (stufe <= 0) {
            return 0;
        }

        double bonus = stufe * zufall.nextDouble() * hoechstProStufe;
        return (int) Math.round(bonus);
    }


    private int zufallInklusive(int min, int max) {
        if (max <= min) {
            return Math.max(0, min);
        }
        return min + zufall.nextInt(max - min + 1);
    }
}

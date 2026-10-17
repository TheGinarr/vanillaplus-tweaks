package vanillaplustweaks.komfort;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

/**
 * Dorfbewohner laufen Spielern hinterher, die einen Smaragdblock in der Hand halten.
 * Praktisch, um Dorfbewohner in eine Handelshalle zu bringen.
 *
 * Gerechnet wird nur fuer Spieler, die gerade einen Smaragdblock halten (alle 0,5 Sekunden).
 * Gesteuert wird ueber Papers Wegfindung (Pathfinder).
 */
public final class DorfbewohnerFolgen extends Modul {

    private double radius;
    private double tempo;
    private double stoppAbstandQuadrat;


    public DorfbewohnerFolgen(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "villager-follow";
    }


    @Override
    public void start() {
        radius = kommazahl("radius", 10.0);
        tempo = kommazahl("speed", 0.6);

        double stoppAbstand = kommazahl("stop-distance", 2.5);
        stoppAbstandQuadrat = stoppAbstand * stoppAbstand;

        wiederholeAlle(10, this::nachziehen);
    }


    private void nachziehen() {
        for (Player spieler : plugin.getServer().getOnlinePlayers()) {
            if (!haeltSmaragdblock(spieler)) {
                continue;
            }

            for (Entity entity : spieler.getNearbyEntities(radius, radius, radius)) {
                if (!(entity instanceof Villager dorfbewohner)) {
                    continue;
                }

                boolean schonNahGenug = dorfbewohner.getLocation().distanceSquared(spieler.getLocation()) <= stoppAbstandQuadrat;
                if (!schonNahGenug) {
                    dorfbewohner.getPathfinder().moveTo(spieler, tempo);
                }
            }
        }
    }


    private static boolean haeltSmaragdblock(Player spieler) {
        ItemStack haupthand = spieler.getInventory().getItemInMainHand();
        ItemStack nebenhand = spieler.getInventory().getItemInOffHand();

        return haupthand.getType() == Material.EMERALD_BLOCK || nebenhand.getType() == Material.EMERALD_BLOCK;
    }
}

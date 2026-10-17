package vanillaplustweaks.bauen;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Rahmen sperren – wie das Wachsen von Kupfer:
 *
 *   Honigwabe + Rechtsklick auf einen Rahmen mit Item -> gesperrt.
 *     Das Item laesst sich nicht mehr herausnehmen oder drehen, und der Rahmen ist im
 *     Ueberlebensmodus nicht mehr zerstoerbar (auch nicht durch Explosionen). Er haelt auch ohne Wand.
 *     Das ist der Minecraft-Zustand "Fixed" – Minecraft macht den Rest selbst.
 *   Axt + Rechtsklick auf einen gesperrten Rahmen -> entsperrt.
 *
 * Die Honigwabe wird verbraucht, die Axt verliert 1 Haltbarkeit (ausser im Kreativmodus).
 */
public final class RahmenSperren extends Modul implements Listener {

    public RahmenSperren(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "item-frame-lock";
    }


    @Override
    public void start() {
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimRechtsklick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame rahmen)) {
            return;
        }

        Player spieler = event.getPlayer();
        ItemStack inHand = event.getHand() == EquipmentSlot.OFF_HAND
                ? spieler.getInventory().getItemInOffHand()
                : spieler.getInventory().getItemInMainHand();
        boolean kreativ = spieler.getGameMode() == GameMode.CREATIVE;

        if (inHand.getType() == Material.HONEYCOMB && !rahmen.isFixed() && !rahmen.getItem().isEmpty()) {
            event.setCancelled(true);
            rahmen.setFixed(true);
            if (!kreativ) {
                inHand.subtract();
            }
            rueckmeldung(rahmen, Particle.WAX_ON, Sound.ITEM_HONEYCOMB_WAX_ON);
            spieler.swingHand(event.getHand());
            return;
        }

        if (Tag.ITEMS_AXES.isTagged(inHand.getType()) && rahmen.isFixed()) {
            event.setCancelled(true);
            rahmen.setFixed(false);
            if (!kreativ) {
                inHand.damage(1, spieler);
            }
            rueckmeldung(rahmen, Particle.WAX_OFF, Sound.ITEM_AXE_WAX_OFF);
            spieler.swingHand(event.getHand());
        }
    }


    private static void rueckmeldung(ItemFrame rahmen, Particle partikel, Sound klang) {
        Location ort = rahmen.getLocation();
        ort.getWorld().spawnParticle(partikel, ort, 8, 0.25, 0.25, 0.25, 0);
        ort.getWorld().playSound(ort, klang, 1f, 1f);
    }
}

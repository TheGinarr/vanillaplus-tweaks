package vanillaplustweaks.bauen;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Unsichtbare Rahmen: Mit der Schere auf einen Rahmen (oder Leuchtrahmen) mit Item rechtsklicken
 * -> der Rahmen wird unsichtbar, nur das Item bleibt zu sehen. Nochmal -> wieder sichtbar.
 *
 * - Nur Rahmen MIT Item: Ein leerer unsichtbarer Rahmen waere kaum wiederzufinden.
 *   Bei einem leeren Rahmen bleibt alles wie in Minecraft (die Schere kommt in den Rahmen).
 * - Wird das Item herausgeschlagen, wird der Rahmen automatisch wieder sichtbar.
 * - Die Schere verliert dabei 1 Haltbarkeit (einstellbar), ausser im Kreativmodus.
 * - Gesperrte Rahmen (Honigwabe) lassen sich nicht umschalten – erst mit der Axt entsperren.
 */
public final class UnsichtbareRahmen extends Modul implements Listener {

    private int haltbarkeitKosten;


    public UnsichtbareRahmen(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "invisible-item-frames";
    }


    @Override
    public void start() {
        haltbarkeitKosten = Math.max(0, zahl("shears-durability-cost", 1));
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimRechtsklick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame rahmen)) {
            return;
        }

        Player spieler = event.getPlayer();
        ItemStack schere = haltbarkeitsHand(spieler, event.getHand());
        if (schere.getType() != Material.SHEARS) {
            return;
        }
        if (rahmen.getItem().isEmpty() || rahmen.isFixed()) {
            return; // leer: Minecraft legt die Schere hinein / gesperrt: nichts aendern
        }

        // Kein Drehen des Items, kein Hineinlegen der Schere
        event.setCancelled(true);

        rahmen.setVisible(!rahmen.isVisible());

        if (spieler.getGameMode() != GameMode.CREATIVE && haltbarkeitKosten > 0) {
            schere.damage(haltbarkeitKosten, spieler);
        }

        Location ort = rahmen.getLocation();
        ort.getWorld().playSound(ort, Sound.ITEM_SHEARS_SNIP, 1f, 1f);
        spieler.swingHand(event.getHand());
    }


    /** Item herausgeschlagen -> Rahmen wieder sichtbar machen (einen Tick spaeter, wenn das Item weg ist). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beiSchaden(EntityDamageEvent event) {
        if (event.getEntity() instanceof ItemFrame rahmen && !rahmen.isVisible()) {
            spaeter(1, () -> {
                if (rahmen.isValid() && rahmen.getItem().isEmpty()) {
                    rahmen.setVisible(true);
                }
            });
        }
    }


    private static ItemStack haltbarkeitsHand(Player spieler, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
                ? spieler.getInventory().getItemInOffHand()
                : spieler.getInventory().getItemInMainHand();
    }
}

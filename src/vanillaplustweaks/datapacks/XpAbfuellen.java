package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/**
 * Ersetzt das Datapack "XP Bottling" (Vanilla Tweaks).
 *
 * Abfuellen: Rechtsklick mit einer leeren Glasflasche (Haupthand) auf einen Zaubertisch
 *            -> 12 Erfahrungspunkte werden zu einer Erfahrungsflasche.
 *            Beim Schleichen wird der ganze Flaschenstapel in der Hand auf einmal gefuellt,
 *            soweit die Erfahrung reicht (abschaltbar mit "sneak-fills-whole-stack").
 * Zurueckholen: Erfahrungsflasche im Ofen schmelzen -> leere Flasche + genau 12 Erfahrungspunkte.
 *
 * Unterschied zum Datapack: Das Datapack musste den Zaubertisch kurz durch Schnee ersetzen,
 * damit sich das Menue nicht oeffnet. Ein Plugin kann den Klick einfach abfangen.
 */
public final class XpAbfuellen extends Modul implements Listener {

    private int punkteProFlasche;
    private boolean schleichenFuelltStapel;


    public XpAbfuellen(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "xp-bottling";
    }


    @Override
    public void start() {
        punkteProFlasche = Math.max(1, zahl("points-per-bottle", 12));
        schleichenFuelltStapel = schalter("sneak-fills-whole-stack", true);

        if (schalter("smelt-back", true)) {
            registriereOfenRezept();
        }
        registriereListener(this);
    }


    // ------------------------------------------------------------------
    // Abfuellen am Zaubertisch
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void amZaubertisch(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENCHANTING_TABLE) {
            return;
        }

        ItemStack flasche = event.getItem();
        if (flasche == null || flasche.getType() != Material.GLASS_BOTTLE) {
            return;
        }

        Player spieler = event.getPlayer();

        // Wie im Datapack: mit Flasche in der Hand oeffnet sich der Zaubertisch nicht
        event.setCancelled(true);

        // Wie viele Flaschen? Normal eine, beim Schleichen so viele wie in der Hand sind –
        // aber nur so viele, wie die Erfahrung des Spielers bezahlen kann.
        int gesamtPunkte = spieler.calculateTotalExperiencePoints();
        int bezahlbar = gesamtPunkte / punkteProFlasche;
        int gewuenscht = (schleichenFuelltStapel && spieler.isSneaking()) ? flasche.getAmount() : 1;
        int anzahl = Math.min(gewuenscht, bezahlbar);

        if (anzahl < 1) {
            return; // zu wenig Erfahrung
        }

        abfuellen(spieler, flasche, anzahl);
    }


    private void abfuellen(Player spieler, ItemStack leereFlaschen, int anzahl) {
        int gesamtPunkte = spieler.calculateTotalExperiencePoints();
        spieler.setExperienceLevelAndProgress(gesamtPunkte - anzahl * punkteProFlasche);

        // Leere Flaschen aus der Hand abziehen (das ist das echte Item in der Hand)
        leereFlaschen.subtract(anzahl);

        // Volle Flaschen ins Inventar – was nicht mehr hineinpasst, faellt vor die Fuesse
        HashMap<Integer, ItemStack> passtNicht = spieler.getInventory().addItem(ItemStack.of(Material.EXPERIENCE_BOTTLE, anzahl));
        for (ItemStack rest : passtNicht.values()) {
            spieler.getWorld().dropItem(spieler.getLocation(), rest);
        }

        spieler.playSound(spieler.getLocation(), Sound.ITEM_BOTTLE_FILL_DRAGONBREATH, 1.0f, 1.25f);
    }


    // ------------------------------------------------------------------
    // Ofen-Rezept: Erfahrungsflasche -> leere Flasche + Erfahrung
    // ------------------------------------------------------------------

    private void registriereOfenRezept() {
        NamespacedKey name = new NamespacedKey(plugin, "xp_flasche_schmelzen");

        // Bei einem Plugin-Neustart ist das Rezept eventuell noch vorhanden
        plugin.getServer().removeRecipe(name);

        FurnaceRecipe rezept = new FurnaceRecipe(
                name,
                ItemStack.of(Material.GLASS_BOTTLE),    // Ergebnis
                Material.EXPERIENCE_BOTTLE,              // Zutat
                punkteProFlasche,                        // Erfahrung beim Herausnehmen
                1);                                      // Schmelzdauer in Ticks (wie im Datapack)

        plugin.getServer().addRecipe(rezept);
    }
}

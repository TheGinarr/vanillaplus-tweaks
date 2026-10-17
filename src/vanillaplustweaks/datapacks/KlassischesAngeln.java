package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTables;

import java.util.Collection;
import java.util.Random;

/**
 * Ersetzt das Datapack "Classic Fishing Loot" (Vanilla Tweaks).
 *
 * Seit Minecraft 1.16 gibt es Schaetze (Sattel, Namensschild, verzauberte Buecher ...) nur noch,
 * wenn der Schwimmer in "offenem Wasser" liegt (mindestens 5x4x5 Wasser ringsum).
 * Das Datapack hat diese Bedingung entfernt – Schaetze gibt es wieder ueberall.
 *
 * So funktioniert es hier, ohne die Beute-Tabelle zu ersetzen:
 *   - Offenes Wasser: Minecraft wuerfelt normal, das Plugin tut nichts.
 *   - Kein offenes Wasser: Minecraft hat nur zwischen Muell und Fisch gewuerfelt. Mit genau der
 *     Wahrscheinlichkeit, die der Schatz im alten Beute-Topf gehabt haette, wird der Fang durch einen
 *     Schatz ersetzt. Dadurch stimmen alle drei Wahrscheinlichkeiten wieder mit dem Datapack ueberein.
 *
 * Gewichte wie in Minecraft (gameplay/fishing): Muell 10 (-2 pro Glueck), Schatz 5 (+2), Fisch 85 (-1).
 * Glueck = Stufe "Glueck des Meeres" + Glueck-Attribut des Spielers (z. B. Trank des Gluecks).
 */
public final class KlassischesAngeln extends Modul implements Listener {

    private final Random zufall = new Random();


    public KlassischesAngeln(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "classic-fishing-loot";
    }


    @Override
    public void start() {
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimAngeln(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (!(event.getCaught() instanceof Item fang)) {
            return;
        }

        FishHook schwimmer = event.getHook();
        if (schwimmer.isInOpenWater()) {
            return; // hier gibt es auch in Vanilla Schaetze
        }

        Player spieler = event.getPlayer();
        float glueck = glueck(spieler, event.getHand());

        if (zufall.nextDouble() < schatzWahrscheinlichkeit(glueck)) {
            ItemStack schatz = wuerfleSchatz(schwimmer, spieler, glueck);
            if (schatz != null) {
                fang.setItemStack(schatz);
            }
        }
    }


    /**
     * Anteil des Schatzes am gesamten Beute-Topf (wie im Datapack, also ohne Offenes-Wasser-Bedingung).
     * Gewicht in Minecraft: max(0, abgerundet(Gewicht + Qualitaet * Glueck)).
     */
    static double schatzWahrscheinlichkeit(float glueck) {
        int muell = gewicht(10, -2, glueck);
        int schatz = gewicht(5, 2, glueck);
        int fisch = gewicht(85, -1, glueck);

        int summe = muell + schatz + fisch;
        return summe == 0 ? 0 : (double) schatz / summe;
    }


    private static int gewicht(int gewicht, int qualitaet, float glueck) {
        return Math.max((int) Math.floor(gewicht + qualitaet * glueck), 0);
    }


    private static float glueck(Player spieler, EquipmentSlot hand) {
        ItemStack angel = (hand == EquipmentSlot.OFF_HAND)
                ? spieler.getInventory().getItemInOffHand()
                : spieler.getInventory().getItemInMainHand();

        float glueck = angel.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);

        AttributeInstance glueckAttribut = spieler.getAttribute(Attribute.LUCK);
        if (glueckAttribut != null) {
            glueck += (float) glueckAttribut.getValue();
        }
        return glueck;
    }


    /** Ein Schatz aus der normalen Minecraft-Tabelle "gameplay/fishing/treasure". */
    private ItemStack wuerfleSchatz(FishHook schwimmer, Player spieler, float glueck) {
        LootContext kontext = new LootContext.Builder(schwimmer.getLocation())
                .luck(glueck)
                .lootedEntity(schwimmer)
                .killer(spieler)
                .build();

        Collection<ItemStack> beute = LootTables.FISHING_TREASURE.getLootTable().populateLoot(zufall, kontext);
        return beute.isEmpty() ? null : beute.iterator().next();
    }
}

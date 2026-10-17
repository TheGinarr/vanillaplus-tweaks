package vanillaplustweaks.datapacks.garten;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

/**
 * Welches Item kommt zurueck, wenn eine Pflanze aus dem Topf entfernt wird?
 *
 * Neue Pflanzen merken sich das Item, mit dem sie gepflanzt wurden – genau das kommt zurueck.
 *
 * Pflanzen aus Datapack-Zeiten haben diese Angabe nicht. Fuer sie gilt die Regel des Datapacks
 * (Item = Block der Anzeige, mit Sonderfaellen fuer Kannenpflanze, Fackellilie und Chorusbluete).
 * Das Datapack hatte dabei einen Fehler: Ist der Block kein Item (z. B. Karotten-Pflanze
 * "carrots" statt "carrot"), verschwand das Item ersatzlos. Hier wird dann das passende Item
 * zum Block genommen (Karotten-Pflanze -> Karotte).
 */
final class PflanzenItem {

    private PflanzenItem() {
    }


    /** Schluessel aus einer frueheren Plugin-Version mit anderem Namen (Einstellung "legacy-namespace"), oder null. */
    static NamespacedKey alterSchluessel;


    static ItemStack fuer(BlockDisplay anzeige, NamespacedKey gepflanztSchluessel) {
        String gepflanzt = anzeige.getPersistentDataContainer().get(gepflanztSchluessel, PersistentDataType.STRING);
        if (gepflanzt == null && alterSchluessel != null) {
            gepflanzt = anzeige.getPersistentDataContainer().get(alterSchluessel, PersistentDataType.STRING);
        }
        if (gepflanzt != null) {
            return item(Material.matchMaterial(gepflanzt));
        }

        BlockData block = anzeige.getBlock();
        Material material = nachDatapackRegel(block, anzeige.getScoreboardTags());

        ItemStack ergebnis = item(material);
        if (ergebnis == null) {
            ergebnis = item(block.getPlacementMaterial());
        }
        return ergebnis;
    }


    /** Die Regel aus "plant_break" des Datapacks. */
    private static Material nachDatapackRegel(BlockData block, Set<String> markierungen) {
        Material art = block.getMaterial();

        if (!markierungen.contains("pp_customdrop")) {
            return markierungen.contains("pp_pitcher") ? Material.PITCHER_PLANT : art;
        }

        int alter = (block instanceof Ageable wachsend) ? wachsend.getAge() : -1;

        if (art == Material.PITCHER_CROP && alter == 4) return Material.PITCHER_PLANT;
        if (art == Material.PITCHER_CROP && alter == 3) return Material.PITCHER_POD;
        if (art == Material.TORCHFLOWER_CROP && block.equals(art.createBlockData())) return Material.TORCHFLOWER_SEEDS;
        if (art == Material.PITCHER_PLANT) return Material.PITCHER_PLANT;
        if (art == Material.CHORUS_FLOWER && alter == 5) return Material.POPPED_CHORUS_FRUIT;
        return null;
    }


    private static ItemStack item(Material material) {
        if (material == null || material.isAir() || !material.isItem()) {
            return null;
        }
        return ItemStack.of(material);
    }
}

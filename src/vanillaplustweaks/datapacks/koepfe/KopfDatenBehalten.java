package vanillaplustweaks.datapacks.koepfe;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Koepfe behalten ihren Namen und ihre Beschreibung, wenn man sie hinstellt und wieder abbaut.
 *
 * Minecraft uebernimmt beim Abbauen nur Aussehen, Notenblock-Klang und einen eigenen Namen.
 * Der Name eines Tierkopfs ("Warm Cow Head") und die Zeile "Killed by ..." gingen verloren.
 * Die Datapacks haben dafuer die Beute-Tabelle des Kopf-Blocks ersetzt.
 *
 * Hier: Beim Hinstellen wird das komplette Item im Block gespeichert, beim Abbauen kommt
 * genau dieses Item wieder heraus – auch bei Explosionen und wenn ein Kolben den Kopf zerstoert.
 * Gilt fuer alle Spielerkoepfe mit Namen oder Beschreibung
 * (Tierkoepfe, Spielerkoepfe, Deko-Toepfe von Gardener's Dream).
 *
 * Koepfe, die noch zu Datapack-Zeiten hingestellt wurden, haben diese Angabe nicht. Sie werden
 * an ihrer Textur erkannt (alle Tierkoepfe und Deko-Toepfe sind bekannt) und bekommen ihren
 * Namen beim Abbauen zurueck. Nur "Killed by ..." unter alten Spielerkoepfen laesst sich nicht
 * wiederherstellen (der Name des Spielers bleibt aber am Kopf).
 */
public final class KopfDatenBehalten extends Modul implements Listener {

    private NamespacedKey speicher;
    private NamespacedKey alterSpeicher;

    /** Textur -> vollstaendiges Kopf-Item (aus der Kopf-Liste und den Steinsaegen-Rezepten) */
    private final Map<String, ItemStack> bekannteKoepfe = new HashMap<>();


    public KopfDatenBehalten(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "keep-head-names";
    }


    @Override
    public void start() {
        speicher = new NamespacedKey(plugin, "kopf_item");
        alterSpeicher = alterSchluessel("kopf_item");
        bekannteKoepfeLaden();
        registriereListener(this);
    }


    // ------------------------------------------------------------------
    // Hinstellen: Item im Block speichern
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimHinstellen(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.PLAYER_HEAD || !hatNameOderBeschreibung(item)) {
            return;
        }

        if (event.getBlockPlaced().getState() instanceof Skull kopfBlock) {
            kopfBlock.getPersistentDataContainer().set(speicher, PersistentDataType.BYTE_ARRAY, item.asOne().serializeAsBytes());
            kopfBlock.update();
        }
    }


    private static boolean hatNameOderBeschreibung(ItemStack item) {
        return item.hasData(DataComponentTypes.ITEM_NAME) || item.hasData(DataComponentTypes.LORE);
    }


    // ------------------------------------------------------------------
    // Abbauen: gespeichertes Item statt des normalen Drops
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimAbbauen(BlockDropItemEvent event) {
        ItemStack gespeichert = gespeichertesItem(event.getBlockState());
        if (gespeichert == null) {
            return;
        }

        for (Item drop : event.getItems()) {
            if (drop.getItemStack().getType() == Material.PLAYER_HEAD) {
                drop.setItemStack(gespeichert);
                return;
            }
        }
    }


    // ------------------------------------------------------------------
    // Kolben und fliessendes Wasser zerstoeren den Kopf: Drops austauschen
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void durchBlockZerstoert(BlockBreakBlockEvent event) {
        ItemStack gespeichert = gespeichertesItem(event.getBlock().getState());
        if (gespeichert == null) {
            return;
        }

        List<ItemStack> drops = event.getDrops();
        for (int i = 0; i < drops.size(); i++) {
            if (drops.get(i).getType() == Material.PLAYER_HEAD) {
                drops.set(i, gespeichert);
                return;
            }
        }
    }


    // ------------------------------------------------------------------
    // Explosionen: Kopf selbst entfernen und das gespeicherte Item fallen lassen
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beiExplosion(EntityExplodeEvent event) {
        explosion(event.blockList(), event.getExplosionResult());
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beiExplosion(BlockExplodeEvent event) {
        explosion(event.blockList(), event.getExplosionResult());
    }


    private void explosion(List<Block> bloecke, ExplosionResult ergebnis) {
        if (ergebnis != ExplosionResult.DESTROY && ergebnis != ExplosionResult.DESTROY_WITH_DECAY) {
            return;
        }

        Iterator<Block> alle = bloecke.iterator();
        while (alle.hasNext()) {
            Block block = alle.next();
            ItemStack gespeichert = gespeichertesItem(block.getState());
            if (gespeichert == null) {
                continue;
            }

            alle.remove();
            block.setType(Material.AIR);

            // Koepfe droppen in Minecraft bei jeder Explosion (ihre Beute-Tabelle hat keine Explosions-Bedingung)
            Location mitte = block.getLocation().add(0.5, 0.5, 0.5);
            block.getWorld().dropItemNaturally(mitte, gespeichert);
        }
    }


    private ItemStack gespeichertesItem(BlockState zustand) {
        if (!(zustand instanceof Skull kopfBlock)) {
            return null;
        }
        byte[] daten = kopfBlock.getPersistentDataContainer().get(speicher, PersistentDataType.BYTE_ARRAY);
        if (daten == null && alterSpeicher != null) {
            daten = kopfBlock.getPersistentDataContainer().get(alterSpeicher, PersistentDataType.BYTE_ARRAY);
        }
        if (daten != null) {
            return ItemStack.deserializeBytes(daten);
        }

        // Alter Kopf aus Datapack-Zeiten: an der Textur erkennen
        String textur = textur(kopfBlock.getProfile());
        ItemStack bekannt = textur == null ? null : bekannteKoepfe.get(textur);
        return bekannt == null ? null : bekannt.clone();
    }


    // ------------------------------------------------------------------
    // Bekannte Koepfe (fuer Koepfe aus Datapack-Zeiten)
    // ------------------------------------------------------------------

    private void bekannteKoepfeLaden() {
        List<String> itemTexte = new ArrayList<>();

        YamlConfiguration koepfe = datenDatei("koepfe.yml");
        for (String tier : koepfe.getKeys(false)) {
            for (Map<?, ?> variante : koepfe.getMapList(tier + ".varianten")) {
                itemTexte.add(String.valueOf(variante.get("item")));
            }
            for (Object item : koepfe.getList(tier + ".zusatz.zufaellig-einer-von", List.of())) {
                itemTexte.add(String.valueOf(item));
            }
        }
        for (Map<?, ?> rezept : datenDatei("rezepte.yml").getMapList("steinsaege")) {
            itemTexte.add(String.valueOf(rezept.get("ergebnis")));
        }

        for (String text : itemTexte) {
            if (!text.startsWith("minecraft:player_head[")) {
                continue;
            }
            try {
                ItemStack kopf = Bukkit.getItemFactory().createItemStack(text);
                String textur = textur(kopf.getData(DataComponentTypes.PROFILE));
                if (textur != null) {
                    bekannteKoepfe.putIfAbsent(textur, kopf);
                }
            } catch (IllegalArgumentException fehler) {
                warnung("Kopf nicht lesbar: " + fehler.getMessage());
            }
        }
    }


    private static String textur(ResolvableProfile profil) {
        if (profil == null) {
            return null;
        }
        for (ProfileProperty eigenschaft : profil.properties()) {
            if (eigenschaft.getName().equals("textures")) {
                return eigenschaft.getValue();
            }
        }
        return null;
    }
}

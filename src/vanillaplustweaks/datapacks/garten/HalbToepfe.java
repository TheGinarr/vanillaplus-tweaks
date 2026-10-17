package vanillaplustweaks.datapacks.garten;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Halbe Toepfe aus Gardener's Dream: ein halb so hoher Kessel oder Komposter mit Erde darin.
 *
 * Aufbau (wie im Datapack):
 *   - Block: bemooste Bruchsteinstufe (unten oder oben) – gibt die Form und Kollision
 *   - Anzeige 1 "gd_halfpot_pot":  Kessel/Komposter, gestaucht
 *   - Anzeige 2 "gd_halfpot_dirt": die Erde obendrauf
 *   - Interaktions-Flaeche "gd_halfpot": zum Anklicken (Erde wechseln) und Schlagen (abbauen)
 *
 * Entstehen beim Platzieren eines Kessels/Komposters
 *   - mit den Steinsaegen-Items "Half-Pot" / "Half-Pot Top" (immer), oder
 *   - schleichend, wenn "half-pot-sneak-placement" in der Config an ist (im Datapack standardmaessig aus).
 */
final class HalbToepfe {

    static final String MARKIERUNG = "gd_halfpot";
    static final String TOPF = "gd_halfpot_pot";
    static final String ERDE = "gd_halfpot_dirt";

    private static final Quaternionf OHNE_DREHUNG = new Quaternionf(0, 0, 0, 1);

    private final Set<String> erden;
    private final Consumer<BlockDisplay> neueAnzeige;


    HalbToepfe(Set<String> erden, Consumer<BlockDisplay> neueAnzeige) {
        this.erden = erden;
        this.neueAnzeige = neueAnzeige;
    }


    // ------------------------------------------------------------------
    // Erkennen der besonderen Steinsaegen-Items
    // ------------------------------------------------------------------

    /** 0 = normales Item, 1 = "Half-Pot", 2 = "Half-Pot Top" (Kennung im Item wie im Datapack). */
    static int halbTopfItem(ItemStack item) {
        if (item == null || (item.getType() != Material.CAULDRON && item.getType() != Material.COMPOSTER)) {
            return 0;
        }
        String daten = Bukkit.getUnsafe().serializeItemAsJson(item).toString();
        if (daten.contains("gd_halfpot_item_upper")) {
            return 2;
        }
        return daten.contains("gd_halfpot_item") ? 1 : 0;
    }


    // ------------------------------------------------------------------
    // Bauen
    // ------------------------------------------------------------------

    /**
     * Macht aus dem gerade platzierten Kessel/Komposter einen halben Topf.
     *
     * @param oben     obere Haelfte des Blocks (Stufe oben)
     * @param erdeItem Item-ID fuer die Erde (Nebenhand beim Schleichen), oder null = leer
     */
    void bauen(Block block, boolean oben, String erdeItem) {
        boolean kessel = block.getType() == Material.CAULDRON;
        if (!kessel && block.getType() != Material.COMPOSTER) {
            return;
        }

        World welt = block.getWorld();
        Location ecke = block.getLocation();

        // Topf: Kessel 0,8 hoch, Komposter 0,6 hoch
        float topfHoehe = kessel ? 0.8f : 0.6f;
        float topfY = kessel ? (oben ? 0.3f : -0.2f) : (oben ? 0.45f : -0.05f);
        BlockData topfBlock = kessel ? Material.CAULDRON.createBlockData() : Bukkit.createBlockData("minecraft:composter[level=0]");
        anzeige(welt, ecke, topfBlock, TOPF, new Vector3f(-0.005f, topfY, -0.005f), new Vector3f(1.01f, topfHoehe, 1.01f));

        // Erde: nur unten beim Schleichen aus der Nebenhand (wie im Datapack)
        BlockData erde = Material.AIR.createBlockData();
        Material erdeMaterial = erdeItem == null ? null : Material.matchMaterial(erdeItem);
        if (!oben && erdeMaterial != null && erdeMaterial.isBlock()) {
            erde = erdeMaterial.createBlockData();
        }
        anzeige(welt, ecke, erde, ERDE, new Vector3f(0f, oben ? 0.71f : 0.21f, 0f), new Vector3f(1f, 0.3f, 1f));

        block.setBlockData(Bukkit.createBlockData("minecraft:mossy_cobblestone_slab[type=" + (oben ? "top" : "bottom") + "]"));

        Location flaeche = ecke.clone().add(0.5, oben ? 0.49 : -0.01, 0.5);
        welt.spawn(flaeche, Interaction.class, neu -> {
            neu.setInteractionWidth(1.02f);
            neu.setInteractionHeight(0.52f);
            neu.setResponsive(true);
            neu.addScoreboardTag(MARKIERUNG);
        });
    }


    private void anzeige(World welt, Location ecke, BlockData block, String art, Vector3f verschiebung, Vector3f groesse) {
        BlockDisplay display = welt.spawn(ecke, BlockDisplay.class, neu -> {
            neu.addScoreboardTag(MARKIERUNG);
            neu.addScoreboardTag(art);
            neu.setBlock(block);
            neu.setTransformation(new Transformation(verschiebung, new Quaternionf(OHNE_DREHUNG), groesse, new Quaternionf(OHNE_DREHUNG)));
        });
        neueAnzeige.accept(display);
    }


    // ------------------------------------------------------------------
    // Abbauen (Schlag auf die Interaktions-Flaeche, oder Stufe ist weg)
    // ------------------------------------------------------------------

    /** Zur Interaktions-Flaeche den Topf (die Anzeige mit dem Kessel/Komposter) finden. */
    static BlockDisplay topfZu(Interaction flaeche) {
        BlockDisplay naechster = null;
        double bester = Double.MAX_VALUE;
        for (Entity entity : flaeche.getNearbyEntities(0.8, 0.8, 0.8)) {
            if (entity instanceof BlockDisplay display && display.getScoreboardTags().contains(TOPF)) {
                double abstand = display.getLocation().distanceSquared(flaeche.getLocation());
                if (abstand < bester) {
                    bester = abstand;
                    naechster = display;
                }
            }
        }
        return naechster;
    }


    /**
     * Halben Topf entfernen: Kessel/Komposter und Erde fallen als Items heraus
     * (nicht im Kreativmodus, wenn ein Spieler ihn abbaut).
     *
     * @param spieler der abbauende Spieler, oder null (Stufe ist von selbst verschwunden)
     */
    void abbauen(BlockDisplay topf, Player spieler) {
        Location ecke = topf.getLocation().getBlock().getLocation();
        World welt = topf.getWorld();
        boolean mitDrops = spieler == null || spieler.getGameMode() != GameMode.CREATIVE;

        BlockDisplay erde = null;
        for (Entity entity : welt.getNearbyEntities(ecke, 0.3, 0.3, 0.3)) {
            if (entity instanceof BlockDisplay display && display.getScoreboardTags().contains(ERDE)) {
                erde = display;
            }
        }

        if (mitDrops) {
            Location drop = ecke.clone().add(0.5, 0.3, 0.5);
            if (erde != null) {
                fallenLassen(welt, drop, erde.getBlock().getMaterial());
            }
            fallenLassen(welt, drop, topf.getBlock().getMaterial());
        }

        for (Entity entity : welt.getNearbyEntities(ecke, 0.3, 0.3, 0.3)) {
            if (entity.getScoreboardTags().contains(MARKIERUNG) && entity.getLocation().distance(ecke) <= 0.3) {
                entity.remove();
            }
        }

        // Stufe entfernen (falls sie noch da ist)
        Block block = ecke.getBlock();
        if (block.getType() == Material.MOSSY_COBBLESTONE_SLAB) {
            block.setType(Material.AIR);
        }

        Location mitte = ecke.clone().add(0.5, 0, 0.5);
        for (Entity entity : welt.getNearbyEntities(mitte, 0.5, 0.5, 0.5)) {
            if (entity instanceof Interaction && entity.getLocation().distance(mitte) <= 0.5) {
                entity.remove();
            }
        }
    }


    private static void fallenLassen(World welt, Location ort, Material material) {
        if (material != null && !material.isAir() && material.isItem()) {
            welt.dropItem(ort, ItemStack.of(material));
        }
    }


    // ------------------------------------------------------------------
    // Erde wechseln (Rechtsklick mit einem Erd-Block auf die Interaktions-Flaeche)
    // ------------------------------------------------------------------

    void erdeWechseln(Interaction flaeche, Player spieler) {
        ItemStack hand = spieler.getInventory().getItemInMainHand();
        boolean kreativ = spieler.getGameMode() == GameMode.CREATIVE;
        String itemId = hand.isEmpty() ? null : hand.getType().getKey().toString();

        if (!kreativ && (itemId == null || !erden.contains(itemId))) {
            if (spieler.getGameMode() == GameMode.SURVIVAL) {
                spieler.sendActionBar(Component.text("Not a suitable soil block."));
                spieler.playSound(spieler.getLocation(), Sound.BLOCK_STONE_BREAK, SoundCategory.MASTER, 1f, 1f);
            }
            return;
        }

        BlockDisplay topf = topfZu(flaeche);
        if (topf == null) {
            return;
        }
        Location ecke = topf.getLocation().getBlock().getLocation();

        BlockDisplay erde = null;
        for (Entity entity : topf.getWorld().getNearbyEntities(ecke, 0.3, 0.3, 0.3)) {
            if (entity instanceof BlockDisplay display && display.getScoreboardTags().contains(ERDE)) {
                erde = display;
            }
        }
        if (erde == null) {
            return;
        }

        boolean stufeOben = ecke.getBlock().getBlockData().getAsString().contains("type=top");
        topf.getWorld().spawnParticle(Particle.DUST_PLUME, ecke.clone().add(0.5, stufeOben ? 1.1 : 0.6, 0.5), 5, 0.2, 0, 0.2, 0);

        if (!kreativ) {
            fallenLassen(topf.getWorld(), ecke.clone().add(0.5, 0.5, 0.5), erde.getBlock().getMaterial());
        }

        if (itemId != null && hand.getType().isBlock()) {
            erde.setBlock(hand.getType().createBlockData());
        }
        if (!kreativ) {
            hand.subtract();
        }
        spieler.playSound(spieler.getLocation(), Sound.BLOCK_ROOTED_DIRT_PLACE, SoundCategory.BLOCKS, 1f, 0.9f);
    }
}

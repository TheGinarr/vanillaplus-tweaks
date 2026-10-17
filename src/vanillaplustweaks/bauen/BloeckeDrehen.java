package vanillaplustweaks.bauen;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Bloecke mit dem Stock drehen (Rechtsklick):
 *
 *   Treppen:         drehen sich im Uhrzeigersinn.  Mit Schleichen: oben/unten umdrehen.
 *   Stufen:          oben/unten umdrehen.
 *   Staemme, Saeulen, Ketten, Heuballen, Basalt ...: liegend in x / stehend / liegend in z.
 *   Glasierte Keramik, geschnitzte Kuerbisse, Kuerbislaternen: drehen sich im Uhrzeigersinn.
 *   Endstab und Blitzableiter: zeigen nacheinander in alle sechs Richtungen.
 *
 * Bewusst nicht: Truhen, Betten, Tueren, Redstone-Bloecke, Schilder, Koepfe und alles mit Inhalt.
 * Dort koennte Drehen etwas kaputt machen oder Redstone-Schaltungen veraendern.
 *
 * Laeuft nach Gardener's Dream: Klickt man mit dem Stock auf einen Topf, dreht sich dort die Pflanze.
 */
public final class BloeckeDrehen extends Modul implements Listener {

    private static final List<BlockFace> HORIZONTAL = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
    private static final List<BlockFace> ALLE_SECHS = List.of(BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN);
    private static final List<Axis> ACHSEN = List.of(Axis.X, Axis.Y, Axis.Z);

    /** Haben eine Achse, sollen aber nicht gedreht werden. */
    private static final Set<Material> ACHSE_AUSNAHMEN = EnumSet.of(Material.NETHER_PORTAL, Material.CREAKING_HEART);

    private static final Set<Material> KUERBISSE = EnumSet.of(Material.CARVED_PUMPKIN, Material.JACK_O_LANTERN);


    public BloeckeDrehen(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "rotate-blocks";
    }


    @Override
    public void start() {
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void beimRechtsklick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getItem() == null || event.getItem().getType() != Material.STICK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Player spieler = event.getPlayer();
        BlockData neu = gedreht(block.getBlockData(), spieler.isSneaking());
        if (neu == null) {
            return;
        }

        event.setCancelled(true);
        block.setBlockData(neu, true);
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_SPYGLASS_USE, SoundCategory.BLOCKS, 0.7f, 1.5f);
        spieler.swingHand(EquipmentSlot.HAND);
    }


    /** Die gedrehte Variante des Blocks – oder null, wenn dieser Block nicht gedreht wird. */
    static BlockData gedreht(BlockData alt, boolean schleichen) {
        BlockData neu = alt.clone();
        Material art = alt.getMaterial();

        if (neu instanceof Stairs treppe) {
            if (schleichen) {
                treppe.setHalf(treppe.getHalf() == Bisected.Half.TOP ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
            } else {
                treppe.setFacing(naechste(HORIZONTAL, treppe.getFacing()));
            }
            treppe.setShape(Stairs.Shape.STRAIGHT);
            return treppe;
        }

        if (neu instanceof Slab stufe) {
            if (stufe.getType() == Slab.Type.DOUBLE) {
                return null;
            }
            stufe.setType(stufe.getType() == Slab.Type.TOP ? Slab.Type.BOTTOM : Slab.Type.TOP);
            return stufe;
        }

        if (neu instanceof Orientable saeule && !ACHSE_AUSNAHMEN.contains(art) && saeule.getAxes().containsAll(ACHSEN)) {
            saeule.setAxis(naechste(ACHSEN, saeule.getAxis()));
            return saeule;
        }

        if (neu instanceof Directional gerichtet) {
            boolean waagerecht = art.name().endsWith("_GLAZED_TERRACOTTA") || KUERBISSE.contains(art);
            boolean alleRichtungen = art == Material.END_ROD || art.name().endsWith("LIGHTNING_ROD");

            if (waagerecht) {
                gerichtet.setFacing(naechste(HORIZONTAL, gerichtet.getFacing()));
                return gerichtet;
            }
            if (alleRichtungen) {
                gerichtet.setFacing(naechste(ALLE_SECHS, gerichtet.getFacing()));
                return gerichtet;
            }
        }
        return null;
    }


    private static <T> T naechste(List<T> reihe, T aktuell) {
        int stelle = reihe.indexOf(aktuell);
        return reihe.get((stelle + 1) % reihe.size());
    }
}

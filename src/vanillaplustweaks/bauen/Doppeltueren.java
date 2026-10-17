package vanillaplustweaks.bauen;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Doppeltueren: Oeffnet oder schliesst ein Spieler eine Tuer, geht die Nachbartuer mit.
 *
 * Eine Nachbartuer ist eine Tuer direkt daneben, die in dieselbe Richtung zeigt, ihre Angeln auf
 * der anderen Seite hat und gerade gleich steht (beide zu oder beide offen).
 * Mit Schleichen geht nur die angeklickte Tuer auf.
 * Eisentueren reagieren wie in Minecraft nur auf Redstone und sind hier nicht dabei.
 */
public final class Doppeltueren extends Modul implements Listener {

    public Doppeltueren(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "double-doors";
    }


    @Override
    public void start() {
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimRechtsklick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getPlayer().isSneaking() || event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
            return;
        }

        Block geklickt = event.getClickedBlock();
        if (geklickt == null || geklickt.getType() == Material.IRON_DOOR || !(geklickt.getBlockData() instanceof Door tuer)) {
            return;
        }

        Block unten = (tuer.getHalf() == Bisected.Half.TOP) ? geklickt.getRelative(BlockFace.DOWN) : geklickt;
        if (!(unten.getBlockData() instanceof Door untereTuer)) {
            return;
        }

        // Das Event kommt, bevor Minecraft die Tuer umschaltet: vorher-Zustand merken
        boolean warOffen = untereTuer.isOpen();

        Block partner = partner(unten, untereTuer);
        if (partner == null) {
            return;
        }

        Door partnerTuer = (Door) partner.getBlockData();
        partnerTuer.setOpen(!warOffen);
        partner.setBlockData(partnerTuer, true);

        // Obere Haelfte mitziehen
        Block partnerOben = partner.getRelative(BlockFace.UP);
        if (partnerOben.getBlockData() instanceof Door oben && oben.getHalf() == Bisected.Half.TOP) {
            oben.setOpen(!warOffen);
            partnerOben.setBlockData(oben, true);
        }
    }


    /** Die passende zweite Tuer neben dieser (untere Haelfte), oder null. */
    private static Block partner(Block unten, Door tuer) {
        BlockFace richtung = tuer.getFacing();
        BlockFace[] seiten = {drehen(richtung, true), drehen(richtung, false)};

        for (BlockFace seite : seiten) {
            Block nachbar = unten.getRelative(seite);
            if (nachbar.getType() == Material.IRON_DOOR || !(nachbar.getBlockData() instanceof Door andere)) {
                continue;
            }
            if (andere.getHalf() == Bisected.Half.BOTTOM
                    && andere.getFacing() == richtung
                    && andere.getHinge() != tuer.getHinge()
                    && andere.isOpen() == tuer.isOpen()) {
                return nachbar;
            }
        }
        return null;
    }


    private static BlockFace drehen(BlockFace richtung, boolean imUhrzeigersinn) {
        return switch (richtung) {
            case NORTH -> imUhrzeigersinn ? BlockFace.EAST : BlockFace.WEST;
            case EAST -> imUhrzeigersinn ? BlockFace.SOUTH : BlockFace.NORTH;
            case SOUTH -> imUhrzeigersinn ? BlockFace.WEST : BlockFace.EAST;
            default -> imUhrzeigersinn ? BlockFace.NORTH : BlockFace.SOUTH;
        };
    }
}

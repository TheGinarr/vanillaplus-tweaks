package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Ersetzt das Datapack "Fast Leaf Decay" (Vanilla Tweaks).
 *
 * Ablauf:
 *   1. Ein Stamm wird abgebaut oder gesprengt.
 *   2. Eine halbe Sekunde spaeter (Minecraft braucht kurz, um die Laub-Entfernungen neu zu berechnen)
 *      wird einmal die Umgebung geprueft.
 *   3. Laub, das keinen Stamm mehr in Reichweite hat, zerfaellt verteilt ueber etwa 1,5 Sekunden –
 *      mit den normalen Drops (Setzlinge, Stoecke, Aepfel).
 *
 * Es laeuft nichts dauerhaft im Hintergrund.
 */
public final class SchnellerLaubzerfall extends Modul implements Listener {

    /** So weit um den Stamm herum wird nach losem Laub gesucht. */
    private static final int SUCHRADIUS = 6;

    /** Minecraft-Laub mit Entfernung 7 hat keinen Stamm mehr in Reichweite und wuerde ohnehin zerfallen. */
    private static final int ENTFERNUNG_OHNE_STAMM = 7;

    private static final long WARTEZEIT_TICKS = 10;
    private static final int VERTEILUNG_TICKS = 30;

    private final Random zufall = new Random();

    /**
     * Baumfarmen sprengen viele Staemme gleichzeitig. Damit nicht jeder Stamm einen eigenen Suchlauf
     * startet, wird pro 4x4x4-Bereich nur ein Suchlauf gleichzeitig geplant.
     */
    private final Set<String> geplanteBereiche = new HashSet<>();


    public SchnellerLaubzerfall(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "leaf-decay";
    }


    @Override
    public void start() {
        registriereListener(this);
    }


    // ------------------------------------------------------------------
    // Ausloeser: Stamm abgebaut oder gesprengt
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimAbbauen(BlockBreakEvent event) {
        if (istStamm(event.getBlock())) {
            planeSuche(event.getBlock());
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beiMobExplosion(EntityExplodeEvent event) {
        planeSucheFuerStaemme(event.blockList());
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beiBlockExplosion(BlockExplodeEvent event) {
        planeSucheFuerStaemme(event.blockList());
    }


    private void planeSucheFuerStaemme(List<Block> bloecke) {
        for (Block block : bloecke) {
            if (istStamm(block)) {
                planeSuche(block);
            }
        }
    }


    private void planeSuche(Block stamm) {
        String bereich = stamm.getWorld().getName()
                + ":" + (stamm.getX() >> 2)
                + ":" + (stamm.getY() >> 2)
                + ":" + (stamm.getZ() >> 2);

        if (!geplanteBereiche.add(bereich)) {
            return; // fuer diesen Bereich ist schon eine Suche geplant
        }

        spaeter(WARTEZEIT_TICKS, () -> {
            geplanteBereiche.remove(bereich);
            sucheLosesLaub(stamm);
        });
    }


    // ------------------------------------------------------------------
    // Suche und Zerfall
    // ------------------------------------------------------------------

    private void sucheLosesLaub(Block mitte) {
        for (int x = -SUCHRADIUS; x <= SUCHRADIUS; x++) {
            for (int y = -SUCHRADIUS; y <= SUCHRADIUS; y++) {
                for (int z = -SUCHRADIUS; z <= SUCHRADIUS; z++) {

                    Block block = mitte.getRelative(x, y, z);
                    if (istLosesLaub(block)) {
                        long verzoegerung = 1 + zufall.nextInt(VERTEILUNG_TICKS);
                        spaeter(verzoegerung, () -> zerfallen(block));
                    }
                }
            }
        }
    }


    private void zerfallen(Block block) {
        // Zwischenzeitlich koennte jemand das Laub abgebaut oder einen Stamm daneben gesetzt haben
        if (istLosesLaub(block)) {
            block.breakNaturally();
        }
    }


    private static boolean istStamm(Block block) {
        return Tag.LOGS.isTagged(block.getType());
    }


    /** Laub, das nicht von Hand gesetzt wurde und keinen Stamm mehr in Reichweite hat. */
    private static boolean istLosesLaub(Block block) {
        if (!Tag.LEAVES.isTagged(block.getType())) {
            return false;
        }

        BlockData daten = block.getBlockData();
        return daten instanceof Leaves laub
                && !laub.isPersistent()
                && laub.getDistance() >= ENTFERNUNG_OHNE_STAMM;
    }
}

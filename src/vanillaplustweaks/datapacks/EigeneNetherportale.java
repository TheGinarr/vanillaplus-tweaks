package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ersetzt das Datapack "Custom Nether Portals" (Vanilla Tweaks).
 *
 * Netherportale duerfen beliebige Formen haben (z. B. rund oder L-foermig) und weinenden Obsidian enthalten.
 *
 * Ablauf (wie im Datapack):
 *   1. Ein Spieler zuendet mit Feuerzeug oder Feuerkugel ein Feuer an (Oberwelt oder Nether).
 *   2. Einen Tick spaeter wird geschaut, ob das Feuer noch brennt. Hat Minecraft selbst schon ein
 *      normales Portal erzeugt, ist das Feuer weg und es passiert nichts weiter.
 *   3. Von der Feuerstelle aus wird die Flaeche abgesucht – erst in X-Richtung, sonst in Z-Richtung.
 *      Die Flaeche muss komplett von Obsidian umschlossen sein und darf innen nur Luft/Feuer enthalten.
 *   4. Passt alles, wird die Innenflaeche mit Portalbloecken gefuellt.
 *
 * Einstellungen wie im Datapack: Mindest- und Hoechstzahl der Rahmenbloecke, weinender Obsidian erlaubt,
 * nicht-rechteckige Formen erlaubt. Zusaetzlich: Hoechstgroesse der Innenflaeche (Schutz vor Endlos-Suche
 * an offenen Rahmen).
 */
public final class EigeneNetherportale extends Modul implements Listener {

    private int mindestRahmen;
    private int hoechstRahmen;
    private int hoechstInnenflaeche;
    private boolean weinenderObsidianErlaubt;
    private boolean freieFormErlaubt;


    public EigeneNetherportale(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "custom-nether-portals";
    }


    @Override
    public void start() {
        mindestRahmen = zahl("min-frame-blocks", 10);
        hoechstRahmen = zahl("max-frame-blocks", 84);
        hoechstInnenflaeche = zahl("max-inner-blocks", 441);
        weinenderObsidianErlaubt = schalter("allow-crying-obsidian", true);
        freieFormErlaubt = schalter("allow-non-rectangular", true);

        registriereListener(this);
    }


    // ------------------------------------------------------------------
    // Ausloeser: Feuer anzuenden
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimAnzuenden(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!istZuender(event.getItem())) {
            return;
        }

        World.Environment dimension = event.getClickedBlock().getWorld().getEnvironment();
        if (dimension != World.Environment.NORMAL && dimension != World.Environment.NETHER) {
            return; // wie im Datapack: nur Oberwelt und Nether
        }

        Block feuerstelle = event.getClickedBlock().getRelative(event.getBlockFace());

        // Minecraft setzt das Feuer (und ggf. ein normales Portal) erst nach diesem Event
        spaeter(1, () -> {
            if (Tag.FIRE.isTagged(feuerstelle.getType())) {
                versuchePortal(feuerstelle);
            }
        });
    }


    private static boolean istZuender(ItemStack item) {
        if (item == null) {
            return false;
        }
        return item.getType() == Material.FLINT_AND_STEEL || item.getType() == Material.FIRE_CHARGE;
    }


    private void versuchePortal(Block feuerstelle) {
        // Erst die Ausrichtung entlang X versuchen, dann entlang Z – wie im Datapack
        if (versucheRichtung(feuerstelle, Axis.X)) {
            return;
        }
        versucheRichtung(feuerstelle, Axis.Z);
    }


    // ------------------------------------------------------------------
    // Flaeche absuchen
    // ------------------------------------------------------------------

    /**
     * Sucht die Flaeche ab, die von der Feuerstelle aus erreichbar ist.
     * Gibt true zurueck, wenn ein Portal entstanden ist.
     */
    private boolean versucheRichtung(Block feuerstelle, Axis richtung) {
        List<Block> innenflaeche = new ArrayList<>();
        List<Block> rahmen = new ArrayList<>();
        Set<Block> besucht = new HashSet<>();

        Deque<Block> zuPruefen = new ArrayDeque<>();
        zuPruefen.add(feuerstelle);
        besucht.add(feuerstelle);

        while (!zuPruefen.isEmpty()) {
            Block block = zuPruefen.poll();

            if (!liegtImGeladenenBereich(block)) {
                return false;
            }

            if (istObsidian(block.getType())) {
                if (block.getType() == Material.CRYING_OBSIDIAN && !weinenderObsidianErlaubt) {
                    return false;
                }
                rahmen.add(block);
                if (rahmen.size() > hoechstRahmen) {
                    return false;
                }
                continue; // am Rahmen nicht weitersuchen
            }

            if (!istLeer(block.getType())) {
                return false; // innen darf nur Luft oder Feuer sein
            }

            innenflaeche.add(block);
            if (innenflaeche.size() > hoechstInnenflaeche) {
                return false; // vermutlich ein offener Rahmen
            }

            for (Block nachbar : nachbarnInDerFlaeche(block, richtung)) {
                if (besucht.add(nachbar)) {
                    zuPruefen.add(nachbar);
                }
            }
        }

        if (rahmen.size() < mindestRahmen) {
            return false;
        }

        Set<Block> innen = new HashSet<>(innenflaeche);
        if (!freieFormErlaubt && hatEinspringendeEcke(rahmen, innen, richtung)) {
            return false;
        }

        fuellePortal(innenflaeche, richtung);
        return true;
    }


    /** Oben, unten und die beiden Seiten – je nach Ausrichtung in X- oder Z-Richtung. */
    private static List<Block> nachbarnInDerFlaeche(Block block, Axis richtung) {
        List<Block> nachbarn = new ArrayList<>(4);
        nachbarn.add(block.getRelative(0, -1, 0));
        nachbarn.add(block.getRelative(0, 1, 0));

        if (richtung == Axis.X) {
            nachbarn.add(block.getRelative(-1, 0, 0));
            nachbarn.add(block.getRelative(1, 0, 0));
        } else {
            nachbarn.add(block.getRelative(0, 0, -1));
            nachbarn.add(block.getRelative(0, 0, 1));
        }
        return nachbarn;
    }


    /**
     * Ein rechteckiger Rahmen hat keine Obsidianbloecke, die gleichzeitig ueber/unter UND neben
     * der Innenflaeche liegen. Gibt es so einen Block, ist die Form nicht rechteckig.
     * (Gleiche Pruefung wie "check_x_diagonal" im Datapack.)
     */
    private static boolean hatEinspringendeEcke(List<Block> rahmen, Set<Block> innen, Axis richtung) {
        for (Block obsidian : rahmen) {
            boolean innenDarueberOderDarunter = innen.contains(obsidian.getRelative(0, 1, 0))
                    || innen.contains(obsidian.getRelative(0, -1, 0));

            boolean innenDaneben;
            if (richtung == Axis.X) {
                innenDaneben = innen.contains(obsidian.getRelative(-1, 0, 0))
                        || innen.contains(obsidian.getRelative(1, 0, 0));
            } else {
                innenDaneben = innen.contains(obsidian.getRelative(0, 0, -1))
                        || innen.contains(obsidian.getRelative(0, 0, 1));
            }

            if (innenDarueberOderDarunter && innenDaneben) {
                return true;
            }
        }
        return false;
    }


    // ------------------------------------------------------------------
    // Portal setzen
    // ------------------------------------------------------------------

    private static void fuellePortal(List<Block> innenflaeche, Axis richtung) {
        for (Block block : innenflaeche) {
            Orientable portal = (Orientable) Material.NETHER_PORTAL.createBlockData();
            portal.setAxis(richtung);

            // Ohne Block-Updates setzen: Minecraft wuerde nicht-rechteckige Portale sonst sofort
            // wieder aufloesen. (Das Datapack setzt die Bloecke ebenfalls ohne Formpruefung.)
            block.setBlockData(portal, false);
        }
    }


    // ------------------------------------------------------------------
    // Block-Pruefungen
    // ------------------------------------------------------------------

    private static boolean istObsidian(Material material) {
        return material == Material.OBSIDIAN || material == Material.CRYING_OBSIDIAN;
    }


    /** Luft (auch Hoehlen- und Leere-Luft) oder Feuer – wie der Tag "custom_nether_portals:air". */
    private static boolean istLeer(Material material) {
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || Tag.FIRE.isTagged(material);
    }


    /** Innerhalb der Bauhoehe und in einem geladenen Chunk (entspricht der "valid"-Pruefung im Datapack). */
    private static boolean liegtImGeladenenBereich(Block block) {
        World welt = block.getWorld();

        if (block.getY() < welt.getMinHeight() || block.getY() >= welt.getMaxHeight()) {
            return false;
        }
        return welt.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }
}

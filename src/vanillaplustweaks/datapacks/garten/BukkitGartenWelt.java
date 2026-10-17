package vanillaplustweaks.datapacks.garten;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Die echte Minecraft-Welt fuer die Garten-Logik.
 *
 * Beruecksichtigt werden nur Block-Displays in der Naehe (Umkreis 3 Bloecke) – alle Abstaende
 * in der Logik sind kleiner als 1 Block, mehr ist nie noetig.
 */
public final class BukkitGartenWelt implements GartenWelt {

    private static final double UMKREIS = 3.0;

    private final World welt;
    private final NamespacedKey gepflanztSchluessel;
    private final Consumer<BlockDisplay> neueAnzeige;
    private final List<Anzeige> anzeigen = new ArrayList<>();

    /** Item, das gerade gepflanzt wird (wird an neuen Anzeigen gespeichert, damit es spaeter zurueckkommt). */
    private String pflanzItem;


    /**
     * @param mitte              Umkreis-Mittelpunkt (die Fuesse des Spielers)
     * @param gepflanztSchluessel Speicher-Schluessel fuer das gepflanzte Item
     * @param neueAnzeige        wird fuer jede neu erschaffene Anzeige aufgerufen (zum Beobachten)
     */
    public BukkitGartenWelt(Location mitte, NamespacedKey gepflanztSchluessel, Consumer<BlockDisplay> neueAnzeige) {
        this.welt = mitte.getWorld();
        this.gepflanztSchluessel = gepflanztSchluessel;
        this.neueAnzeige = neueAnzeige;

        for (Entity entity : welt.getNearbyEntities(mitte, UMKREIS, UMKREIS, UMKREIS)) {
            if (entity instanceof BlockDisplay display) {
                anzeigen.add(new Anzeige(display));
            }
        }
    }


    /** Fuer eine einzelne, schon bekannte Anzeige (Pruefung auf kaputte Toepfe). */
    public BukkitGartenWelt(BlockDisplay display, NamespacedKey gepflanztSchluessel) {
        this.welt = display.getWorld();
        this.gepflanztSchluessel = gepflanztSchluessel;
        this.neueAnzeige = d -> { };
        anzeigen.add(new Anzeige(display));
    }


    public void setzePflanzItem(String itemId) {
        this.pflanzItem = itemId;
    }


    public Pflanze einzige() {
        return anzeigen.get(0);
    }


    // ------------------------------------------------------------------
    // GartenWelt
    // ------------------------------------------------------------------

    @Override
    public Block block(int x, int y, int z) {
        return block(welt.getBlockAt(x, y, z).getBlockData());
    }


    /** BlockData -> ID + Eigenschaften (aus "minecraft:player_head[powered=false,rotation=5]"). */
    static Block block(BlockData daten) {
        String text = daten.getAsString();
        int klammer = text.indexOf('[');
        if (klammer < 0) {
            return new Block(text, Map.of());
        }

        Map<String, String> eigenschaften = new HashMap<>();
        for (String paar : text.substring(klammer + 1, text.length() - 1).split(",")) {
            int gleich = paar.indexOf('=');
            eigenschaften.put(paar.substring(0, gleich), paar.substring(gleich + 1));
        }
        return new Block(text.substring(0, klammer), eigenschaften);
    }


    @Override
    public List<? extends Pflanze> pflanzen() {
        anzeigen.removeIf(anzeige -> !anzeige.display.isValid());
        return new ArrayList<>(anzeigen);
    }


    @Override
    public Pflanze erschaffe(Vek position, Set<String> markierungen, String blockZustand, Darstellung darstellung) {
        Location ort = new Location(welt, position.x(), position.y(), position.z(), 0f, 0f);

        BlockDisplay display = welt.spawn(ort, BlockDisplay.class, neu -> {
            for (String markierung : markierungen) {
                neu.addScoreboardTag(markierung);
            }
            neu.setBlock(blockZustand == null ? Material.AIR.createBlockData() : Bukkit.createBlockData(blockZustand));
            neu.setTransformation(new Transformation(
                    vektor(darstellung.verschiebung()),
                    quaternion(darstellung.drehungLinks()),
                    vektor(darstellung.groesse()),
                    quaternion(darstellung.drehungRechts())));

            if (pflanzItem != null) {
                neu.getPersistentDataContainer().set(gepflanztSchluessel, PersistentDataType.STRING, pflanzItem);
            }
        });

        neueAnzeige.accept(display);
        Anzeige anzeige = new Anzeige(display);
        anzeigen.add(anzeige);
        return anzeige;
    }


    @Override
    public void entferne(Pflanze pflanze) {
        ((Anzeige) pflanze).display.remove();
    }


    @Override
    public void lasseFallen(Vek position, Pflanze vonPflanze) {
        ItemStack item = PflanzenItem.fuer(((Anzeige) vonPflanze).display, gepflanztSchluessel);
        if (item != null) {
            welt.dropItem(new Location(welt, position.x(), position.y(), position.z()), item);
        }
    }


    /** Pflanze aus dem Topf nehmen: Anzeige entfernen und das passende Item zurueckgeben (oder null). */
    public ItemStack herausnehmen(Pflanze pflanze) {
        BlockDisplay display = ((Anzeige) pflanze).display;
        ItemStack item = PflanzenItem.fuer(display, gepflanztSchluessel);
        display.remove();
        return item;
    }


    private static Vector3f vektor(float[] werte) {
        return new Vector3f(werte[0], werte[1], werte[2]);
    }


    private static Quaternionf quaternion(float[] werte) {
        return new Quaternionf(werte[0], werte[1], werte[2], werte[3]);
    }


    // ------------------------------------------------------------------
    // Eine Anzeige (Block-Display)
    // ------------------------------------------------------------------

    private static final class Anzeige implements Pflanze {

        private final BlockDisplay display;

        Anzeige(BlockDisplay display) {
            this.display = display;
        }

        @Override
        public Vek position() {
            Location ort = display.getLocation();
            return new Vek(ort.getX(), ort.getY(), ort.getZ());
        }

        @Override
        public void setzePosition(Vek position) {
            Location ort = display.getLocation();
            display.teleport(new Location(display.getWorld(), position.x(), position.y(), position.z(), ort.getYaw(), ort.getPitch()));
        }

        @Override
        public Set<String> markierungen() {
            return display.getScoreboardTags();
        }

        @Override
        public void markiere(String markierung) {
            display.addScoreboardTag(markierung);
        }

        @Override
        public void entmarkiere(String markierung) {
            display.removeScoreboardTag(markierung);
        }

        @Override
        public String blockId() {
            return display.getBlock().getMaterial().getKey().toString();
        }

        @Override
        public void setzeBlockVonItem(String itemId) {
            // Wie im Datapack: nur wenn es zu der Item-ID auch einen Block gibt
            Material material = Material.matchMaterial(itemId);
            if (material != null && material.isBlock()) {
                display.setBlock(material.createBlockData());
            }
        }

        @Override
        public float gierung() {
            return display.getLocation().getYaw();
        }

        @Override
        public void setzeGierung(float gierung) {
            display.setRotation(gierung, display.getLocation().getPitch());
        }

        @Override
        public void setzeVerschiebungUndGroesse(float[] verschiebung, float[] groesse) {
            Transformation alt = display.getTransformation();
            display.setTransformation(new Transformation(
                    vektor(verschiebung), alt.getLeftRotation(), vektor(groesse), alt.getRightRotation()));
        }
    }
}

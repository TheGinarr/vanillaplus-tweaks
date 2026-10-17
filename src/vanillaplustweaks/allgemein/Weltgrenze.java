package vanillaplustweaks.allgemein;

import vanillaplustweaks.Modul;
import vanillaplustweaks.NeuLadbar;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Ersetzt ein eigenes Weltgrenzen-Datapack.
 *
 * Setzt beim Serverstart (und bei "/vptweaks reload") die Weltgrenze jeder Dimension:
 * Mittelpunkt und Durchmesser in Bloecken – genau wie vorher der Befehl "worldborder set".
 */
public final class Weltgrenze extends Modul implements NeuLadbar {

    public Weltgrenze(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "world-border";
    }


    @Override
    public void start() {
        neuLaden();
    }


    @Override
    public void neuLaden() {
        for (World welt : plugin.getServer().getWorlds()) {
            String dimension = configNameFuer(welt.getEnvironment());
            if (dimension == null) {
                continue;
            }

            double mitteX = kommazahl(dimension + ".center-x", 0);
            double mitteZ = kommazahl(dimension + ".center-z", 0);
            double durchmesser = kommazahl(dimension + ".diameter", 0);

            if (durchmesser <= 0) {
                // 0 oder leer = Weltgrenze dieser Dimension nicht anfassen
                continue;
            }

            WorldBorder grenze = welt.getWorldBorder();
            grenze.setCenter(mitteX, mitteZ);
            grenze.setSize(durchmesser);

            log(dimension + ": Mitte " + mitteX + " / " + mitteZ + ", Durchmesser " + durchmesser);
        }
    }


    private static String configNameFuer(World.Environment dimension) {
        switch (dimension) {
            case NORMAL:
                return "overworld";
            case NETHER:
                return "nether";
            case THE_END:
                return "end";
            default:
                return null;
        }
    }
}

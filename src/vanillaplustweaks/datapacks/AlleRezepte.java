package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Ersetzt das Datapack "Unlock All Recipes" (Vanilla Tweaks).
 *
 * Jeder Spieler kennt alle Rezepte im Rezeptbuch – auch die aus Datapacks und Plugins.
 *
 * Unterschied zum Datapack: Das Datapack hat alle 5 Sekunden "recipe give @a *" ausgefuehrt.
 * Hier passiert das nur, wenn es noetig ist:
 *   - wenn ein Spieler den Server betritt,
 *   - wenn Datapacks neu geladen werden (/reload, /minecraft:reload, /datapack enable ...).
 */
public final class AlleRezepte extends Modul implements Listener {

    public AlleRezepte(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "unlock-all-recipes";
    }


    @Override
    public void start() {
        registriereListener(this);

        // Spieler, die beim Laden des Plugins schon online sind (z. B. nach einem Plugin-Neustart)
        for (Player spieler : plugin.getServer().getOnlinePlayers()) {
            alleFreischalten(spieler);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void beimBetreten(PlayerJoinEvent event) {
        alleFreischalten(event.getPlayer());
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void nachDatapackReload(ServerResourcesReloadedEvent event) {
        List<NamespacedKey> rezepte = alleRezeptSchluessel();

        for (Player spieler : plugin.getServer().getOnlinePlayers()) {
            spieler.discoverRecipes(rezepte);
        }
    }


    private void alleFreischalten(Player spieler) {
        spieler.discoverRecipes(alleRezeptSchluessel());
    }


    /** Die Namen (Schluessel) aller Rezepte, die der Server gerade kennt. */
    private List<NamespacedKey> alleRezeptSchluessel() {
        List<NamespacedKey> schluessel = new ArrayList<>();

        Iterator<Recipe> alle = plugin.getServer().recipeIterator();
        while (alle.hasNext()) {
            Recipe rezept = alle.next();
            if (rezept instanceof Keyed mitNamen) {
                schluessel.add(mitNamen.getKey());
            }
        }
        return schluessel;
    }
}

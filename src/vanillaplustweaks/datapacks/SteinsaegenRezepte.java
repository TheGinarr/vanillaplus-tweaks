package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ersetzt die Rezept-Datapacks MasterCutter, Painting Picker und Wood Stripper sowie die
 * Deko-Rezepte von Gardener's Dream. Alle sind Steinsaegen-Rezepte.
 *
 * Die Rezepte stehen lesbar in der Datei "daten/rezepte.yml" im Plugin. Sie werden einmal beim
 * Start registriert (und nach einem /minecraft:reload erneut) – danach kosten sie keine Rechenzeit.
 *
 * Jedes Pack laesst sich in der config.yml einzeln abschalten.
 */
public final class SteinsaegenRezepte extends Modul implements Listener {

    /** Rezept-Name beginnt mit ... -> Schalter in der Config */
    private static final Map<String, String> PACK_SCHALTER = Map.of(
            "mastercutter/", "mastercutter",
            "painting_picker/", "painting-picker",
            "wood_stripper/", "wood-stripper",
            "gardeners_dream/", "gardeners-dream-decorations");

    private final List<StonecuttingRecipe> rezepte = new ArrayList<>();


    public SteinsaegenRezepte(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "stonecutter-recipes";
    }


    @Override
    public void start() {
        YamlConfiguration daten = datenDatei("rezepte.yml");
        int fehlerhaft = 0;

        for (Map<?, ?> eintrag : daten.getMapList("steinsaege")) {
            String name = String.valueOf(eintrag.get("name"));
            if (!packEingeschaltet(name)) {
                continue;
            }

            try {
                rezepte.add(baueRezept(name, eintrag));
            } catch (IllegalArgumentException fehler) {
                fehlerhaft++;
                warnung("Rezept " + name + " uebersprungen: " + fehler.getMessage());
            }
        }

        registriereAlle();
        registriereListener(this);

        log(rezepte.size() + " Steinsaegen-Rezepte registriert" + (fehlerhaft > 0 ? ", " + fehlerhaft + " fehlerhaft" : "") + ".");
    }


    private boolean packEingeschaltet(String rezeptName) {
        for (Map.Entry<String, String> pack : PACK_SCHALTER.entrySet()) {
            if (rezeptName.startsWith(pack.getKey())) {
                return schalter(pack.getValue(), true);
            }
        }
        return true;
    }


    // ------------------------------------------------------------------
    // Ein Rezept aus der Liste bauen
    // ------------------------------------------------------------------

    private StonecuttingRecipe baueRezept(String name, Map<?, ?> eintrag) {
        // Ergebnis: Item-Text wie im /give-Befehl, z. B. minecraft:painting[minecraft:painting/variant="minecraft:wither"]
        ItemStack ergebnis = Bukkit.getItemFactory().createItemStack(String.valueOf(eintrag.get("ergebnis")));
        Object anzahl = eintrag.get("anzahl");
        ergebnis.setAmount(anzahl instanceof Number zahl ? zahl.intValue() : 1);

        RecipeChoice zutat = zutat((List<?>) eintrag.get("zutat"));

        return new StonecuttingRecipe(new NamespacedKey(plugin, name), ergebnis, zutat);
    }


    /** Zutat: eine Liste von Items oder ein Item-Tag wie "#minecraft:hanging_signs". */
    private static RecipeChoice zutat(List<?> eintraege) {
        if (eintraege.size() == 1 && String.valueOf(eintraege.get(0)).startsWith("#")) {
            String tagName = String.valueOf(eintraege.get(0)).substring(1);
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.fromString(tagName), Material.class);
            if (tag == null) {
                throw new IllegalArgumentException("unbekannter Tag " + tagName);
            }
            return new RecipeChoice.MaterialChoice(tag);
        }

        List<Material> items = new ArrayList<>();
        for (Object id : eintraege) {
            Material material = Material.matchMaterial(String.valueOf(id));
            if (material == null) {
                throw new IllegalArgumentException("unbekanntes Item " + id);
            }
            items.add(material);
        }
        return new RecipeChoice.MaterialChoice(items);
    }


    // ------------------------------------------------------------------
    // Registrieren – auf einmal, damit die Spieler-Rezeptlisten nur einmal aktualisiert werden
    // ------------------------------------------------------------------

    private void registriereAlle() {
        List<Recipe> fehlende = new ArrayList<>();
        for (StonecuttingRecipe rezept : rezepte) {
            if (plugin.getServer().getRecipe(rezept.getKey()) == null) {
                fehlende.add(rezept);
            }
        }

        if (!fehlende.isEmpty()) {
            plugin.getServer().addRecipes(fehlende);
        }
    }


    /** Nach /minecraft:reload sind Plugin-Rezepte weg -> wieder hinzufuegen (vor "Alle Rezepte freischalten"). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void nachDatapackReload(ServerResourcesReloadedEvent event) {
        registriereAlle();
    }
}

package vanillaplustweaks;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.tag.TagKey;
import org.bukkit.block.BlockType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Set;

/**
 * Laeuft, bevor die Welt geladen wird.
 *
 * Das Plugin bringt KEINE Datapacks mehr mit – alles ist Plugin-Code.
 * Nur eine Sache muss so frueh passieren: Block-Tags lassen sich nur beim Laden der Spieldaten
 * aendern. Fuer Gardener's Dream werden Spielerkoepfe (die Deko-Toepfe) mit Axt und Spitzhacke
 * schneller abbaubar – wie im Original-Datapack.
 */
public final class TweaksBootstrap implements PluginBootstrap {

    private static final TagKey<BlockType> MIT_AXT = TagKey.create(RegistryKey.BLOCK, "minecraft:mineable/axe");
    private static final TagKey<BlockType> MIT_SPITZHACKE = TagKey.create(RegistryKey.BLOCK, "minecraft:mineable/pickaxe");

    private static final Set<TypedKey<BlockType>> KOPF_BLOECKE = Set.of(
            TypedKey.create(RegistryKey.BLOCK, "minecraft:player_head"),
            TypedKey.create(RegistryKey.BLOCK, "minecraft:player_wall_head"));


    @Override
    public void bootstrap(BootstrapContext kontext) {
        // Die Config wird hier direkt gelesen, weil das Plugin selbst noch nicht geladen ist
        File configDatei = kontext.getDataDirectory().resolve("config.yml").toFile();
        YamlConfiguration config = configDatei.isFile() ? YamlConfiguration.loadConfiguration(configDatei) : null;

        boolean gaertnerTraum = (config == null) || config.getBoolean("gardeners-dream.enabled", true);
        if (!gaertnerTraum) {
            return;
        }

        kontext.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.BLOCK), event -> {
            event.registrar().addToTag(MIT_AXT, KOPF_BLOECKE);
            event.registrar().addToTag(MIT_SPITZHACKE, KOPF_BLOECKE);
        });
    }
}

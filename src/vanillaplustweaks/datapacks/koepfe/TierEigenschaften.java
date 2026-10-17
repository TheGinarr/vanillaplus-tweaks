package vanillaplustweaks.datapacks.koepfe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Keyed;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Goat;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.ZombieNautilus;
import org.bukkit.entity.ZombieVillager;

import java.util.Locale;

/**
 * Liest die Eigenschaften eines Tiers, die in der Kopf-Liste als Bedingung vorkommen.
 * Die Werte sind so geschrieben wie in Minecraft selbst (z. B. "white_splotched" beim Hasen),
 * damit sie zu den Namen aus dem Datapack passen.
 *
 *   variante      Fell-/Farbvariante (Wolf, Katze, Huhn, Kuh, Schwein, Frosch, Pferd, Lama, Hase, Axolotl, Papagei, Fuchs, Pilzkuh, Zombie-Nautilus)
 *   farbe         Wollfarbe des Schafs
 *   wuetend       Biene/Wolf wuetend? (true/false)
 *   nektar        Biene hat Nektar? (true/false)
 *   schreiend     Schreiende Ziege? (true/false)
 *   aufgeladen    Geladener Creeper? (true/false)
 *   hauptgen, nebengen   Gene des Pandas
 *   verwitterung  Kupfergolem: unaffected, exposed, weathered, oxidized
 *   beruf         Beruf von Dorfbewohner / Zombie-Dorfbewohner
 *   name          eigener Name (fuer das jeb_-Schaf)
 */
public final class TierEigenschaften {

    private TierEigenschaften() {
    }


    /** Wert der Eigenschaft fuer dieses Tier – oder null, wenn das Tier sie nicht hat. */
    public static String wert(LivingEntity tier, String eigenschaft) {
        return switch (eigenschaft) {
            case "variante" -> variante(tier);
            case "farbe" -> tier instanceof Sheep schaf && schaf.getColor() != null ? klein(schaf.getColor().name()) : null;
            case "wuetend" -> wuetend(tier);
            case "nektar" -> tier instanceof Bee biene ? String.valueOf(biene.hasNectar()) : null;
            case "schreiend" -> tier instanceof Goat ziege ? String.valueOf(ziege.isScreaming()) : null;
            case "aufgeladen" -> tier instanceof Creeper creeper ? String.valueOf(creeper.isPowered()) : null;
            case "hauptgen" -> tier instanceof Panda panda ? klein(panda.getMainGene().name()) : null;
            case "nebengen" -> tier instanceof Panda panda ? klein(panda.getHiddenGene().name()) : null;
            case "verwitterung" -> tier instanceof CopperGolem golem ? klein(golem.getWeatheringState().name()) : null;
            case "beruf" -> beruf(tier);
            case "name" -> eigenerName(tier);
            default -> null;
        };
    }


    private static String variante(LivingEntity tier) {
        // Varianten aus Registern (Name wie in Minecraft, z. B. "ashen")
        if (tier instanceof Wolf wolf) return schluessel(wolf.getVariant());
        if (tier instanceof Cat katze) return schluessel(katze.getCatType());
        if (tier instanceof Chicken huhn) return schluessel(huhn.getVariant());
        if (tier instanceof Cow kuh) return schluessel(kuh.getVariant());
        if (tier instanceof Pig schwein) return schluessel(schwein.getVariant());
        if (tier instanceof Frog frosch) return schluessel(frosch.getVariant());
        if (tier instanceof ZombieNautilus nautilus) return schluessel(nautilus.getVariant());

        // Varianten als feste Aufzaehlung – Namen meist gleich wie in Minecraft
        if (tier instanceof Horse pferd) return klein(pferd.getColor().name());
        if (tier instanceof Llama lama) return klein(lama.getColor().name()); // auch Haendler-Lama
        if (tier instanceof Axolotl axolotl) return klein(axolotl.getVariant().name());
        if (tier instanceof Fox fuchs) return klein(fuchs.getFoxType().name());
        if (tier instanceof MushroomCow pilzkuh) return klein(pilzkuh.getVariant().name());

        // Hier heissen die Varianten in Paper anders als in Minecraft
        if (tier instanceof Rabbit hase) {
            return switch (hase.getRabbitType()) {
                case BLACK_AND_WHITE -> "white_splotched";
                case SALT_AND_PEPPER -> "salt";
                case THE_KILLER_BUNNY -> "evil";
                default -> klein(hase.getRabbitType().name());
            };
        }
        if (tier instanceof Parrot papagei) {
            return switch (papagei.getVariant()) {
                case RED -> "red_blue";
                case CYAN -> "yellow_blue";
                default -> klein(papagei.getVariant().name());
            };
        }
        return null;
    }


    private static String wuetend(LivingEntity tier) {
        if (tier instanceof Bee biene) {
            return String.valueOf(biene.getAnger() > 0);
        }
        if (tier instanceof Wolf wolf) {
            return String.valueOf(wolf.isAngry());
        }
        return null;
    }


    private static String beruf(LivingEntity tier) {
        if (tier instanceof Villager dorfbewohner) {
            return schluessel(dorfbewohner.getProfession());
        }
        if (tier instanceof ZombieVillager zombie) {
            return schluessel(zombie.getVillagerProfession());
        }
        return null;
    }


    private static String eigenerName(LivingEntity tier) {
        Component name = tier.customName();
        return name == null ? null : PlainTextComponentSerializer.plainText().serialize(name);
    }


    private static String schluessel(Keyed eintrag) {
        return eintrag == null ? null : eintrag.key().value();
    }


    private static String klein(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}

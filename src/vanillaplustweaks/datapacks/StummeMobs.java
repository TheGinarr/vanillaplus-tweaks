package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ersetzt das Datapack "Silence Mobs" (Vanilla Tweaks).
 *
 * Benennt man ein Tier oder Monster mit einem Namensschild "silence", wird es stumm.
 * Mit "unsilence" bekommt es seine Stimme zurueck. Gross-/Kleinschreibung ist egal.
 * Die alten Datapack-Namen ("silence me" usw.) funktionieren standardmaessig weiter.
 * Das Tier leuchtet kurz auf und es gibt einen Klang.
 *
 * Zwei Fehler im Original-Datapack (beide im Datapack-Code nachvollziehbar):
 *   - Der Name "silenced" sollte nach 3 Sekunden verschwinden. Das Datapack ruft dafuer eine Funktion
 *     "remove_names/entity" auf, die es nicht gibt – der Name blieb deshalb stehen.
 *     Hier einstellbar mit "remove-name-after-seconds". Standard 0 = Name bleibt wie bisher.
 *     Grund: Benannte Monster verschwinden in Minecraft nicht von selbst. Ohne Namen koennten
 *     stumm geschaltete Monster wieder despawnen.
 *   - Haelt man ein "unsilence me"-Namensschild in der Hand, sollten stumme Tiere aufleuchten.
 *     Das Datapack verweist dafuer auf eine falsch geschriebene Funktion ("silence_mob:glow_named").
 *     Hier funktioniert es.
 *
 * Bereits stumme Tiere aus Datapack-Zeiten werden weiter erkannt (Markierung "silence_mobs.silenced").
 */
public final class StummeMobs extends Modul implements Listener {

    /** Dieselbe Markierung wie im Datapack, damit alte stumme Tiere erkannt werden. */
    private static final String MARKIERUNG = "silence_mobs.silenced";

    private static final int LEUCHTEN_TICKS = 60;
    private static final double KLANG_RADIUS = 6;

    private final Set<String> stummNamen = new HashSet<>();
    private final Set<String> lautNamen = new HashSet<>();

    private int nameEntfernenNachTicks;
    private double leuchtRadius;


    public StummeMobs(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "silence-mobs";
    }


    @Override
    public void start() {
        for (String name : namenOderStandard("silence-names", "silence", "silence me", "silence_me")) {
            stummNamen.add(vereinheitlichen(name));
        }
        for (String name : namenOderStandard("unsilence-names", "unsilence", "unsilence me", "unsilence_me")) {
            lautNamen.add(vereinheitlichen(name));
        }

        nameEntfernenNachTicks = zahl("remove-name-after-seconds", 0) * 20;
        leuchtRadius = kommazahl("glow-radius-while-holding-unsilence-tag", 32);

        registriereListener(this);

        if (leuchtRadius > 0) {
            wiederholeAlle(20, this::stummeTiereAnzeigen);
        }
    }


    // ------------------------------------------------------------------
    // Namensschild benutzen
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimBenennen(PlayerInteractEntityEvent event) {
        ItemStack schild = gehaltenesItem(event.getPlayer(), event.getHand());
        String name = namensschildText(schild);
        if (name == null) {
            return;
        }

        boolean stummMachen = stummNamen.contains(vereinheitlichen(name));
        boolean lautMachen = lautNamen.contains(vereinheitlichen(name));
        if (!stummMachen && !lautMachen) {
            return;
        }

        if (!(event.getRightClicked() instanceof LivingEntity tier)) {
            return;
        }

        // Minecraft vergibt den Namen erst nach diesem Event. Einen Tick warten, dann pruefen,
        // ob der Name wirklich vergeben wurde (z. B. nicht bei Spielern oder ohne Erfolg).
        spaeter(1, () -> {
            if (!tier.isValid() || !name.equals(aktuellerName(tier))) {
                return;
            }

            if (stummMachen) {
                stummSchalten(tier);
            } else {
                lautSchalten(tier);
            }
        });
    }


    private void stummSchalten(LivingEntity tier) {
        tier.setSilent(true);
        tier.addScoreboardTag(MARKIERUNG);
        tier.customName(Component.text("silenced"));
        rueckmeldung(tier);
    }


    private void lautSchalten(LivingEntity tier) {
        tier.setSilent(false);
        tier.removeScoreboardTag(MARKIERUNG);
        tier.customName(Component.text("unsilenced"));
        rueckmeldung(tier);
    }


    /** Kurzes Aufleuchten, ein Klang, und der Hilfsname verschwindet wieder. */
    private void rueckmeldung(LivingEntity tier) {
        tier.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, LEUCHTEN_TICKS, 0, true, false));

        for (Player spieler : tier.getWorld().getPlayers()) {
            if (spieler.getLocation().distanceSquared(tier.getLocation()) <= KLANG_RADIUS * KLANG_RADIUS) {
                spieler.playSound(tier.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 2.0f);
            }
        }

        if (nameEntfernenNachTicks > 0) {
            String vergebenerName = aktuellerName(tier);
            spaeter(nameEntfernenNachTicks, () -> {
                // Nur entfernen, wenn der Spieler das Tier nicht inzwischen anders benannt hat
                if (tier.isValid() && vergebenerName != null && vergebenerName.equals(aktuellerName(tier))) {
                    tier.customName(null);
                }
            });
        }
    }


    // ------------------------------------------------------------------
    // "unsilence"-Schild in der Hand: stumme Tiere in der Naehe leuchten
    // ------------------------------------------------------------------

    private void stummeTiereAnzeigen() {
        for (Player spieler : plugin.getServer().getOnlinePlayers()) {
            if (!haeltLautSchild(spieler)) {
                continue;
            }

            for (Entity entity : spieler.getNearbyEntities(leuchtRadius, leuchtRadius, leuchtRadius)) {
                if (entity instanceof LivingEntity tier && tier.getScoreboardTags().contains(MARKIERUNG)) {
                    tier.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 30, 0, true, false));
                }
            }
        }
    }


    private boolean haeltLautSchild(Player spieler) {
        String haupthand = namensschildText(spieler.getInventory().getItemInMainHand());
        String nebenhand = namensschildText(spieler.getInventory().getItemInOffHand());

        return (haupthand != null && lautNamen.contains(vereinheitlichen(haupthand)))
                || (nebenhand != null && lautNamen.contains(vereinheitlichen(nebenhand)));
    }


    // ------------------------------------------------------------------
    // Hilfsmethoden
    // ------------------------------------------------------------------

    /** Namen aus der Config – fehlt der Eintrag, gelten dieselben Namen wie im Datapack. */
    private List<String> namenOderStandard(String schluessel, String... standard) {
        List<String> ausConfig = textListe(schluessel);
        return ausConfig.isEmpty() ? List.of(standard) : ausConfig;
    }


    /** "Silence ", "SILENCE" und "silence" gelten als derselbe Name. */
    private static String vereinheitlichen(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }


    private static ItemStack gehaltenesItem(Player spieler, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) {
            return spieler.getInventory().getItemInOffHand();
        }
        return spieler.getInventory().getItemInMainHand();
    }


    /** Der Name auf einem Namensschild als reiner Text – oder null, wenn es kein benanntes Namensschild ist. */
    private static String namensschildText(ItemStack item) {
        if (item == null || item.getType() != Material.NAME_TAG) {
            return null;
        }

        Component name = item.getData(DataComponentTypes.CUSTOM_NAME);
        if (name == null) {
            return null;
        }
        return PlainTextComponentSerializer.plainText().serialize(name);
    }


    private static String aktuellerName(Entity entity) {
        Component name = entity.customName();
        if (name == null) {
            return null;
        }
        return PlainTextComponentSerializer.plainText().serialize(name);
    }
}

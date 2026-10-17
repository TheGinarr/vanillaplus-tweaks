package vanillaplustweaks.datapacks.koepfe;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Ersetzt das Datapack "More Mob Heads" (Vanilla Tweaks).
 *
 * Wird ein Tier oder Monster von einem Spieler getoetet, droppt es mit etwas Glueck seinen Kopf –
 * passend zur Variante (z. B. warme Kuh, wuetender Wolf, Bauern-Dorfbewohner). Die Koepfe
 * (Texturen, Namen, Klang auf dem Notenblock) stehen in "daten/koepfe.yml" im Plugin.
 *
 * Drop-Chance wie der Witherskelett-Schaedel in Vanilla (einstellbar):
 *   2,5 % ohne Pluenderung, +1 % je Stufe Pluenderung (Pluenderung III = 5,5 %).
 * Das Datapack hatte pro Tier eigene Chancen (0,5 % bis 100 %) – die gelten hier nicht mehr.
 * Einzelne Tiere koennen in der Config eine eigene Chance bekommen (chance-per-mob).
 */
public final class TierKoepfe extends Modul implements Listener {

    private final Random zufall = new Random();
    private final Map<String, Double> eigeneChancen = new LinkedHashMap<>();

    private KopfTabelle<ItemStack> tabelle;
    private double chance;
    private double proPluenderungsStufe;


    public TierKoepfe(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "mob-heads";
    }


    @Override
    public void start() {
        chance = kommazahl("chance", 0.025);
        proPluenderungsStufe = kommazahl("looting-bonus-per-level", 0.01);

        ConfigurationSection proTier = config().getConfigurationSection(configAbschnitt() + ".chance-per-mob");
        if (proTier != null) {
            for (String tier : proTier.getKeys(false)) {
                eigeneChancen.put(tier, proTier.getDouble(tier));
            }
        }

        // Alle Koepfe einmal beim Start bauen und im Speicher halten (spart spaeter Rechenzeit)
        Map<String, Object> daten = YamlKarten.alsKarte(datenDatei("koepfe.yml"));
        tabelle = KopfTabelle.aus(daten, itemText -> Bukkit.getItemFactory().createItemStack(itemText));

        registriereListener(this);
        log(tabelle.anzahlTiere() + " Tiere mit Koepfen, Chance " + prozent(chance) + " (+" + prozent(proPluenderungsStufe) + " je Pluenderungs-Stufe).");
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimTod(EntityDeathEvent event) {
        LivingEntity tier = event.getEntity();
        if (tier instanceof Player) {
            return; // Spielerkoepfe: eigenes Modul
        }

        Player killer = tier.getKiller();
        String tierName = tier.getType().getKey().getKey();
        if (killer == null || !tabelle.kennt(tierName)) {
            return;
        }

        int pluenderung = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        double tierChance = eigeneChancen.getOrDefault(tierName, chance);
        if (zufall.nextDouble() >= tierChance + pluenderung * proPluenderungsStufe) {
            return;
        }

        ItemStack kopf = tabelle.kopfFuer(tierName, eigenschaft -> TierEigenschaften.wert(tier, eigenschaft));
        if (kopf == null) {
            return;
        }
        event.getDrops().add(kopf.clone());

        // Wither: wie im Datapack in der Haelfte der Faelle zusaetzlich ein Geschoss-Kopf
        KopfTabelle.Zusatz<ItemStack> zusatz = tabelle.zusatzFuer(tierName);
        if (zusatz != null && zufall.nextDouble() < zusatz.chance()) {
            ItemStack extra = zusatz.zufaelligEinerVon().get(zufall.nextInt(zusatz.zufaelligEinerVon().size()));
            event.getDrops().add(extra.clone());
        }
    }


    private static String prozent(double wert) {
        return String.format(Locale.GERMANY, "%.1f %%", wert * 100);
    }
}

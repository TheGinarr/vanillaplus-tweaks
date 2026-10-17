package vanillaplustweaks.datapacks.koepfe;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Random;

/**
 * Ersetzt das Datapack "Player Head Drops" (Vanilla Tweaks).
 *
 * Wird ein Spieler von einem anderen Spieler getoetet, droppt mit etwas Glueck sein Kopf.
 * Darunter steht "Killed by <Name>" – wie im Datapack.
 *
 * Drop-Chance doppelt so hoch wie der Witherskelett-Schaedel (einstellbar):
 *   5 % ohne Pluenderung, +2 % je Stufe Pluenderung (Pluenderung III = 11 %).
 * Das Datapack hat den Kopf bei jedem Spieler-Kill gedroppt (100 %).
 */
public final class SpielerKoepfe extends Modul implements Listener {

    private final Random zufall = new Random();

    private double chance;
    private double proPluenderungsStufe;


    public SpielerKoepfe(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "player-heads";
    }


    @Override
    public void start() {
        chance = kommazahl("chance", 0.05);
        proPluenderungsStufe = kommazahl("looting-bonus-per-level", 0.02);
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimTod(PlayerDeathEvent event) {
        Player opfer = event.getPlayer();
        Player killer = opfer.getKiller();
        if (killer == null || killer.equals(opfer)) {
            return;
        }

        int pluenderung = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        if (zufall.nextDouble() >= chance + pluenderung * proPluenderungsStufe) {
            return;
        }

        // Direkt fallen lassen statt ueber die Drop-Liste: so droppt der Kopf auch,
        // wenn die Spielregel "keepInventory" an ist.
        opfer.getWorld().dropItemNaturally(opfer.getLocation(), kopfVon(opfer, killer));
    }


    private static ItemStack kopfVon(Player opfer, Player killer) {
        ItemStack kopf = ItemStack.of(Material.PLAYER_HEAD);

        kopf.editMeta(SkullMeta.class, meta -> {
            meta.setPlayerProfile(opfer.getPlayerProfile());

            Component zeile = Component.text("Killed by ", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(killer.getName(), NamedTextColor.YELLOW));
            meta.lore(List.of(zeile));
        });
        return kopf;
    }
}

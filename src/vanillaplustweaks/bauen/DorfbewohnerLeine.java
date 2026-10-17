package vanillaplustweaks.bauen;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Dorfbewohner und fahrende Haendler an die Leine nehmen.
 *
 * In Minecraft geht das nicht (sie sind als "nicht anleinbar" markiert). Haelt das Ziel aber erst
 * einmal eine Leine, behandelt Minecraft es ganz normal: Es folgt dem Spieler und reisst los, wenn
 * der Abstand zu gross wird (die Leine faellt dann auf den Boden).
 *
 *   Leine + Rechtsklick auf einen Dorfbewohner: angeleint (statt Handelsfenster).
 *   Rechtsklick auf den eigenen angeleinten Dorfbewohner: Leine wieder ab, sie kommt zurueck.
 *   Rechtsklick auf einen Zaun: angeleinte Dorfbewohner werden dort festgebunden.
 *   Rechtsklick auf den Knoten am Zaun: festgebundene Dorfbewohner kommen zurueck an die eigene Leine.
 *
 * Das Festbinden am Zaun muss das Plugin selbst machen: Minecraft prueft dabei erneut, ob das Tier
 * anleinbar ist, und lehnt Dorfbewohner ab. Andere Tiere an der Leine erledigt weiter Minecraft.
 */
public final class DorfbewohnerLeine extends Modul implements Listener {

    /** Wie weit ein angeleintes Tier vom Zaun weg sein darf (Minecraft: Leine reisst ab 12 Bloecken). */
    private static final double REICHWEITE = 12.0;

    private boolean haendler;


    public DorfbewohnerLeine(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "leash-villagers";
    }


    @Override
    public void start() {
        haendler = schalter("wandering-traders", true);
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimRechtsklick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractVillager dorfbewohner) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!haendler && dorfbewohner.getType() != EntityType.VILLAGER) {
            return;
        }

        Player spieler = event.getPlayer();

        // Schon an der eigenen Leine -> abnehmen
        if (dorfbewohner.isLeashed()) {
            Entity halter = dorfbewohner.getLeashHolder();
            if (halter.equals(spieler)) {
                event.setCancelled(true);
                dorfbewohner.setLeashHolder(null);
                if (spieler.getGameMode() != GameMode.CREATIVE) {
                    gebeLeine(spieler);
                }
            }
            return;
        }

        ItemStack inHand = spieler.getInventory().getItemInMainHand();
        if (inHand.getType() != Material.LEAD) {
            return;
        }

        event.setCancelled(true); // kein Handelsfenster
        if (dorfbewohner.setLeashHolder(spieler)) {
            if (spieler.getGameMode() != GameMode.CREATIVE) {
                inHand.subtract();
            }
            Location ort = dorfbewohner.getLocation();
            ort.getWorld().playSound(ort, Sound.ITEM_LEAD_TIED, 1f, 1f);
            spieler.swingHand(EquipmentSlot.HAND);
        }
    }


    // ------------------------------------------------------------------
    // Zaun: angeleinte Dorfbewohner festbinden
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimZaunKlick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block zaun = event.getClickedBlock();
        if (zaun == null || !Tag.FENCES.isTagged(zaun.getType())) {
            return;
        }

        Player spieler = event.getPlayer();
        List<AbstractVillager> eigene = angeleintAn(spieler, zaun.getLocation().add(0.5, 0.5, 0.5));
        if (eigene.isEmpty()) {
            return;
        }

        // Einen Tick spaeter: dann hat Minecraft andere Tiere schon festgebunden und evtl. einen Knoten gesetzt
        spaeter(1, () -> {
            if (!Tag.FENCES.isTagged(zaun.getType())) {
                return;
            }
            LeashHitch knoten = knotenAn(zaun);
            for (AbstractVillager dorfbewohner : eigene) {
                if (dorfbewohner.isValid() && dorfbewohner.isLeashed() && spieler.equals(dorfbewohner.getLeashHolder())) {
                    dorfbewohner.setLeashHolder(knoten);
                }
            }
            zaun.getWorld().playSound(zaun.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_LEAD_TIED, 1f, 1f);
        });
    }


    /** Rechtsklick auf den Knoten: eigene Dorfbewohner festbinden oder festgebundene zurueckholen. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimKnotenKlick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof LeashHitch knoten) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player spieler = event.getPlayer();
        List<AbstractVillager> eigene = angeleintAn(spieler, knoten.getLocation());

        if (!eigene.isEmpty()) {
            for (AbstractVillager dorfbewohner : eigene) {
                dorfbewohner.setLeashHolder(knoten);
            }
            return;
        }

        // Nichts an der eigenen Leine -> festgebundene Dorfbewohner zurueck an den Spieler (wie Minecraft bei Tieren)
        for (AbstractVillager dorfbewohner : angeleintAn(knoten, knoten.getLocation())) {
            dorfbewohner.setLeashHolder(spieler);
        }
    }


    /** Dorfbewohner (und ggf. Haendler) in Reichweite, deren Leine dieser Halter haelt. */
    private List<AbstractVillager> angeleintAn(Entity halter, Location mitte) {
        List<AbstractVillager> ergebnis = new ArrayList<>();
        for (AbstractVillager dorfbewohner : mitte.getNearbyEntitiesByType(AbstractVillager.class, REICHWEITE)) {
            if (!haendler && dorfbewohner.getType() != EntityType.VILLAGER) {
                continue;
            }
            if (dorfbewohner.isLeashed() && halter.equals(dorfbewohner.getLeashHolder())) {
                ergebnis.add(dorfbewohner);
            }
        }
        return ergebnis;
    }


    /** Der vorhandene Leinen-Knoten am Zaun, sonst ein neuer. */
    private static LeashHitch knotenAn(Block zaun) {
        Location mitte = zaun.getLocation().add(0.5, 0.5, 0.5);
        for (LeashHitch knoten : mitte.getNearbyEntitiesByType(LeashHitch.class, 0.6)) {
            if (knoten.getLocation().getBlock().equals(zaun)) {
                return knoten;
            }
        }
        return zaun.getWorld().spawn(zaun.getLocation(), LeashHitch.class);
    }


    private static void gebeLeine(Player spieler) {
        for (ItemStack rest : spieler.getInventory().addItem(ItemStack.of(Material.LEAD)).values()) {
            spieler.getWorld().dropItem(spieler.getLocation(), rest);
        }
    }
}

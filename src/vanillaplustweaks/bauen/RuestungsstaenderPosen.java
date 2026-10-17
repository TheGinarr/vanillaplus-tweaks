package vanillaplustweaks.bauen;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

import java.util.List;

/**
 * Ruestungsstaender mit dem Stock einstellen:
 *
 *   Stock + Rechtsklick:              Arme einblenden und zur naechsten Pose wechseln.
 *   Schleichen + Stock + Rechtsklick: Bodenplatte ein-/ausblenden.
 *
 * Der Stock wird nicht verbraucht und landet nicht in der Hand des Staenders.
 * Die Posen sind feste Winkel in Grad (Kopf, Koerper, linker/rechter Arm, linkes/rechtes Bein).
 */
public final class RuestungsstaenderPosen extends Modul implements Listener {

    /** Eine Pose: Name und Winkel in Grad (x, y, z) fuer jedes Koerperteil. */
    private record Pose(String name, double[] kopf, double[] koerper,
                        double[] linkerArm, double[] rechterArm, double[] linkesBein, double[] rechtesBein) {
    }

    private static double[] w(double x, double y, double z) {
        return new double[] {x, y, z};
    }

    private static final double[] NULL = w(0, 0, 0);

    /** Die Reihenfolge, in der die Posen durchgeschaltet werden. */
    private static final List<Pose> POSEN = List.of(
            new Pose("Standard",    NULL,          NULL, w(-10, 0, -10), w(-15, 0, 10), w(-1, 0, -1), w(1, 0, 1)),
            new Pose("Gerade",      NULL,          NULL, NULL,           NULL,          NULL,         NULL),
            new Pose("Winken",      w(-5, 0, 0),   NULL, w(-10, 0, -10), w(-170, 0, 10), NULL,        NULL),
            new Pose("Salutieren",  NULL,          NULL, w(-10, 0, -10), w(-110, 35, 0), NULL,        NULL),
            new Pose("Arme hoch",   w(-15, 0, 0),  NULL, w(-170, 0, -15), w(-170, 0, 15), NULL,       NULL),
            new Pose("Zeigen",      NULL,          NULL, w(-10, 0, -10), w(-90, 0, 0),  NULL,         NULL),
            new Pose("Laufen",      NULL,          NULL, w(40, 0, -5),   w(-40, 0, 5),  w(-35, 0, 0), w(35, 0, 0)),
            new Pose("Rennen",      w(10, 0, 0),   w(5, 0, 0), w(60, 0, -5), w(-60, 0, 5), w(-50, 0, 0), w(45, 0, 0)),
            new Pose("Sitzen",      NULL,          NULL, w(-40, 0, -5),  w(-40, 0, 5),  w(-90, -10, 0), w(-90, 10, 0)),
            new Pose("Nachdenken",  w(15, 0, 0),   NULL, w(-10, 0, -10), w(-125, -30, 0), NULL,       NULL),
            new Pose("Haende vorn", NULL,          NULL, w(-90, 20, 0),  w(-90, -20, 0), NULL,        NULL),
            new Pose("Verbeugen",   w(30, 0, 0),   w(20, 0, 0), w(10, 0, -5), w(10, 0, 5), NULL,       NULL));

    /** Markierung am Staender: welche Pose zuletzt gesetzt wurde. */
    private static final String POSE_PREFIX = "vpt_pose_";


    public RuestungsstaenderPosen(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "armor-stand-poses";
    }


    @Override
    public void start() {
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimRechtsklick(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand staender) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player spieler = event.getPlayer();
        ItemStack inHand = spieler.getInventory().getItemInMainHand();
        if (inHand.getType() != Material.STICK || staender.isMarker()) {
            return;
        }

        // Kein Ausruesten des Stocks in die Hand des Staenders
        event.setCancelled(true);

        if (spieler.isSneaking()) {
            staender.setBasePlate(!staender.hasBasePlate());
        } else {
            int naechste = (aktuellePose(staender) + 1) % POSEN.size();
            setzePose(staender, naechste);
            spieler.sendActionBar(Component.text("Pose: " + POSEN.get(naechste).name(), NamedTextColor.GOLD));
        }

        staender.getWorld().playSound(staender.getLocation(), Sound.ENTITY_ARMOR_STAND_HIT, 0.6f, 1.2f);
    }


    private static int aktuellePose(ArmorStand staender) {
        for (String markierung : staender.getScoreboardTags()) {
            if (markierung.startsWith(POSE_PREFIX)) {
                try {
                    return Integer.parseInt(markierung.substring(POSE_PREFIX.length()));
                } catch (NumberFormatException ignoriert) {
                    return -1;
                }
            }
        }
        return -1; // noch nie eingestellt -> die naechste ist Pose 0
    }


    private static void setzePose(ArmorStand staender, int nummer) {
        Pose pose = POSEN.get(nummer);

        staender.setArms(true);
        staender.setHeadPose(winkel(pose.kopf()));
        staender.setBodyPose(winkel(pose.koerper()));
        staender.setLeftArmPose(winkel(pose.linkerArm()));
        staender.setRightArmPose(winkel(pose.rechterArm()));
        staender.setLeftLegPose(winkel(pose.linkesBein()));
        staender.setRightLegPose(winkel(pose.rechtesBein()));

        for (String markierung : List.copyOf(staender.getScoreboardTags())) {
            if (markierung.startsWith(POSE_PREFIX)) {
                staender.removeScoreboardTag(markierung);
            }
        }
        staender.addScoreboardTag(POSE_PREFIX + nummer);
    }


    private static EulerAngle winkel(double[] grad) {
        return new EulerAngle(Math.toRadians(grad[0]), Math.toRadians(grad[1]), Math.toRadians(grad[2]));
    }
}

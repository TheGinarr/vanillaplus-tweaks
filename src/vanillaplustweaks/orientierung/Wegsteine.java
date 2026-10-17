package vanillaplustweaks.orientierung;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Wegsteine: Teleport zu einem Leitstein mit einem verlinkten Kompass.
 *
 * Ablauf:
 *   1. Kompass wie in Minecraft an einem Leitstein verlinken (Rechtsklick auf den Leitstein).
 *      Den Kompass am Amboss benennen, z. B. "Basis" – das ist der Name des Wegsteins.
 *   2. Kompass in die Hand, schleichen und in die Luft rechtsklicken.
 *   3. Kurz stillstehen (Aufladen). Bewegung oder Schaden bricht ab.
 *   4. Teleport oben auf den Leitstein. Danach gilt eine Abklingzeit.
 *
 * Reichweite – Pyramide unter dem Leitstein wie beim Leuchtfeuer (3x3, 5x5, 7x7, 9x9):
 *   Jeder Block bringt Punkte (Eisen 1, Gold 2, Smaragd 3, Diamant 4, Netherit 10).
 *   Reichweite = Grundreichweite + Punkte x Faktor. Nur vollstaendige Ebenen zaehlen, von oben nach unten.
 *   Rechtsklick mit leerer Hand auf den Leitstein zeigt die Reichweite an.
 *
 * Es gibt keine Datenbank und keinen Dauerbetrieb: Der Kompass speichert das Ziel selbst (Minecraft),
 * gerechnet wird nur bei einem Teleport-Versuch.
 */
public final class Wegsteine extends Modul implements Listener {

    /** Ein laufender Aufladevorgang. */
    private record Aufladen(BukkitTask aufgabe, Location start) {
    }

    private final Map<Material, Integer> punkteProBlock = new EnumMap<>(Material.class);
    private final Map<UUID, Aufladen> aufladend = new HashMap<>();
    private final Map<UUID, Long> abklingenBis = new HashMap<>();

    private boolean nurMitSchleichen;
    private int aufladeTicks;
    private long abklingzeitMillis;
    private double grundReichweite;
    private double reichweiteProPunkt;
    private double hoechstReichweite;
    private boolean zwischenDimensionen;
    private boolean infoBeimKlick;
    private int xpStufen;
    private int xpStufenPro1000;
    private Material kostenItem;
    private int kostenAnzahl;


    public Wegsteine(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "waystones";
    }


    @Override
    public void start() {
        nurMitSchleichen = !text("activation", "sneak-right-click").equalsIgnoreCase("right-click");
        aufladeTicks = (int) Math.round(Math.max(0, kommazahl("charge-seconds", 3.0)) * 20);
        abklingzeitMillis = Math.round(Math.max(0, kommazahl("cooldown-seconds", 60.0)) * 1000);
        grundReichweite = kommazahl("base-range", 300);
        reichweiteProPunkt = kommazahl("range-per-point", 10);
        hoechstReichweite = kommazahl("max-range", 0);
        zwischenDimensionen = schalter("cross-dimension", false);
        infoBeimKlick = schalter("info-on-click", true);
        xpStufen = Math.max(0, zahl("cost.xp-levels", 0));
        xpStufenPro1000 = Math.max(0, zahl("cost.xp-levels-per-1000-blocks", 0));
        kostenItem = Material.matchMaterial(text("cost.item", ""));
        kostenAnzahl = Math.max(0, zahl("cost.item-amount", 0));

        ladePunkte();
        registriereListener(this);
    }


    private void ladePunkte() {
        punkteProBlock.put(Material.IRON_BLOCK, 1);
        punkteProBlock.put(Material.GOLD_BLOCK, 2);
        punkteProBlock.put(Material.EMERALD_BLOCK, 3);
        punkteProBlock.put(Material.DIAMOND_BLOCK, 4);
        punkteProBlock.put(Material.NETHERITE_BLOCK, 10);

        ConfigurationSection abschnitt = config().getConfigurationSection(configAbschnitt() + ".block-points");
        if (abschnitt == null) {
            return;
        }
        for (String name : abschnitt.getKeys(false)) {
            Material material = Material.matchMaterial(name);
            if (material != null && Tag.BEACON_BASE_BLOCKS.isTagged(material)) {
                punkteProBlock.put(material, abschnitt.getInt(name));
            } else {
                warnung("block-points: " + name + " ist kein Leuchtfeuer-Block und wird ignoriert.");
            }
        }
    }


    // ==================================================================
    // Ausloesen
    // ==================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void beimRechtsklick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player spieler = event.getPlayer();
        Block geklickt = event.getClickedBlock();

        // Leere Hand auf einen Leitstein: Reichweite anzeigen
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && infoBeimKlick && geklickt != null
                && geklickt.getType() == Material.LODESTONE && (event.getItem() == null || event.getItem().isEmpty())) {
            zeigeInfo(spieler, geklickt);
            return;
        }

        // Teleport: Kompass, Rechtsklick in die Luft – oder schleichend auf einen Block (aber nicht auf
        // einen Leitstein, dort verlinkt Minecraft). Beim Schleichen mit Item in der Hand oeffnet
        // Minecraft keine Truhen o. ae., es kann also nichts anderes ausgeloest werden.
        boolean luft = event.getAction() == Action.RIGHT_CLICK_AIR;
        boolean blockAberKeinLeitstein = event.getAction() == Action.RIGHT_CLICK_BLOCK && spieler.isSneaking()
                && geklickt != null && geklickt.getType() != Material.LODESTONE;
        if (!luft && !blockAberKeinLeitstein) {
            return;
        }

        ItemStack kompass = event.getItem();
        if (kompass == null || kompass.getType() != Material.COMPASS || !(kompass.getItemMeta() instanceof CompassMeta meta)
                || !meta.hasLodestone()) {
            return;
        }
        if (nurMitSchleichen && !spieler.isSneaking()) {
            return;
        }

        event.setCancelled(true);
        versuchen(spieler, meta.getLodestone(), name(kompass));
    }


    private void versuchen(Player spieler, Location leitstein, String name) {
        if (aufladend.containsKey(spieler.getUniqueId())) {
            return; // laedt schon
        }

        Long bis = abklingenBis.get(spieler.getUniqueId());
        if (bis != null && bis > System.currentTimeMillis()) {
            long sekunden = (bis - System.currentTimeMillis() + 999) / 1000;
            fehler(spieler, "Der Wegstein ist noch nicht bereit (" + sekunden + " s).");
            return;
        }

        String problem = pruefen(spieler, leitstein);
        if (problem != null) {
            fehler(spieler, problem);
            return;
        }

        if (aufladeTicks == 0) {
            teleportieren(spieler, leitstein, name);
            return;
        }
        aufladenStarten(spieler, leitstein, name);
    }


    /** Alle Bedingungen fuer den Teleport. Gibt eine Fehlermeldung zurueck, oder null wenn alles passt. */
    private String pruefen(Player spieler, Location leitstein) {
        World zielWelt = leitstein.getWorld();
        if (zielWelt == null) {
            return "Dieser Wegstein liegt in einer Welt, die es nicht mehr gibt.";
        }
        if (!zwischenDimensionen && !zielWelt.equals(spieler.getWorld())) {
            return "Wegsteine funktionieren nur innerhalb derselben Dimension.";
        }
        if (plugin.dimensionGesperrt(leitstein)) {
            return "Diese Dimension ist noch gesperrt.";
        }
        if (spieler.isInsideVehicle()) {
            return "Bitte erst absteigen.";
        }

        Block block = leitstein.getBlock();
        if (block.getType() != Material.LODESTONE) {
            return "Der Leitstein dieses Wegsteins existiert nicht mehr.";
        }
        if (!frei(block.getRelative(0, 1, 0)) || !frei(block.getRelative(0, 2, 0))) {
            return "Der Wegstein ist zugebaut – ueber dem Leitstein muessen 2 Bloecke frei sein.";
        }

        double entfernung = entfernung(spieler.getLocation(), leitstein);
        double reichweite = reichweite(block);
        if (entfernung > reichweite) {
            return String.format(Locale.GERMANY, "Zu weit weg: %,.0f Bloecke, Reichweite %,.0f.", entfernung, reichweite);
        }

        String kosten = kostenFehlen(spieler, entfernung);
        if (kosten != null) {
            return kosten;
        }
        return null;
    }


    private static boolean frei(Block block) {
        return block.isPassable() && block.getType() != Material.LAVA;
    }


    // ==================================================================
    // Aufladen
    // ==================================================================

    private void aufladenStarten(Player spieler, Location leitstein, String name) {
        UUID id = spieler.getUniqueId();
        Location start = spieler.getLocation();
        double entfernung = entfernung(start, leitstein);
        double reichweite = reichweite(leitstein.getBlock());
        int[] vergangen = {0};

        BukkitTask aufgabe = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!spieler.isOnline()) {
                abbrechen(id, null);
                return;
            }
            if (spieler.getLocation().getWorld() != start.getWorld() || spieler.getLocation().distanceSquared(start) > 0.25) {
                abbrechen(id, spieler);
                return;
            }

            vergangen[0] += 5;
            if (vergangen[0] >= aufladeTicks) {
                aufladend.remove(id).aufgabe().cancel();
                String problem = pruefen(spieler, leitstein);
                if (problem != null) {
                    fehler(spieler, problem);
                    return;
                }
                teleportieren(spieler, leitstein, name);
                return;
            }

            spieler.sendActionBar(Component.text(name + "  ", NamedTextColor.GOLD)
                    .append(Component.text(balken(vergangen[0], aufladeTicks), NamedTextColor.AQUA))
                    .append(Component.text(String.format(Locale.GERMANY, "  %,.0f / %,.0f Bloecke", entfernung, reichweite), NamedTextColor.GRAY)));
            spieler.getWorld().spawnParticle(Particle.PORTAL, spieler.getLocation().add(0, 1, 0), 12, 0.4, 0.8, 0.4, 0.2);
        }, 0, 5);

        aufladend.put(id, new Aufladen(aufgabe, start));
        spieler.playSound(spieler.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
    }


    private void abbrechen(UUID id, Player spieler) {
        Aufladen laufend = aufladend.remove(id);
        if (laufend != null) {
            laufend.aufgabe().cancel();
        }
        if (spieler != null) {
            fehler(spieler, "Teleport abgebrochen.");
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beiSchaden(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player spieler && aufladend.containsKey(spieler.getUniqueId())) {
            abbrechen(spieler.getUniqueId(), spieler);
        }
    }


    @EventHandler
    public void beimVerlassen(PlayerQuitEvent event) {
        abbrechen(event.getPlayer().getUniqueId(), null);
    }


    private static String balken(int stand, int ziel) {
        int voll = Math.min(10, stand * 10 / Math.max(1, ziel));
        return "■".repeat(voll) + "□".repeat(10 - voll);
    }


    // ==================================================================
    // Teleport
    // ==================================================================

    private void teleportieren(Player spieler, Location leitstein, String name) {
        double entfernung = entfernung(spieler.getLocation(), leitstein);
        kostenAbziehen(spieler, entfernung);

        Location von = spieler.getLocation();
        Location ziel = leitstein.getBlock().getLocation().add(0.5, 1, 0.5);
        ziel.setYaw(von.getYaw());
        ziel.setPitch(von.getPitch());

        von.getWorld().spawnParticle(Particle.REVERSE_PORTAL, von.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
        von.getWorld().playSound(von, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        spieler.teleportAsync(ziel).thenAccept(erfolg -> {
            if (!erfolg) {
                fehler(spieler, "Teleport nicht moeglich.");
                return;
            }
            ziel.getWorld().spawnParticle(Particle.REVERSE_PORTAL, ziel.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
            ziel.getWorld().playSound(ziel, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            spieler.sendActionBar(Component.text("Willkommen bei " + name, NamedTextColor.GOLD));
            if (abklingzeitMillis > 0) {
                abklingenBis.put(spieler.getUniqueId(), System.currentTimeMillis() + abklingzeitMillis);
            }
        });
    }


    // ==================================================================
    // Kosten (Erfahrung und/oder Item) – standardmaessig aus
    // ==================================================================

    private int xpKosten(double entfernung) {
        return xpStufen + (int) Math.ceil(entfernung / 1000.0) * xpStufenPro1000;
    }


    private boolean itemKostenAktiv() {
        return kostenItem != null && kostenItem != Material.AIR && kostenAnzahl > 0;
    }


    private String kostenFehlen(Player spieler, double entfernung) {
        if (spieler.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return null;
        }
        int stufen = xpKosten(entfernung);
        if (stufen > 0 && spieler.getLevel() < stufen) {
            return "Du brauchst " + stufen + " Erfahrungsstufen.";
        }
        if (itemKostenAktiv() && !spieler.getInventory().containsAtLeast(ItemStack.of(kostenItem), kostenAnzahl)) {
            return "Du brauchst " + kostenAnzahl + "x " + kostenItem.getKey().getKey() + ".";
        }
        return null;
    }


    private void kostenAbziehen(Player spieler, double entfernung) {
        if (spieler.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        int stufen = xpKosten(entfernung);
        if (stufen > 0) {
            spieler.setLevel(Math.max(0, spieler.getLevel() - stufen));
        }
        if (itemKostenAktiv()) {
            spieler.getInventory().removeItem(ItemStack.of(kostenItem, kostenAnzahl));
        }
    }


    // ==================================================================
    // Reichweite und Pyramide
    // ==================================================================

    /** Waagerechte Entfernung. Zwischen Dimensionen wird der Nether wie in Minecraft mal 8 gerechnet. */
    static double entfernung(Location von, Location nach) {
        double faktorVon = netherFaktor(von.getWorld());
        double faktorNach = netherFaktor(nach.getWorld());
        double dx = von.getX() * faktorVon - nach.getX() * faktorNach;
        double dz = von.getZ() * faktorVon - nach.getZ() * faktorNach;
        return Math.sqrt(dx * dx + dz * dz);
    }


    private static double netherFaktor(World welt) {
        return (welt != null && welt.getEnvironment() == World.Environment.NETHER) ? 8.0 : 1.0;
    }


    /** Punkte der Pyramide: nur vollstaendige Ebenen von oben nach unten (wie beim Leuchtfeuer). */
    int pyramidenPunkte(Block leitstein, int[] ebenenAusgabe) {
        int punkte = 0;
        int ebenen = 0;

        for (int ebene = 1; ebene <= 4; ebene++) {
            int y = leitstein.getY() - ebene;
            if (y < leitstein.getWorld().getMinHeight()) {
                break;
            }

            int ebenenPunkte = 0;
            boolean vollstaendig = true;
            for (int dx = -ebene; dx <= ebene && vollstaendig; dx++) {
                for (int dz = -ebene; dz <= ebene; dz++) {
                    Integer wert = punkteProBlock.get(leitstein.getWorld().getBlockAt(leitstein.getX() + dx, y, leitstein.getZ() + dz).getType());
                    if (wert == null) {
                        vollstaendig = false;
                        break;
                    }
                    ebenenPunkte += wert;
                }
            }
            if (!vollstaendig) {
                break;
            }
            punkte += ebenenPunkte;
            ebenen++;
        }

        if (ebenenAusgabe != null) {
            ebenenAusgabe[0] = ebenen;
        }
        return punkte;
    }


    double reichweite(Block leitstein) {
        double reichweite = grundReichweite + pyramidenPunkte(leitstein, null) * reichweiteProPunkt;
        return hoechstReichweite > 0 ? Math.min(reichweite, hoechstReichweite) : reichweite;
    }


    private void zeigeInfo(Player spieler, Block leitstein) {
        int[] ebenen = {0};
        int punkte = pyramidenPunkte(leitstein, ebenen);
        double reichweite = grundReichweite + punkte * reichweiteProPunkt;
        if (hoechstReichweite > 0) {
            reichweite = Math.min(reichweite, hoechstReichweite);
        }
        spieler.sendActionBar(Component.text(String.format(Locale.GERMANY,
                "Wegstein: Reichweite %,.0f Bloecke  (Pyramide: %d Ebenen, %d Punkte)", reichweite, ebenen[0], punkte),
                NamedTextColor.GOLD));
    }


    // ==================================================================
    // Kleinkram
    // ==================================================================

    private static String name(ItemStack kompass) {
        Component eigener = kompass.getData(DataComponentTypes.CUSTOM_NAME);
        return eigener == null ? "Wegstein" : PlainTextComponentSerializer.plainText().serialize(eigener);
    }


    private static void fehler(Player spieler, String text) {
        spieler.sendActionBar(Component.text(text, NamedTextColor.RED));
    }
}

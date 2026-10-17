package vanillaplustweaks.datapacks.garten;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import vanillaplustweaks.datapacks.koepfe.YamlKarten;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.Container;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Ersetzt das Datapack "Gardener's Dream" (von Gurkis).
 *
 * Pflanzen in Toepfe setzen – als Deko, sie wachsen nicht:
 *   - Mit der Pflanze in der Hand oben auf den Topf rechtsklicken -> Pflanze steht im Topf.
 *     Toepfe: Blumentopf, Spielerkoepfe (z. B. die Deko-Toepfe aus der Steinsaege), verzierter Topf,
 *     Kessel, Komposter, Fass (offen), Ackerland, Seerose, halbe Toepfe.
 *     Toepfe, die selbst auf Rechtsklick reagieren (verzierter Topf, Komposter, Fass, Ackerland),
 *     brauchen Schleichen + Rechtsklick – sonst wuerde z. B. der Komposter die Pflanze kompostieren.
 *     Pflanzen, die in einen normalen Blumentopf passen (Setzlinge, kleine Blumen ...), kommen
 *     wie in Minecraft ganz normal in den Blumentopf.
 *   - Tropfblatt / Mangroven-Keimling in der Nebenhand: haengend.
 *   - Stock in der Hand + Rechtsklick auf den Topf: Pflanze dreht sich (15 Grad, Nebenhand 7,5 Grad).
 *   - Wie im Datapack geht es auch durch Schleichen auf dem Topf (Einstellung "control").
 *   - Leere Hand + Rechtsklick: Pflanze wie beim normalen Blumentopf herausnehmen
 *     (beim Fass und vollen Komposter mit Schleichen, weil Minecraft dort selbst reagiert).
 *   - Topf abbauen: die Pflanze faellt sofort als Item heraus.
 *   - Hohe Blumen (Rosenstrauch, Flieder ...) schleichend platzieren: nur die Bluete, ein Block hoch.
 *   - Halbe Toepfe (siehe HalbToepfe).
 *
 * Unterschied zum Datapack (Rechenzeit):
 *   Das Datapack hat fuer jeden Spieler JEDEN Tick sechs Bedingungen geprueft (Fortschritte mit
 *   "tick"-Ausloeser) und alle 2 Sekunden alle Entities der Welt nach Pflanzen durchsucht.
 *   Hier wird nur geprueft, wenn ein Spieler schleicht, und nur die bekannten Pflanzen-Anzeigen
 *   werden alle paar Sekunden kontrolliert.
 */
public final class GaertnersTraum extends Modul implements Listener {

    private static final Set<Material> HOHE_BLUMEN = EnumSet.of(
            Material.ROSE_BUSH, Material.LILAC, Material.PEONY, Material.SUNFLOWER,
            Material.TALL_GRASS, Material.LARGE_FERN, Material.PITCHER_PLANT, Material.MANGROVE_PROPAGULE);

    private GartenDaten daten;
    private Gaertner gaertner;
    private HalbToepfe halbToepfe;
    private NamespacedKey gepflanztSchluessel;
    private boolean halbTopfBeimSchleichen;

    /** Bekannte Anzeigen (Pflanzen und halbe Toepfe), die regelmaessig geprueft werden. */
    private final Map<UUID, BlockDisplay> beobachtet = new HashMap<>();

    /** Pro schleichendem Spieler: welche Aktionen in diesem Schleich-Vorgang noch moeglich sind. */
    private final Map<UUID, Set<Aktion>> bereit = new HashMap<>();

    private enum Aktion { PFLANZEN, NEBENHAND, DREHEN, DREHEN_NEBENHAND }

    /** Toepfe, die selbst auf Rechtsklick reagieren: hier nur mit Schleichen + Rechtsklick pflanzen. */
    private static final Set<Material> EIGENER_RECHTSKLICK = EnumSet.of(
            Material.DECORATED_POT, Material.COMPOSTER, Material.BARREL, Material.FARMLAND);

    /** Toepfe, die man von jeder Seite anklicken darf (klein, z. B. an der Wand haengende Koepfe). */
    private static final Set<Material> JEDE_SEITE = EnumSet.of(
            Material.FLOWER_POT, Material.PLAYER_HEAD, Material.PLAYER_WALL_HEAD);

    private boolean perRechtsklick;
    private boolean perSchleichen;

    /** Pro Spieler der Tick der letzten Rechtsklick-Aktion (damit die Nebenhand nicht doppelt ausloest). */
    private final Map<UUID, Integer> letzterKlick = new HashMap<>();


    public GaertnersTraum(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "gardeners-dream";
    }


    @Override
    public void start() {
        Function<String, Set<String>> itemTag = name -> tagInhalt(Tag.REGISTRY_ITEMS, name);
        Function<String, Set<String>> blockTag = name -> tagInhalt(Tag.REGISTRY_BLOCKS, name);

        // Die Pflanzen-Daten stammen aus Gardener's Dream (All Rights Reserved) und liegen deshalb nicht im
        // oeffentlichen Quellcode. Ohne sie bleibt das Modul aus (siehe README: konverter/gaertner_konvertieren.py).
        org.bukkit.configuration.file.YamlConfiguration gartenDatei = datenDatei("gaertner.yml");
        if (!gartenDatei.contains("pflanzen") || !gartenDatei.contains("listen")) {
            warnung("daten/gaertner.yml fehlt – Gardener's Dream bleibt aus. Datei mit dem Konverter erzeugen.");
            return;
        }
        daten = new GartenDaten(YamlKarten.alsKarte(gartenDatei), itemTag, blockTag);
        gaertner = new Gaertner(daten, itemTag.apply("minecraft:leaves"));
        gepflanztSchluessel = new NamespacedKey(plugin, "gepflanzt");
        PflanzenItem.alterSchluessel = alterSchluessel("gepflanzt");
        halbToepfe = new HalbToepfe(daten.erden, this::beobachten);
        halbTopfBeimSchleichen = schalter("half-pot-sneak-placement", false);

        String steuerung = text("control", "right-click");
        perRechtsklick = !steuerung.equalsIgnoreCase("sneak");
        perSchleichen = steuerung.equalsIgnoreCase("sneak") || steuerung.equalsIgnoreCase("both");

        // Anzeigen in schon geladenen Chunks einsammeln
        for (World welt : Bukkit.getWorlds()) {
            for (BlockDisplay display : welt.getEntitiesByClass(BlockDisplay.class)) {
                beobachtenFallsGarten(display);
            }
        }

        registriereListener(this);

        long intervall = Math.max(1, Math.round(kommazahl("check-interval-seconds", 2.0) * 20));
        wiederholeAlle(intervall, this::toepfePruefen);

        log(daten.topfPflanzen.size() + " Pflanzen fuer Toepfe, " + beobachtet.size() + " vorhandene Anzeigen.");
    }


    private static Set<String> tagInhalt(String register, String name) {
        Set<String> ergebnis = new HashSet<>();
        NamespacedKey schluessel = NamespacedKey.fromString(name);
        Tag<Material> tag = schluessel == null ? null : Bukkit.getTag(register, schluessel, Material.class);
        if (tag != null) {
            for (Material material : tag.getValues()) {
                ergebnis.add(material.getKey().toString());
            }
        }
        return ergebnis;
    }


    // ==================================================================
    // Anzeigen beobachten
    // ==================================================================

    private void beobachten(BlockDisplay display) {
        beobachtet.put(display.getUniqueId(), display);
    }


    private void beobachtenFallsGarten(Entity entity) {
        if (entity instanceof BlockDisplay display) {
            Set<String> markierungen = display.getScoreboardTags();
            if (markierungen.contains("potplant") || markierungen.contains(HalbToepfe.TOPF)) {
                beobachten(display);
            }
        }
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void beimChunkLaden(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            beobachtenFallsGarten(entity);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void beimChunkEntladen(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            beobachtet.remove(entity.getUniqueId());
        }
    }


    /** Alle paar Sekunden: Steht der Topf unter jeder Pflanze noch? (pot_broken) */
    private void toepfePruefen() {
        for (BlockDisplay display : new ArrayList<>(beobachtet.values())) {
            if (!display.isValid()) {
                beobachtet.remove(display.getUniqueId());
                continue;
            }

            if (display.getScoreboardTags().contains(HalbToepfe.TOPF)) {
                if (display.getLocation().getBlock().getType() != Material.MOSSY_COBBLESTONE_SLAB) {
                    beobachtet.remove(display.getUniqueId());
                    halbToepfe.abbauen(display, null);
                }
                continue;
            }

            BukkitGartenWelt welt = new BukkitGartenWelt(display, gepflanztSchluessel);
            if (gaertner.topfFehlt(welt, welt.einzige())) {
                beobachtet.remove(display.getUniqueId());
                gaertner.abbauen(welt, welt.einzige());
            }
        }
    }


    // ==================================================================
    // Schleichen: einpflanzen und drehen
    // ==================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimSchleichen(PlayerToggleSneakEvent event) {
        if (!perSchleichen) {
            return;
        }
        Player spieler = event.getPlayer();
        if (!event.isSneaking()) {
            bereit.remove(spieler.getUniqueId());
            return;
        }

        bereit.put(spieler.getUniqueId(), EnumSet.allOf(Aktion.class));
        // Das Event kommt vor dem Wechsel in den Schleich-Zustand -> einen Tick spaeter pruefen
        spaeter(1, () -> pruefen(spieler));
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimBewegen(PlayerMoveEvent event) {
        Set<Aktion> offen = bereit.get(event.getPlayer().getUniqueId());
        if (offen == null || offen.isEmpty()) {
            return;
        }
        // Nur bei Wechsel des Blocks oder der Hoehe (z. B. auf einen Topf treten)
        Location von = event.getFrom();
        Location nach = event.getTo();
        if (von.getBlockX() != nach.getBlockX() || von.getBlockZ() != nach.getBlockZ() || von.getY() != nach.getY()) {
            pruefen(event.getPlayer());
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimItemWechsel(PlayerItemHeldEvent event) {
        if (bereit.containsKey(event.getPlayer().getUniqueId())) {
            spaeter(1, () -> pruefen(event.getPlayer()));
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimHandTausch(PlayerSwapHandItemsEvent event) {
        if (bereit.containsKey(event.getPlayer().getUniqueId())) {
            spaeter(1, () -> pruefen(event.getPlayer()));
        }
    }


    @EventHandler
    public void beimVerlassen(PlayerQuitEvent event) {
        bereit.remove(event.getPlayer().getUniqueId());
        letzterKlick.remove(event.getPlayer().getUniqueId());
    }


    // ==================================================================
    // Rechtsklick auf einen Topf: einpflanzen und drehen
    // ==================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimRechtsklick(PlayerInteractEvent event) {
        if (!perRechtsklick || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null) {
            return;
        }

        Block topf = event.getClickedBlock();
        if (topf == null || !gaertner.istTopf(topf.getType().getKey().toString())) {
            return;
        }
        if (event.getBlockFace() != BlockFace.UP && !JEDE_SEITE.contains(topf.getType())) {
            return; // grosse Toepfe nur von oben
        }

        Player spieler = event.getPlayer();
        boolean nebenhand = event.getHand() == EquipmentSlot.OFF_HAND;
        ItemStack inHand = event.getItem();
        String item = (inHand == null || inHand.isEmpty()) ? null : inHand.getType().getKey().toString();

        // Leere Hand: Pflanze herausnehmen. Nur beim Fass und beim vollen Komposter macht Minecraft
        // selbst etwas mit leerer Hand (oeffnen / Knochenmehl) – dort nur mit Schleichen.
        if (item == null) {
            if (!nebenhand && (spieler.isSneaking() || !leereHandGehoertMinecraft(topf)) && herausnehmen(spieler, topf)) {
                event.setCancelled(true);
                spieler.swingHand(EquipmentSlot.HAND);
            }
            return;
        }

        if (EIGENER_RECHTSKLICK.contains(topf.getType()) && !spieler.isSneaking()) {
            return; // normaler Rechtsklick mit Item gehoert Minecraft (Komposter fuellen, Item in den Topf ...)
        }

        // Normaler Blumentopf + Pflanze, die Minecraft selbst eintopfen kann -> Minecraft macht das
        if (topf.getType() == Material.FLOWER_POT && Material.getMaterial("POTTED_" + inHand.getType().name()) != null) {
            return;
        }

        boolean drehen = item.equals("minecraft:stick");
        boolean pflanzen = nebenhand ? gaertner.istNebenhandPflanze(item) : gaertner.istTopfPflanze(item);
        if (!drehen && !pflanzen) {
            return;
        }

        // Nebenhand nicht ausloesen, wenn die Haupthand in diesem Tick schon etwas getan hat
        int jetzt = Bukkit.getCurrentTick();
        if (nebenhand && Integer.valueOf(jetzt).equals(letzterKlick.get(spieler.getUniqueId()))) {
            event.setCancelled(true);
            return;
        }
        letzterKlick.put(spieler.getUniqueId(), jetzt);

        // Minecraft soll weder den Block benutzen noch das Item platzieren
        event.setCancelled(true);

        letzteWelt = topf.getWorld();

        // Die Logik arbeitet mit "Spieler steht auf dem Topf" – also so tun, als stuende er oben drauf
        Location fuesse = new Location(topf.getWorld(), topf.getX() + 0.5, topf.getBoundingBox().getMaxY(), topf.getZ() + 0.5);
        Spieler gartenSpieler = new Spieler(spieler, new Vek(fuesse.getX(), fuesse.getY(), fuesse.getZ()));
        BukkitGartenWelt welt = new BukkitGartenWelt(fuesse, gepflanztSchluessel, this::beobachten);

        if (drehen) {
            gaertner.drehen(welt, gartenSpieler, nebenhand ? 7.5f : 15f, effekte);
        } else if (nebenhand) {
            welt.setzePflanzItem(item);
            gaertner.einpflanzenNebenhand(welt, gartenSpieler, effekte);
        } else {
            welt.setzePflanzItem(item);
            gaertner.einpflanzen(welt, gartenSpieler, effekte);
        }
        spieler.swingHand(event.getHand());
    }


    private void pruefen(Player spieler) {
        Set<Aktion> offen = bereit.get(spieler.getUniqueId());
        if (offen == null || !spieler.isOnline() || !spieler.isSneaking() || !stehtAufTopf(spieler)) {
            return;
        }

        Spieler gartenSpieler = new Spieler(spieler);
        String haupt = gartenSpieler.haupthand();
        String neben = gartenSpieler.nebenhand();

        if (offen.contains(Aktion.PFLANZEN) && gaertner.istTopfPflanze(haupt)) {
            offen.remove(Aktion.PFLANZEN);
            gaertner.einpflanzen(welt(spieler, haupt), gartenSpieler, effekte);
        }
        if (offen.contains(Aktion.NEBENHAND) && gaertner.istNebenhandPflanze(neben)) {
            offen.remove(Aktion.NEBENHAND);
            gaertner.einpflanzenNebenhand(welt(spieler, neben), gartenSpieler, effekte);
        }
        if (offen.contains(Aktion.DREHEN) && "minecraft:stick".equals(haupt)) {
            offen.remove(Aktion.DREHEN);
            gaertner.drehen(welt(spieler, null), gartenSpieler, 15f, effekte);
        }
        if (offen.contains(Aktion.DREHEN_NEBENHAND) && "minecraft:stick".equals(neben)) {
            offen.remove(Aktion.DREHEN_NEBENHAND);
            gaertner.drehen(welt(spieler, null), gartenSpieler, 7.5f, effekte);
        }
    }


    /**
     * Steht der Spieler auf (oder in) einem Topf? Entspricht im Datapack "stepping_on" bzw. bei
     * kleinen Toepfen "location". Ob dort wirklich gepflanzt werden kann, prueft danach die Logik selbst.
     */
    private boolean stehtAufTopf(Player spieler) {
        Location fuesse = spieler.getLocation();
        Block imBlock = fuesse.getBlock();
        Block darunter = fuesse.clone().subtract(0, 0.2, 0).getBlock();
        return gaertner.istTopf(imBlock.getType().getKey().toString())
                || gaertner.istTopf(darunter.getType().getKey().toString());
    }


    private BukkitGartenWelt welt(Player spieler, String pflanzItem) {
        BukkitGartenWelt welt = new BukkitGartenWelt(spieler.getLocation(), gepflanztSchluessel, this::beobachten);
        welt.setzePflanzItem(pflanzItem);
        return welt;
    }


    /** Der Bukkit-Spieler aus Sicht der Garten-Logik. */
    private static final class Spieler implements Gaertner.Gartenspieler {

        private final Player spieler;

        /** Bei Rechtsklick: oben auf dem angeklickten Topf. Sonst null = echte Position des Spielers. */
        private final Vek fuesse;

        Spieler(Player spieler) {
            this(spieler, null);
        }

        Spieler(Player spieler, Vek fuesse) {
            this.spieler = spieler;
            this.fuesse = fuesse;
        }

        @Override
        public Vek fuesse() {
            if (fuesse != null) {
                return fuesse;
            }
            Location ort = spieler.getLocation();
            return new Vek(ort.getX(), ort.getY(), ort.getZ());
        }

        @Override
        public String haupthand() {
            return id(spieler.getInventory().getItemInMainHand());
        }

        @Override
        public String nebenhand() {
            return id(spieler.getInventory().getItemInOffHand());
        }

        @Override
        public boolean kreativ() {
            return spieler.getGameMode() == GameMode.CREATIVE;
        }

        @Override
        public void verbrauche(boolean nebenhand) {
            ItemStack item = nebenhand ? spieler.getInventory().getItemInOffHand() : spieler.getInventory().getItemInMainHand();
            if (!item.isEmpty()) {
                item.subtract();
            }
        }

        @Override
        public void nichtDrehbar() {
            spieler.sendMessage(Component.text("Sorry, this plant can't be rotated."));
        }

        private static String id(ItemStack item) {
            return item.isEmpty() ? null : item.getType().getKey().toString();
        }
    }


    private final Gaertner.Effekte effekte = new Gaertner.Effekte() {
        @Override
        public void einpflanzKlang(Gaertner.Gartenspieler spieler, boolean grosserTopf) {
            Location ort = ort(spieler);
            ort.getWorld().playSound(ort, Sound.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1f, grosserTopf ? 0.8f : 1.5f);
        }

        @Override
        public void erdPartikel(Vek ecke) {
            // Wird nur waehrend einer Spieler-Aktion aufgerufen -> Welt des letzten Spielers
            if (letzteWelt != null) {
                Location ort = new Location(letzteWelt, ecke.x() + 0.5, ecke.y() + 0.3, ecke.z() + 0.5);
                letzteWelt.spawnParticle(Particle.BLOCK, ort, 3, 0.05, 0, 0.05, 0, Material.ROOTED_DIRT.createBlockData());
            }
        }

        @Override
        public void drehKlang(Gaertner.Gartenspieler spieler) {
            Location ort = ort(spieler);
            ort.getWorld().playSound(ort, Sound.BLOCK_AZALEA_LEAVES_PLACE, SoundCategory.BLOCKS, 1f, 1.5f);
        }

        private Location ort(Gaertner.Gartenspieler spieler) {
            Player bukkitSpieler = ((Spieler) spieler).spieler;
            letzteWelt = bukkitSpieler.getWorld();
            return bukkitSpieler.getLocation();
        }
    };

    private World letzteWelt;


    /** Reagiert Minecraft selbst auf einen Rechtsklick mit leerer Hand? (Fass oeffnen, vollen Komposter leeren) */
    private static boolean leereHandGehoertMinecraft(Block topf) {
        if (topf.getType() == Material.BARREL) {
            return true;
        }
        return topf.getBlockData() instanceof Levelled komposter
                && topf.getType() == Material.COMPOSTER
                && komposter.getLevel() >= komposter.getMaximumLevel();
    }


    /**
     * Leere Hand + Rechtsklick: Pflanze(n) wie bei einem normalen Blumentopf herausnehmen.
     * Das Item kommt in die Hand bzw. ins Inventar; passt es nicht, faellt es auf den Boden.
     *
     * @return true, wenn etwas herausgenommen wurde
     */
    private boolean herausnehmen(Player spieler, Block topf) {
        Location mitte = topf.getLocation().add(0.5, 0.5, 0.5);
        BukkitGartenWelt welt = new BukkitGartenWelt(mitte, gepflanztSchluessel, this::beobachten);

        List<GartenWelt.Pflanze> pflanzen = gaertner.pflanzenImTopf(welt, topf.getX(), topf.getY(), topf.getZ());
        if (pflanzen.isEmpty()) {
            return false;
        }

        for (GartenWelt.Pflanze pflanze : pflanzen) {
            ItemStack item = welt.herausnehmen(pflanze);
            if (item == null || spieler.getGameMode() == GameMode.CREATIVE) {
                continue; // im Kreativmodus wurde beim Einpflanzen auch nichts verbraucht
            }
            for (ItemStack rest : spieler.getInventory().addItem(item).values()) {
                spieler.getWorld().dropItem(spieler.getLocation(), rest);
            }
        }

        topf.getWorld().playSound(mitte, Sound.BLOCK_AZALEA_LEAVES_BREAK, SoundCategory.BLOCKS, 1f, 1.2f);
        return true;
    }


    // ==================================================================
    // Topf abgebaut: Pflanze sofort herausfallen lassen
    // ==================================================================

    /**
     * Beim Abbauen durch einen Spieler sofort pruefen (einen Tick spaeter, wenn der Block weg ist).
     * Andere Wege (Kolben, Explosion ...) faengt die regelmaessige Pruefung ab.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimTopfAbbauen(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!gaertner.istTopf(block.getType().getKey().toString())) {
            return;
        }

        Location mitte = block.getLocation().add(0.5, 0.5, 0.5);
        spaeter(1, () -> {
            for (Entity entity : mitte.getWorld().getNearbyEntities(mitte, 1.5, 1.5, 1.5)) {
                if (entity instanceof BlockDisplay display && beobachtet.containsKey(display.getUniqueId())
                        && display.getScoreboardTags().contains("potplant")) {
                    BukkitGartenWelt welt = new BukkitGartenWelt(display, gepflanztSchluessel);
                    if (gaertner.topfFehlt(welt, welt.einzige())) {
                        beobachtet.remove(display.getUniqueId());
                        gaertner.abbauen(welt, welt.einzige());
                    }
                }
            }
        });
    }


    // ==================================================================
    // Platzieren: halbe Toepfe, hohe Blumen kurz, Fass oeffnen
    // ==================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimPlatzieren(BlockPlaceEvent event) {
        Player spieler = event.getPlayer();
        Block block = event.getBlockPlaced();
        Material art = block.getType();
        boolean schleicht = spieler.isSneaking();
        letzteWelt = block.getWorld();

        // Halbe Toepfe aus Kessel / Komposter
        if (art == Material.CAULDRON || art == Material.COMPOSTER) {
            int besonders = HalbToepfe.halbTopfItem(event.getItemInHand());

            if (event.getHand() == EquipmentSlot.HAND) {
                if (schleicht && halbTopfBeimSchleichen) {
                    ItemStack nebenhand = spieler.getInventory().getItemInOffHand();
                    halbToepfe.bauen(block, false, nebenhand.isEmpty() ? null : nebenhand.getType().getKey().toString());
                } else if (!schleicht && besonders > 0) {
                    halbToepfe.bauen(block, besonders == 2, null);
                }
            } else if (event.getHand() == EquipmentSlot.OFF_HAND && schleicht && halbTopfBeimSchleichen) {
                halbToepfe.bauen(block, true, null);
            }
            return;
        }

        // Hohe Blumen schleichend platzieren -> nur die obere Haelfte (die Bluete) bleibt
        if (schleicht && HOHE_BLUMEN.contains(art)) {
            spaeter(1, () -> hoheBlumeKuerzen(block, art));
            return;
        }

        // Fass: schleichend, senkrecht nach unten schauend, auf einem Fass stehend -> Fass darunter oeffnen
        if (schleicht && art == Material.BARREL && event.getHand() == EquipmentSlot.HAND && spieler.getLocation().getPitch() >= 90f) {
            Block unten = spieler.getLocation().getBlock().getRelative(0, -1, 0);
            if (unten.getType() == Material.BARREL) {
                fassOeffnen(unten);
            }
        }
    }


    private static void hoheBlumeKuerzen(Block block, Material art) {
        if (block.getType() != art) {
            return;
        }
        String neu = switch (art) {
            case PITCHER_PLANT -> "minecraft:pitcher_crop[half=upper,age=3]";
            case MANGROVE_PROPAGULE -> "minecraft:mangrove_propagule[hanging=true,age=4]";
            default -> art.getKey() + "[half=upper]";
        };

        if (art != Material.MANGROVE_PROPAGULE) {
            Block oben = block.getRelative(0, 1, 0);
            if (oben.getType() == art) {
                oben.setType(Material.AIR, false);
            }
        }
        block.setBlockData(Bukkit.createBlockData(neu), false);
    }


    /** Fass nach oben geoeffnet hinstellen; der Inhalt faellt heraus (wie "setblock ... destroy"). */
    private static void fassOeffnen(Block fass) {
        if (fass.getState() instanceof Container behaelter) {
            Location mitte = fass.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack inhalt : behaelter.getInventory().getContents()) {
                if (inhalt != null && !inhalt.isEmpty()) {
                    fass.getWorld().dropItemNaturally(mitte, inhalt);
                }
            }
            behaelter.getInventory().clear();
        }
        fass.setBlockData(Bukkit.createBlockData("minecraft:barrel[facing=up,open=true]"));
    }


    // ==================================================================
    // Halbe Toepfe: Schlagen = abbauen, Rechtsklick = Erde wechseln
    // ==================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimSchlagen(PrePlayerAttackEntityEvent event) {
        if (event.getAttacked() instanceof Interaction flaeche && flaeche.getScoreboardTags().contains(HalbToepfe.MARKIERUNG)) {
            BlockDisplay topf = HalbToepfe.topfZu(flaeche);
            if (topf != null) {
                beobachtet.remove(topf.getUniqueId());
                halbToepfe.abbauen(topf, event.getPlayer());
            }
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimAnklicken(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getRightClicked() instanceof Interaction flaeche && flaeche.getScoreboardTags().contains(HalbToepfe.MARKIERUNG)) {
            halbToepfe.erdeWechseln(flaeche, event.getPlayer());
        }
    }
}

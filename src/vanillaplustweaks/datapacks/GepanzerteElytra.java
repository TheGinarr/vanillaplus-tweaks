package vanillaplustweaks.datapacks;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ersetzt das Datapack "Armored Elytra" (Vanilla Tweaks).
 *
 * Zusammenbauen: Elytra und Brustplatte auf einen Amboss werfen -> gepanzerte Elytra.
 *   Sie schuetzt wie die Brustplatte und fliegt wie eine Elytra.
 * Zerlegen:
 *   - Gepanzerte Elytra auf einen Schleifstein werfen,
 *   - sie geht kaputt (Haltbarkeit 1) -> beide Teile fallen vor die Fuesse,
 *   - sie faellt in Feuer oder Lava -> beide Teile werden getrennt, damit z. B. Netherit nicht mitverbrennt.
 *
 * WICHTIG – kompatibel mit vorhandenen Items:
 *   Das Plugin baut die gepanzerte Elytra exakt so auf wie das Datapack (Brustplatte und Elytra
 *   stecken als "Bundle-Inhalt" in der Elytra, gleiche Ruestungswerte, gleiche Kennung).
 *   Gepanzerte Elytren, die noch mit dem Datapack gebaut wurden, funktionieren also weiter.
 *
 * Unterschied zum Datapack: Das Datapack hat JEDEN Tick alle brennenden Items und jede Sekunde
 * alle Items der Welt abgesucht. Hier werden nur Elytra-Items beobachtet, und Feuer meldet Paper selbst.
 */
public final class GepanzerteElytra extends Modul implements Listener {

    /** Dieselbe Kennung wie im Datapack (custom_data), damit das Datapack die Items auch wiedererkennt. */
    private static final String DATAPACK_KENNUNG = "[minecraft:custom_data={armored_elytra:{armored:true}}]";

    /** Wie oft geworfene Elytren auf Amboss/Schleifstein geprueft werden (10 Ticks = 0,5 s). */
    private static final long PRUEF_INTERVALL_TICKS = 10;

    /** Ruestungswerte pro Brustplatte – gleiche Werte wie das Datapack (= Werte der normalen Brustplatte). */
    private record Ruestung(double ruestung, double haerte, double rueckstossschutz, float modellNummer) {
    }

    private static final Map<Material, Ruestung> RUESTUNG = new HashMap<>();

    static {
        RUESTUNG.put(Material.LEATHER_CHESTPLATE, new Ruestung(3, 0, 0, 13522551));
        RUESTUNG.put(Material.CHAINMAIL_CHESTPLATE, new Ruestung(5, 0, 0, 13522552));
        RUESTUNG.put(Material.GOLDEN_CHESTPLATE, new Ruestung(5, 0, 0, 13522553));
        RUESTUNG.put(Material.IRON_CHESTPLATE, new Ruestung(6, 0, 0, 13522554));
        RUESTUNG.put(Material.DIAMOND_CHESTPLATE, new Ruestung(8, 2, 0, 13522555));
        RUESTUNG.put(Material.NETHERITE_CHESTPLATE, new Ruestung(8, 3, 0.1, 13522556));
    }

    /** Geworfene Elytren, die gerade in der Welt liegen. */
    private final Map<UUID, Item> beobachteteElytren = new HashMap<>();


    public GepanzerteElytra(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "armored-elytra";
    }


    @Override
    public void start() {
        registriereListener(this);

        // Chunks, die schon vor dem Plugin geladen wurden (z. B. dauerhaft geladene Chunks),
        // loesen kein EntitiesLoadEvent mehr fuer uns aus -> einmal alle Welten durchsehen.
        for (World welt : Bukkit.getWorlds()) {
            for (Item item : welt.getEntitiesByClass(Item.class)) {
                if (item.getItemStack().getType() == Material.ELYTRA) {
                    beobachteteElytren.put(item.getUniqueId(), item);
                }
            }
        }

        wiederholeAlle(PRUEF_INTERVALL_TICKS, this::geworfeneElytrenPruefen);
    }


    // ==================================================================
    // 1. Geworfene Elytren beobachten (Amboss / Schleifstein)
    // ==================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimItemErscheinen(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (item.getItemStack().getType() == Material.ELYTRA) {
            beobachteteElytren.put(item.getUniqueId(), item);
        }
    }


    /**
     * Elytren, die beim Laden eines Chunks schon auf dem Boden liegen (z. B. nach einem Neustart),
     * loesen kein ItemSpawnEvent aus. Das Datapack hat sie trotzdem gefunden – also hier auch.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void beimChunkLaden(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item item && item.getItemStack().getType() == Material.ELYTRA) {
                beobachteteElytren.put(item.getUniqueId(), item);
            }
        }
    }


    private void geworfeneElytrenPruefen() {
        // Mit einer Kopie arbeiten: Beim Zerlegen entstehen neue Elytra-Items, die ueber
        // ItemSpawnEvent sofort in die Liste kommen. Die Original-Liste darf sich deshalb
        // waehrend der Schleife nicht aendern (sonst ConcurrentModificationException).
        List<Item> zuPruefen = new ArrayList<>(beobachteteElytren.values());

        for (Item elytraItem : zuPruefen) {

            // Aufgehoben, verschwunden oder verbrannt -> nicht mehr beobachten
            if (!elytraItem.isValid()) {
                beobachteteElytren.remove(elytraItem.getUniqueId());
                continue;
            }

            Block darunter = elytraItem.getLocation().subtract(0, 0.01, 0).getBlock();
            ItemStack stapel = elytraItem.getItemStack();

            if (istGepanzert(stapel)) {
                if (darunter.getType() == Material.GRINDSTONE) {
                    beobachteteElytren.remove(elytraItem.getUniqueId());
                    amSchleifsteinZerlegen(elytraItem);
                }
            } else if (Tag.ANVIL.isTagged(darunter.getType()) && hatGenugHaltbarkeit(stapel)) {
                Item brustplatte = findeBrustplatteImSelbenBlock(elytraItem);
                if (brustplatte != null) {
                    zusammenbauen(elytraItem, brustplatte);
                }
            }
        }
    }


    /** Wie im Datapack: Eine fast kaputte Elytra (Haltbarkeit 1) kann nicht gepanzert werden. */
    private static boolean hatGenugHaltbarkeit(ItemStack elytra) {
        Integer maxSchaden = elytra.getData(DataComponentTypes.MAX_DAMAGE);
        Integer schaden = elytra.getData(DataComponentTypes.DAMAGE);

        if (maxSchaden == null) {
            return true;
        }
        int bisherigerSchaden = (schaden == null) ? 0 : schaden;
        return maxSchaden - bisherigerSchaden >= 2;
    }


    private static Item findeBrustplatteImSelbenBlock(Item elytraItem) {
        Block elytraBlock = elytraItem.getLocation().getBlock();

        for (Entity nachbar : elytraItem.getNearbyEntities(1, 1, 1)) {
            if (!(nachbar instanceof Item item) || !item.isValid()) {
                continue;
            }
            if (!RUESTUNG.containsKey(item.getItemStack().getType())) {
                continue;
            }
            if (item.getLocation().getBlock().equals(elytraBlock)) {
                return item;
            }
        }
        return null;
    }


    // ==================================================================
    // 2. Zusammenbauen
    // ==================================================================

    private void zusammenbauen(Item elytraItem, Item brustplattenItem) {
        ItemStack elytra = elytraItem.getItemStack();
        ItemStack brustplatte = brustplattenItem.getItemStack();

        ItemStack gepanzert = baueGepanzerteElytra(elytra, brustplatte);

        elytraItem.setItemStack(gepanzert);
        elytraItem.setPickupDelay(0);
        brustplattenItem.remove();

        Location ort = elytraItem.getLocation();
        ort.getWorld().playSound(ort, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
    }


    /** Baut die gepanzerte Elytra genau wie das Datapack auf. */
    static ItemStack baueGepanzerteElytra(ItemStack elytra, ItemStack brustplatte) {
        ItemStack ergebnis = elytra.clone();
        Ruestung werte = RUESTUNG.get(brustplatte.getType());

        // Beide Original-Teile werden im Bundle-Inhalt aufbewahrt: [0] = Brustplatte, [1] = Elytra
        ergebnis.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(List.of(brustplatte.clone(), elytra.clone())));

        setzeRuestungswerte(ergebnis, werte);
        ergebnis.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addFloat(werte.modellNummer()).build());
        haengeBeschreibungAn(ergebnis, brustplatte);

        int reparaturkosten = reparaturkosten(elytra) + reparaturkosten(brustplatte);
        ergebnis.setData(DataComponentTypes.REPAIR_COST, reparaturkosten);

        Map<Enchantment, Integer> verzauberungen = ElytraVerzauberungen.zusammenfuehren(verzauberungen(elytra), verzauberungen(brustplatte));
        ergebnis.setData(DataComponentTypes.ENCHANTMENTS, ItemEnchantments.itemEnchantments(verzauberungen));

        setzeDatapackKennung(ergebnis);
        return ergebnis;
    }


    private static void setzeRuestungswerte(ItemStack item, Ruestung werte) {
        ItemAttributeModifiers.Builder attribute = ItemAttributeModifiers.itemAttributes();

        // Vorhandene Werte behalten (das Datapack ersetzt sie ebenfalls nicht)
        ItemAttributeModifiers vorhanden = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (vorhanden != null) {
            for (ItemAttributeModifiers.Entry eintrag : vorhanden.modifiers()) {
                attribute.addModifier(eintrag.attribute(), eintrag.modifier(), eintrag.getGroup(), eintrag.display());
            }
        }

        attribute.addModifier(Attribute.ARMOR, brustModifikator("armor", werte.ruestung()));
        if (werte.haerte() > 0) {
            attribute.addModifier(Attribute.ARMOR_TOUGHNESS, brustModifikator("armor_toughness", werte.haerte()));
        }
        if (werte.rueckstossschutz() > 0) {
            attribute.addModifier(Attribute.KNOCKBACK_RESISTANCE, brustModifikator("knockback_resistance", werte.rueckstossschutz()));
        }

        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, attribute.build());
    }


    /** Gleiche Namen wie im Datapack, z. B. "armored_elytra:armor", gueltig im Brust-Slot. */
    private static AttributeModifier brustModifikator(String name, double wert) {
        return new AttributeModifier(
                new NamespacedKey("armored_elytra", name),
                wert,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.CHEST);
    }


    /** Neue Zeile in der Beschreibung: "+ Diamantharnisch" (bzw. der eigene Name der Brustplatte, kursiv). */
    private static void haengeBeschreibungAn(ItemStack item, ItemStack brustplatte) {
        Component eigenerName = brustplatte.getData(DataComponentTypes.CUSTOM_NAME);

        Component name;
        if (eigenerName != null) {
            name = Component.text("", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, true).append(eigenerName);
        } else {
            name = Component.translatable(brustplatte.translationKey()).color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false);
        }

        Component zeile = Component.text("+ ", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false).append(name);

        ItemLore.Builder beschreibung = ItemLore.lore();
        ItemLore vorhanden = item.getData(DataComponentTypes.LORE);
        if (vorhanden != null) {
            beschreibung.addLines(vorhanden.lines());
        }
        beschreibung.addLine(zeile);

        item.setData(DataComponentTypes.LORE, beschreibung.build());
    }


    /**
     * Setzt zusaetzlich die Kennung des Datapacks. Falls das Datapack spaeter wieder benutzt wird,
     * erkennt es die Items. Fuer das Plugin selbst ist das nicht noetig (es erkennt den Bundle-Inhalt).
     */
    private static void setzeDatapackKennung(ItemStack item) {
        try {
            org.bukkit.Bukkit.getUnsafe().modifyItemStack(item, DATAPACK_KENNUNG);
        } catch (RuntimeException fehler) {
            // Nicht schlimm – das Plugin erkennt gepanzerte Elytren auch ohne diese Kennung
        }
    }


    // ==================================================================
    // 3. Zerlegen
    // ==================================================================

    /** Gepanzert = Elytra, in deren Bundle-Inhalt eine Brustplatte und eine Elytra stecken. */
    static boolean istGepanzert(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA) {
            return false;
        }
        if (!item.hasData(DataComponentTypes.BUNDLE_CONTENTS)) {
            return false;
        }

        BundleContents inhalt = item.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (inhalt == null || inhalt.contents().size() < 2) {
            return false;
        }

        List<ItemStack> teile = inhalt.contents();
        return RUESTUNG.containsKey(teile.get(0).getType()) && teile.get(1).getType() == Material.ELYTRA;
    }


    /** Die beiden Einzelteile: [0] = Elytra, [1] = Brustplatte. */
    static ItemStack[] zerlegen(ItemStack gepanzert) {
        List<ItemStack> teile = gepanzert.getData(DataComponentTypes.BUNDLE_CONTENTS).contents();
        ItemStack brustplatte = teile.get(0).clone();
        ItemStack elytra = teile.get(1).clone();

        ElytraVerzauberungen.Aufteilung<Enchantment> aufteilung = ElytraVerzauberungen.aufteilen(
                verzauberungen(gepanzert),
                verzauberungen(elytra),
                verzauberungen(brustplatte));

        elytra.setData(DataComponentTypes.ENCHANTMENTS, ItemEnchantments.itemEnchantments(aufteilung.elytra()));
        brustplatte.setData(DataComponentTypes.ENCHANTMENTS, ItemEnchantments.itemEnchantments(aufteilung.brustplatte()));

        // Abnutzung und eigener Name gehoeren zur Elytra (wie im Datapack)
        uebernehmeOderEntferne(gepanzert, elytra, DataComponentTypes.DAMAGE);
        uebernehmeOderEntferne(gepanzert, elytra, DataComponentTypes.CUSTOM_NAME);

        return new ItemStack[] {elytra, brustplatte};
    }


    private void amSchleifsteinZerlegen(Item gepanzertesItem) {
        Location ort = gepanzertesItem.getLocation();
        ItemStack[] teile = zerlegen(gepanzertesItem.getItemStack());

        gepanzertesItem.remove();
        ort.getWorld().dropItem(ort, teile[0]);
        ort.getWorld().dropItem(ort, teile[1]);
        ort.getWorld().playSound(ort, Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.0f);
    }


    // ------------------------------------------------------------------
    // Zerlegen bei Feuer und Lava
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimEntzuenden(EntityCombustEvent event) {
        if (event.getEntity() instanceof Item item && item.isValid() && istGepanzert(item.getItemStack())) {
            event.setCancelled(true);
            imFeuerZerlegen(item, Math.round(event.getDuration() * 20));
        }
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beiSchaden(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item) || !item.isValid() || !istGepanzert(item.getItemStack())) {
            return; // isValid: ein bereits zerlegtes Item kann im selben Tick noch ein zweites Event ausloesen
        }

        EntityDamageEvent.DamageCause ursache = event.getCause();
        boolean feuer = ursache == EntityDamageEvent.DamageCause.FIRE
                || ursache == EntityDamageEvent.DamageCause.FIRE_TICK
                || ursache == EntityDamageEvent.DamageCause.LAVA;

        if (feuer) {
            event.setCancelled(true);
            imFeuerZerlegen(item, Math.max(item.getFireTicks(), 20));
        }
    }


    /** Beide Teile fliegen mit derselben Bewegung weiter und brennen wie vorher. */
    private void imFeuerZerlegen(Item gepanzertesItem, int feuerTicks) {
        Location ort = gepanzertesItem.getLocation();
        Vector bewegung = gepanzertesItem.getVelocity();
        int aufhebeVerzoegerung = gepanzertesItem.getPickupDelay();
        UUID besitzer = gepanzertesItem.getOwner();

        ItemStack[] teile = zerlegen(gepanzertesItem.getItemStack());
        gepanzertesItem.remove();

        for (ItemStack teil : teile) {
            ort.getWorld().dropItem(ort, teil, neuesItem -> {
                neuesItem.setVelocity(bewegung);
                neuesItem.setFireTicks(feuerTicks);
                neuesItem.setPickupDelay(aufhebeVerzoegerung);
                neuesItem.setOwner(besitzer);
            });
        }
    }


    // ------------------------------------------------------------------
    // Zerlegen, wenn die getragene gepanzerte Elytra kaputtgeht
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beiAbnutzung(PlayerItemDamageEvent event) {
        if (!istGepanzert(event.getItem())) {
            return;
        }

        Player spieler = event.getPlayer();

        // Der Schaden wird erst nach dem Event abgezogen -> einen Tick spaeter nachsehen
        spaeter(1, () -> pruefeObKaputt(spieler));
    }


    private void pruefeObKaputt(Player spieler) {
        if (!spieler.isOnline()) {
            return;
        }

        ItemStack getragen = spieler.getInventory().getChestplate();
        if (!istGepanzert(getragen) || hatGenugHaltbarkeit(getragen)) {
            return;
        }

        ItemStack[] teile = zerlegen(getragen);

        // Wie im Datapack: die Elytra kommt mit Haltbarkeit 1 zurueck (nicht mehr flugfaehig, aber reparierbar)
        Integer maxSchaden = teile[0].getData(DataComponentTypes.MAX_DAMAGE);
        if (maxSchaden != null) {
            teile[0].setData(DataComponentTypes.DAMAGE, maxSchaden - 1);
        }

        spieler.getInventory().setChestplate(null);

        Location ort = spieler.getLocation();
        for (ItemStack teil : teile) {
            ort.getWorld().dropItem(ort, teil, neuesItem -> neuesItem.setOwner(spieler.getUniqueId()));
        }
    }


    // ------------------------------------------------------------------
    // Kleine Helfer
    // ------------------------------------------------------------------

    private static Map<Enchantment, Integer> verzauberungen(ItemStack item) {
        ItemEnchantments vorhanden = item.getData(DataComponentTypes.ENCHANTMENTS);
        if (vorhanden == null) {
            return Map.of();
        }
        return vorhanden.enchantments();
    }


    private static int reparaturkosten(ItemStack item) {
        Integer kosten = item.getData(DataComponentTypes.REPAIR_COST);
        return (kosten == null) ? 0 : kosten;
    }


    /** Kopiert einen Wert von "quelle" nach "ziel" – oder entfernt ihn am Ziel, wenn die Quelle keinen hat. */
    private static <T> void uebernehmeOderEntferne(ItemStack quelle, ItemStack ziel,
                                                   io.papermc.paper.datacomponent.DataComponentType.Valued<T> typ) {
        T wert = quelle.getData(typ);
        if (wert != null) {
            ziel.setData(typ, wert);
        } else {
            ziel.unsetData(typ);
        }
    }
}

package vanillaplustweaks.orientierung;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Todespunkt: Nach dem Tod bekommt nur der Gestorbene eine Chat-Nachricht mit seinen Koordinaten.
 * Ein Klick auf die Koordinaten kopiert sie in die Zwischenablage.
 */
public final class Todespunkt extends Modul implements Listener {

    public Todespunkt(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "death-point";
    }


    @Override
    public void start() {
        registriereListener(this);
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void beimTod(PlayerDeathEvent event) {
        Player spieler = event.getPlayer();
        Location ort = spieler.getLocation();
        String koordinaten = ort.getBlockX() + " " + ort.getBlockY() + " " + ort.getBlockZ();

        Component klickbar = Component.text("[" + koordinaten + "]", NamedTextColor.YELLOW)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.copyToClipboard(koordinaten))
                .hoverEvent(HoverEvent.showText(Component.text("Klicken zum Kopieren")));

        spieler.sendMessage(Component.text("☠ Du bist gestorben bei ", NamedTextColor.GRAY)
                .append(klickbar)
                .append(Component.text(" (" + dimension(ort.getWorld()) + ")", NamedTextColor.GRAY)));
    }


    private static String dimension(World welt) {
        return switch (welt.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> "Oberwelt";
        };
    }
}

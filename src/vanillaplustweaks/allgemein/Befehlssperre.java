package vanillaplustweaks.allgemein;

import vanillaplustweaks.Modul;
import vanillaplustweaks.NeuLadbar;
import vanillaplustweaks.VanillaPlusTweaks;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Befehlssperre fuer Spieler ohne OP.
 *
 * Erlaubt ist nur, was in der config.yml unter "command-block.commands" auf true steht
 * (z. B. /me, /msg). Alles andere – auch /plugins, /version oder /bukkit:help – wird abgelehnt
 * und taucht in der Tab-Vervollstaendigung gar nicht erst auf.
 *
 * Befehle mit Namensraum (z. B. /minecraft:msg) zaehlen wie der Befehl ohne (msg).
 * OPs und Spieler mit dem Recht "vptweaks.commands.bypass" sind nicht betroffen.
 */
public final class Befehlssperre extends Modul implements Listener, NeuLadbar {

    private static final String UMGEHEN = "vptweaks.commands.bypass";

    private final Set<String> erlaubt = new HashSet<>();
    private String meldung;


    public Befehlssperre(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "command-block";
    }


    /**
     * Anders als die anderen Module standardmaessig AUS: Fehlt der Abschnitt in einer aelteren
     * config.yml, wuerde die leere Liste sonst auch /me und /msg sperren.
     */
    @Override
    public boolean istEingeschaltet() {
        return config().getBoolean(configAbschnitt() + ".enabled", false);
    }


    @Override
    public void start() {
        einlesen();
        registriereListener(this);
    }


    @Override
    public void neuLaden() {
        einlesen();
        // Tab-Vervollstaendigung der Spieler sofort an die neue Liste anpassen
        for (Player spieler : plugin.getServer().getOnlinePlayers()) {
            spieler.updateCommands();
        }
    }


    private void einlesen() {
        erlaubt.clear();
        ConfigurationSection befehle = config().getConfigurationSection(configAbschnitt() + ".commands");
        if (befehle != null) {
            for (String name : befehle.getKeys(false)) {
                if (befehle.getBoolean(name, false)) {
                    erlaubt.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        meldung = text("message", "<red>Unbekannter Befehl.");
        log("Erlaubte Befehle fuer Spieler ohne OP: " + String.join(", ", erlaubt.stream().sorted().toList()));
    }


    // ------------------------------------------------------------------
    // Ausfuehren verhindern
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void beimBefehl(PlayerCommandPreprocessEvent event) {
        Player spieler = event.getPlayer();
        if (darfAlles(spieler)) {
            return;
        }

        if (!istErlaubt(befehlsName(event.getMessage()))) {
            event.setCancelled(true);
            spieler.sendMessage(MiniMessage.miniMessage().deserialize(meldung));
        }
    }


    // ------------------------------------------------------------------
    // Aus der Tab-Vervollstaendigung entfernen
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void beimSendenDerBefehlsliste(PlayerCommandSendEvent event) {
        if (darfAlles(event.getPlayer())) {
            return;
        }
        event.getCommands().removeIf(name -> !istErlaubt(name));
    }


    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    private static boolean darfAlles(Player spieler) {
        return spieler.isOp() || spieler.hasPermission(UMGEHEN);
    }


    /** "minecraft:msg" und "msg" zaehlen gleich. */
    private boolean istErlaubt(String name) {
        String ohneNamensraum = name.substring(name.indexOf(':') + 1);
        return erlaubt.contains(ohneNamensraum.toLowerCase(Locale.ROOT));
    }


    /** Aus "/msg Steve hallo" wird "msg". */
    private static String befehlsName(String nachricht) {
        String ohneSchraegstrich = nachricht.startsWith("/") ? nachricht.substring(1) : nachricht;
        int leerzeichen = ohneSchraegstrich.indexOf(' ');
        return leerzeichen < 0 ? ohneSchraegstrich : ohneSchraegstrich.substring(0, leerzeichen);
    }
}

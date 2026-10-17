package vanillaplustweaks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

/**
 * Platzhalter fuer PlaceholderAPI (z. B. im Tab von TAB):
 *
 *   %vptweaks_cpu%     CPU des Servers, 100 = ein voller Kern
 *   %vptweaks_deaths%  Tode des Spielers (Minecraft-Statistik, zaehlt alle bisherigen)
 *   %vptweaks_nether%  "ab 24.10. um 20:00 Uhr" oder "offen"
 *   %vptweaks_end%     wie oben fuer das End
 *
 * Wird nur geladen, wenn PlaceholderAPI installiert ist.
 */
final class TweaksPlatzhalter extends PlaceholderExpansion {

    private final VanillaPlusTweaks plugin;


    TweaksPlatzhalter(VanillaPlusTweaks plugin) {
        this.plugin = plugin;
    }


    @Override
    public String getIdentifier() {
        return "vptweaks";
    }


    @Override
    public String getAuthor() {
        return "VanillaPlusTweaks";
    }


    @Override
    public String getVersion() {
        return "3.0.0";
    }


    /** Platzhalter bleiben bei "/papi reload" erhalten. */
    @Override
    public boolean persist() {
        return true;
    }


    @Override
    public String onRequest(OfflinePlayer spieler, String platzhalter) {
        switch (platzhalter.toLowerCase()) {
            case "cpu":
                return plugin.cpuProzent();

            case "nether":
                return plugin.netherStatus();

            case "end":
                return plugin.endStatus();

            case "deaths":
                return todeVon(spieler);

            default:
                return null; // unbekannter Platzhalter
        }
    }


    private static String todeVon(OfflinePlayer spieler) {
        if (spieler == null || !spieler.isOnline()) {
            return "0";
        }

        Player online = spieler.getPlayer();
        if (online == null) {
            return "0";
        }
        return String.valueOf(online.getStatistic(Statistic.DEATHS));
    }
}

package vanillaplustweaks;

import vanillaplustweaks.allgemein.CpuMessung;
import vanillaplustweaks.bauen.BloeckeDrehen;
import vanillaplustweaks.bauen.Doppeltueren;
import vanillaplustweaks.bauen.DorfbewohnerLeine;
import vanillaplustweaks.bauen.RahmenSperren;
import vanillaplustweaks.bauen.RuestungsstaenderPosen;
import vanillaplustweaks.bauen.UnsichtbareRahmen;
import vanillaplustweaks.orientierung.Todespunkt;
import vanillaplustweaks.orientierung.Wegsteine;
import vanillaplustweaks.allgemein.DimensionsSperre;
import vanillaplustweaks.allgemein.ElytraBoostAbklingzeit;
import vanillaplustweaks.allgemein.Weltgrenze;
import vanillaplustweaks.allgemein.Zeitzone;
import vanillaplustweaks.datapacks.garten.GaertnersTraum;
import vanillaplustweaks.datapacks.koepfe.KopfDatenBehalten;
import vanillaplustweaks.datapacks.koepfe.SpielerKoepfe;
import vanillaplustweaks.datapacks.koepfe.TierKoepfe;
import vanillaplustweaks.datapacks.AfkAnzeige;
import vanillaplustweaks.datapacks.AlleRezepte;
import vanillaplustweaks.datapacks.EigeneNetherportale;
import vanillaplustweaks.datapacks.GepanzerteElytra;
import vanillaplustweaks.datapacks.KesselUmwandlung;
import vanillaplustweaks.datapacks.KlassischesAngeln;
import vanillaplustweaks.datapacks.KnospenderAmethyst;
import vanillaplustweaks.datapacks.LeereBoote;
import vanillaplustweaks.datapacks.MehrspielerSchlaf;
import vanillaplustweaks.datapacks.SchnellerLaubzerfall;
import vanillaplustweaks.datapacks.SteinsaegenRezepte;
import vanillaplustweaks.datapacks.StummeMobs;
import vanillaplustweaks.datapacks.WuestenzombieSand;
import vanillaplustweaks.datapacks.XpAbfuellen;
import vanillaplustweaks.komfort.DorfbewohnerFolgen;
import vanillaplustweaks.komfort.KnochenmehlExtra;
import vanillaplustweaks.komfort.RechtsklickErnte;
import vanillaplustweaks.komfort.SpenderPflanzen;
import vanillaplustweaks.komfort.TrampelSchutz;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Hauptklasse von VanillaPlusTweaks.
 *
 * Hier wird nur organisiert:
 *   onLoad()   – Config anlegen, Rechte registrieren, Zeitzone setzen
 *   onEnable() – alle eingeschalteten Module starten, Befehl /vptweaks und Platzhalter registrieren
 *
 * Die eigentlichen Funktionen stecken in den Modulen:
 *   allgemein/  – Zeitzone, CPU-Anzeige, Nether/End-Sperre, Elytra-Abklingzeit, Weltgrenze
 *   datapacks/  – Ersatz fuer die frueheren Datapacks
 *   bauen/      – Bauen & Deko (Rahmen, Ruestungsstaender, Bloecke drehen, Doppeltueren, Leinen)
 *   orientierung/ – Wegsteine und Todespunkt
 *   komfort/    – Vanilla+-Komfortfunktionen
 */
public final class VanillaPlusTweaks extends JavaPlugin {

    private final List<Modul> aktiveModule = new ArrayList<>();

    // Diese Module werden auch von aussen gebraucht (Platzhalter, Befehle)
    private CpuMessung cpuMessung;
    private DimensionsSperre dimensionsSperre;
    private LeereBoote leereBoote;


    // ==================================================================
    // Start
    // ==================================================================

    @Override
    public void onLoad() {
        saveDefaultConfig();

        registriereRecht("vptweaks.admin", "Befehl /vptweaks", PermissionDefault.OP);
        registriereRecht("vptweaks.elytra.bypass", "Keine Abklingzeit fuer Elytra-Raketen", PermissionDefault.FALSE);
        registriereRecht("vptweaks.dimension.bypass", "Gesperrte Nether-/End-Portale benutzen", PermissionDefault.FALSE);

        Zeitzone.anwenden(getConfig(), getLogger());
    }


    @Override
    public void onEnable() {
        cpuMessung = new CpuMessung(this);
        dimensionsSperre = new DimensionsSperre(this);
        leereBoote = new LeereBoote(this);

        List<Modul> alleModule = List.of(
                // allgemein
                cpuMessung,
                dimensionsSperre,
                new ElytraBoostAbklingzeit(this),
                new Weltgrenze(this),

                // Ersatz fuer Datapacks
                new AfkAnzeige(this),
                new MehrspielerSchlaf(this),
                new SchnellerLaubzerfall(this),
                new KesselUmwandlung(this),
                new StummeMobs(this),
                new SteinsaegenRezepte(this),       // vor "Alle Rezepte", damit sie mit freigeschaltet werden
                new AlleRezepte(this),
                leereBoote,
                new XpAbfuellen(this),
                new EigeneNetherportale(this),
                new WuestenzombieSand(this),
                new GepanzerteElytra(this),
                new KlassischesAngeln(this),
                new KnospenderAmethyst(this),
                new TierKoepfe(this),
                new SpielerKoepfe(this),
                new KopfDatenBehalten(this),
                new GaertnersTraum(this),

                // Komfort
                new RechtsklickErnte(this),
                new TrampelSchutz(this),
                new SpenderPflanzen(this),
                new KnochenmehlExtra(this),
                new DorfbewohnerFolgen(this),

                // Bauen & Deko
                new UnsichtbareRahmen(this),
                new RahmenSperren(this),
                new RuestungsstaenderPosen(this),
                new BloeckeDrehen(this),
                new Doppeltueren(this),
                new DorfbewohnerLeine(this),

                // Orientierung
                new Wegsteine(this),
                new Todespunkt(this));

        for (Modul modul : alleModule) {
            starte(modul);
        }

        registriereBefehl();
        registrierePlatzhalter();

        getLogger().info("Aktive Module: " + modulNamen());
    }


    private void starte(Modul modul) {
        if (!modul.istEingeschaltet()) {
            return;
        }

        try {
            modul.start();
            aktiveModule.add(modul);
        } catch (RuntimeException fehler) {
            // Ein fehlerhaftes Modul soll nicht das ganze Plugin lahmlegen
            getLogger().severe("Modul '" + modul.configAbschnitt() + "' konnte nicht starten: " + fehler);
        }
    }


    private String modulNamen() {
        List<String> namen = new ArrayList<>();
        for (Modul modul : aktiveModule) {
            namen.add(modul.configAbschnitt());
        }
        return String.join(", ", namen);
    }


    private void registriereRecht(String name, String beschreibung, PermissionDefault standard) {
        if (getServer().getPluginManager().getPermission(name) == null) {
            getServer().getPluginManager().addPermission(new Permission(name, beschreibung, standard));
        }
    }


    private void registrierePlatzhalter() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().info("PlaceholderAPI nicht gefunden – Platzhalter %vptweaks_...% sind nicht verfuegbar.");
            return;
        }

        new TweaksPlatzhalter(this).register();
        getLogger().info("Platzhalter registriert: %vptweaks_cpu%, %vptweaks_deaths%, %vptweaks_nether%, %vptweaks_end%");
    }


    // ==================================================================
    // Befehl /vptweaks
    // ==================================================================

    private void registriereBefehl() {
        registerCommand("vptweaks", new BasicCommand() {

            @Override
            public void execute(CommandSourceStack quelle, String[] argumente) {
                befehlAusfuehren(quelle.getSender(), argumente);
            }

            @Override
            public String permission() {
                return "vptweaks.admin";
            }
        });
    }


    private void befehlAusfuehren(CommandSender absender, String[] argumente) {
        String unterbefehl = (argumente.length == 0) ? "" : argumente[0].toLowerCase();

        switch (unterbefehl) {
            case "reload":
                neuLaden(absender);
                break;

            case "boote":
                booteEntfernen(absender);
                break;

            case "status":
                absender.sendMessage("§7Aktive Module: §f" + modulNamen());
                absender.sendMessage("§7Nether: §f" + netherStatus() + " §8| §7End: §f" + endStatus());
                break;

            default:
                absender.sendMessage("§7/vptweaks reload §8– Termine, Elytra-Abklingzeit und Weltgrenze neu laden");
                absender.sendMessage("§7/vptweaks boote §8– leere Boote entfernen");
                absender.sendMessage("§7/vptweaks status §8– aktive Module und Freischalt-Termine");
                break;
        }
    }


    private void neuLaden(CommandSender absender) {
        reloadConfig();

        for (Modul modul : aktiveModule) {
            if (modul instanceof NeuLadbar neuLadbar) {
                neuLadbar.neuLaden();
            }
        }

        absender.sendMessage("§aVanillaPlusTweaks neu geladen. Nether: " + netherStatus() + ", End: " + endStatus());
        absender.sendMessage("§7Module ein-/ausschalten, Rezepte und Zeitzone wirken erst nach einem Neustart.");
    }


    private void booteEntfernen(CommandSender absender) {
        if (!aktiveModule.contains(leereBoote)) {
            absender.sendMessage("§cDas Modul empty-boats ist in der config.yml ausgeschaltet.");
            return;
        }

        int anzahl = leereBoote.leereBooteEntfernen();
        absender.sendMessage("§a" + anzahl + " leere Boote entfernt.");
    }


    // ==================================================================
    // Werte fuer die Platzhalter
    // ==================================================================

    String cpuProzent() {
        return aktiveModule.contains(cpuMessung) ? cpuMessung.prozent() : "?";
    }


    String netherStatus() {
        return aktiveModule.contains(dimensionsSperre) ? dimensionsSperre.netherStatus() : "offen";
    }


    String endStatus() {
        return aktiveModule.contains(dimensionsSperre) ? dimensionsSperre.endStatus() : "offen";
    }


    /** Fuer andere Module (z. B. Wegsteine): liegt dieser Ort in einer noch gesperrten Dimension? */
    public boolean dimensionGesperrt(org.bukkit.Location ziel) {
        return aktiveModule.contains(dimensionsSperre) && dimensionsSperre.istGesperrt(ziel);
    }
}

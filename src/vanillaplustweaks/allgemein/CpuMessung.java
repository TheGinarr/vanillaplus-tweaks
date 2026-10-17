package vanillaplustweaks.allgemein;

import vanillaplustweaks.Modul;
import vanillaplustweaks.VanillaPlusTweaks;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * Misst, wie viel CPU der Server-Prozess braucht – fuer den Platzhalter %vptweaks_cpu% im Tab.
 *
 * Rechnung: verbrauchte CPU-Zeit des Prozesses geteilt durch die vergangene Echtzeit.
 * 100 % bedeutet also "ein voller CPU-Kern" (gleiche Skala wie in vielen Hosting-Panels).
 *
 * Gemessen wird alle 5 Sekunden, das kostet praktisch nichts.
 */
public final class CpuMessung extends Modul {

    private long letzteCpuZeitNanos = -1;
    private long letzteEchtzeitNanos;

    private volatile String prozent = "0";


    public CpuMessung(VanillaPlusTweaks plugin) {
        super(plugin);
    }


    @Override
    public String configAbschnitt() {
        return "cpu-placeholder";
    }


    @Override
    public void start() {
        wiederholeAlle(100, this::messen);
    }


    /** Letzter Messwert als ganze Zahl ohne Prozentzeichen, z. B. "37". */
    public String prozent() {
        return prozent;
    }


    private void messen() {
        OperatingSystemMXBean betriebssystem = ManagementFactory.getOperatingSystemMXBean();

        // Die Prozess-CPU-Zeit gibt es nur ueber die erweiterte Schnittstelle von OpenJDK
        if (!(betriebssystem instanceof com.sun.management.OperatingSystemMXBean erweitert)) {
            return;
        }

        long cpuZeit = erweitert.getProcessCpuTime();
        long echtzeit = System.nanoTime();

        boolean ersteMessung = letzteCpuZeitNanos < 0;
        if (!ersteMessung && cpuZeit >= 0 && echtzeit > letzteEchtzeitNanos) {
            double anteil = (cpuZeit - letzteCpuZeitNanos) * 100.0 / (echtzeit - letzteEchtzeitNanos);
            prozent = String.valueOf(Math.round(anteil));
        }

        letzteCpuZeitNanos = cpuZeit;
        letzteEchtzeitNanos = echtzeit;
    }
}

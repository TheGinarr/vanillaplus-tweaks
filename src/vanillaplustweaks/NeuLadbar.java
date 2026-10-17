package vanillaplustweaks;

/**
 * Markiert Module, deren Einstellungen sich mit "/vptweaks reload"
 * ohne Server-Neustart neu einlesen lassen.
 */
public interface NeuLadbar {

    /** Liest die Werte aus der (bereits neu geladenen) config.yml erneut ein. */
    void neuLaden();
}

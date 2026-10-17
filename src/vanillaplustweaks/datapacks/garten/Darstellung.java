package vanillaplustweaks.datapacks.garten;

/**
 * Wie eine Pflanze im Topf aussieht: Verschiebung und Groesse des Block-Displays sowie zwei Drehungen
 * (als Quaternion x, y, z, w – so speichert Minecraft die "transformation" eines Displays).
 */
public record Darstellung(float[] verschiebung, float[] groesse, float[] drehungLinks, float[] drehungRechts) {

    public static final float[] KEINE_DREHUNG = {0f, 0f, 0f, 1f};
}

package vanillaplustweaks.datapacks.garten;

/** Ein Punkt in der Welt (x, y, z). */
public record Vek(double x, double y, double z) {

    public Vek plus(double dx, double dy, double dz) {
        return new Vek(x + dx, y + dy, z + dz);
    }


    /** Wie "align xyz": auf die Block-Ecke abrunden. */
    public Vek blockEcke() {
        return new Vek(Math.floor(x), Math.floor(y), Math.floor(z));
    }


    /** Wie "align xyz positioned ~.5 ~ ~.5": Block-Mitte am Boden des Blocks. */
    public Vek blockMitteUnten() {
        return blockEcke().plus(0.5, 0, 0.5);
    }


    public int blockX() {
        return (int) Math.floor(x);
    }


    public int blockY() {
        return (int) Math.floor(y);
    }


    public int blockZ() {
        return (int) Math.floor(z);
    }


    public double abstand(Vek anderer) {
        double dx = x - anderer.x;
        double dy = y - anderer.y;
        double dz = z - anderer.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

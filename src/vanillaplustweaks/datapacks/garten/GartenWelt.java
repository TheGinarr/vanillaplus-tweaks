package vanillaplustweaks.datapacks.garten;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Alles, was die Garten-Logik von der Welt wissen oder in ihr veraendern muss.
 *
 * Im Plugin steckt dahinter die echte Minecraft-Welt (Bloecke, Block-Displays, Spieler).
 * Im Vergleichstest eine kleine Nachbildung – so laesst sich die Logik ausserhalb des Servers
 * gegen das Original-Datapack pruefen.
 */
public interface GartenWelt {

    /** Ein Block: ID (z. B. "minecraft:player_head") und seine Eigenschaften (z. B. rotation=5). */
    record Block(String id, Map<String, String> eigenschaften) {

        public static final Block LUFT = new Block("minecraft:air", Map.of());

        public String eigenschaft(String name) {
            return eigenschaften.get(name);
        }
    }


    Block block(int x, int y, int z);


    /** Alle Pflanzen-Anzeigen in der Naehe (Block-Displays des Gartens). */
    List<? extends Pflanze> pflanzen();


    /** Neue Anzeige erschaffen (entspricht "summon block_display"). */
    Pflanze erschaffe(Vek position, Set<String> markierungen, String blockZustand, Darstellung darstellung);


    /** Anzeige entfernen. */
    void entferne(Pflanze pflanze);


    /** Ein Item fallen lassen (z. B. die Pflanze, wenn der Topf kaputt ist). */
    void lasseFallen(Vek position, Pflanze vonPflanze);


    // ------------------------------------------------------------------

    /** Eine Pflanzen-Anzeige (Block-Display) in der Welt. */
    interface Pflanze {

        Vek position();

        void setzePosition(Vek position);

        Set<String> markierungen();

        void markiere(String markierung);

        void entmarkiere(String markierung);

        /** "minecraft:air", wenn noch kein Block gesetzt ist. */
        String blockId();

        /** Block ueber die Item-ID setzen ("data modify ... block_state set from SelectedItem.id"). */
        void setzeBlockVonItem(String itemId);

        float gierung();

        void setzeGierung(float gierung);

        /** Nur Verschiebung und Groesse aendern ("data merge ... {transformation:{translation, scale}}"). */
        void setzeVerschiebungUndGroesse(float[] verschiebung, float[] groesse);
    }
}

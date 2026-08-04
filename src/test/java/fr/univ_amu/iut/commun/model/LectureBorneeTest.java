package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Lecture bornée d'une entrée externe (#3222) : ce qui vient de la carte SD ou du réseau ne se lit
/// plus qu'en dessous d'un plafond, et le refus nomme la limite.
///
/// Le test central est [#texte_refuse_avant_d_avoir_tout_lu] : un plafond vérifié **après** avoir tout
/// chargé ne protège de rien, la mémoire est déjà prise. Il compte donc les octets réellement demandés
/// à la source.
class LectureBorneeTest {

    private static final long UN_MO = 1024L * 1024;

    @TempDir
    Path racine;

    @AfterEach
    void rendreLesProprietes() {
        System.clearProperty("vigiechiro.import.journal.max-octets");
        System.clearProperty("vigiechiro.reseau.corps.max-octets");
    }

    @Test
    @DisplayName("Un fichier sous le plafond se lit normalement, ligne à ligne")
    void lignes_sous_le_plafond() throws IOException {
        Path journal = racine.resolve("LogPR1925492.txt");
        Files.writeString(journal, "premiere\nseconde\n", StandardCharsets.UTF_8);

        List<String> lignes = LectureBornee.lignes(journal, PlafondLecture.journalCapteur());

        assertThat(lignes).containsExactly("premiere", "seconde");
    }

    @Test
    @DisplayName("Un fichier au-delà du plafond est refusé, et le refus nomme limite, taille et surcharge")
    void lignes_refuse_au_dela_du_plafond() throws IOException {
        System.setProperty("vigiechiro.import.journal.max-octets", Long.toString(UN_MO));
        Path journal = racine.resolve("LogPR1925492.txt");
        Files.write(journal, new byte[(int) (2 * UN_MO)]);

        assertThatThrownBy(() -> LectureBornee.lignes(journal, PlafondLecture.journalCapteur()))
                .isInstanceOf(EntreeTropVolumineuse.class)
                .hasMessageContaining("LogPR1925492.txt")
                .hasMessageContaining("2 Mo")
                .hasMessageContaining("1 Mo")
                .hasMessageContaining("-Dvigiechiro.import.journal.max-octets=");
    }

    @Test
    @DisplayName("Le plafond est une taille admise, pas une taille interdite (limite incluse)")
    void lignes_accepte_exactement_le_plafond() throws IOException {
        System.setProperty("vigiechiro.import.journal.max-octets", "8");
        Path journal = racine.resolve("court.txt");
        Files.writeString(journal, "12345678", StandardCharsets.UTF_8);

        assertThatCode(() -> LectureBornee.lignes(journal, PlafondLecture.journalCapteur()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un flux sous le plafond se lit en entier")
    void texte_sous_le_plafond() throws IOException {
        InputStream flux = new ByteArrayInputStream("{\"_id\":\"u-1\"}".getBytes(StandardCharsets.UTF_8));

        String corps = LectureBornee.texte(flux, PlafondLecture.corpsReseau(), "/moi");

        assertThat(corps).isEqualTo("{\"_id\":\"u-1\"}");
    }

    @Test
    @DisplayName("Un flux au-delà du plafond est refusé SANS avoir été lu en entier")
    void texte_refuse_avant_d_avoir_tout_lu() {
        PlafondLecture plafond =
                new PlafondLecture("vigiechiro.reseau.corps.max-octets", UN_MO, "Réponse du serveur refusée");
        FluxCompte source = new FluxCompte(8 * (int) UN_MO);

        assertThatThrownBy(() -> LectureBornee.texte(source, plafond, "/donnees"))
                .isInstanceOf(EntreeTropVolumineuse.class)
                .hasMessageContaining("/donnees")
                .hasMessageContaining("1 Mo");

        assertThat(source.lus())
                .as("un plafond vérifié après coup ne protège de rien : la mémoire est déjà prise")
                .isLessThanOrEqualTo(UN_MO + LectureBornee.TAILLE_BLOC_OCTETS);
    }

    @Test
    @DisplayName("Les plafonds par défaut sont larges, et chacun se surcharge par propriété système")
    void plafonds_par_defaut_et_surcharge() {
        assertThat(PlafondLecture.journalCapteur().octets()).isEqualTo(32 * UN_MO);
        assertThat(PlafondLecture.corpsReseau().octets()).isEqualTo(64 * UN_MO);

        System.setProperty("vigiechiro.import.journal.max-octets", "4096");
        System.setProperty("vigiechiro.reseau.corps.max-octets", "8192");

        assertThat(PlafondLecture.journalCapteur().octets()).isEqualTo(4096);
        assertThat(PlafondLecture.corpsReseau().octets()).isEqualTo(8192);
    }

    /// Source qui compte les octets réellement demandés : c'est elle qui distingue « on refuse » de
    /// « on refuse **avant** d'avoir tout avalé ».
    private static final class FluxCompte extends InputStream {

        private final int disponibles;
        private long lus;

        private FluxCompte(int disponibles) {
            this.disponibles = disponibles;
        }

        private long lus() {
            return lus;
        }

        @Override
        public int read() {
            if (lus >= disponibles) {
                return -1;
            }
            lus++;
            return 'x';
        }

        @Override
        public int read(byte[] tampon, int debut, int longueur) {
            if (lus >= disponibles) {
                return -1;
            }
            int rendus = (int) Math.min(longueur, disponibles - lus);
            Arrays.fill(tampon, debut, debut + rendus, (byte) 'x');
            lus += rendus;
            return rendus;
        }
    }
}

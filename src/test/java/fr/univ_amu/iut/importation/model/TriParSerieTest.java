package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// N'importer que la série du journal (#1492).
class TriParSerieTest {

    private static final String SERIE = "1925492";

    private static Path wav(String serie, String horodatage) {
        return Path.of("/sd/PaRecPR" + serie + "_" + horodatage + ".wav");
    }

    @Test
    @DisplayName("#1492 : les enregistrements d'un autre capteur sont écartés")
    void autre_capteur_ecarte() {
        Path mien = wav(SERIE, "20260422_203922");
        Path etranger = wav("1648011", "20260422_210000");

        TriParSerie tri = TriParSerie.selon(List.of(mien, etranger), SERIE);

        // Sans ce tri, le passage porte l'enregistreur du journal ET des séquences d'un autre capteur :
        // une donnée incohérente avec elle-même, qui part telle quelle au dépôt.
        assertThat(tri.retenus()).containsExactly(mien);
        assertThat(tri.ecartes()).containsExactly(etranger);
        assertThat(tri.aEcarte()).isTrue();
    }

    @Test
    @DisplayName("#1492 : un dossier homogène n'écarte rien")
    void dossier_homogene_ne_perd_rien() {
        List<Path> tous = List.of(wav(SERIE, "20260422_203922"), wav(SERIE, "20260422_204326"));

        TriParSerie tri = TriParSerie.selon(tous, SERIE);

        // Le garde-fou qui compte : le cas nominal est de loin le plus fréquent, et y perdre un fichier
        // serait bien pire que le défaut qu'on corrige.
        assertThat(tri.retenus()).isEqualTo(tous);
        assertThat(tri.aEcarte()).isFalse();
    }

    @Test
    @DisplayName("#1492 : sans série attendue, rien n'est écarté")
    void sans_journal_rien_n_est_ecarte() {
        List<Path> tous = List.of(wav(SERIE, "20260422_203922"), wav("1648011", "20260422_210000"));

        // Mode dégradé (#107) : un journal absent n'empêche pas d'importer. Sans référence, écarter
        // reviendrait à choisir arbitrairement laquelle des deux séries est la bonne.
        assertThat(TriParSerie.selon(tous, null).ecartes()).isEmpty();
        assertThat(TriParSerie.selon(tous, "  ").retenus()).isEqualTo(tous);
    }

    @Test
    @DisplayName("#1492 : un nom sans série lisible est retenu, pas écarté")
    void nom_hors_motif_reste_retenu() {
        Path libre = Path.of("/sd/enregistrement-du-22-avril.wav");

        TriParSerie tri = TriParSerie.selon(List.of(libre), SERIE);

        // L'écarter le ferait disparaître sur un critère qu'il ne peut pas satisfaire. C'est à la
        // transformation de dire ce qu'elle en fait, avec son propre motif de rejet.
        assertThat(tri.retenus()).containsExactly(libre);
        assertThat(tri.ecartes()).isEmpty();
    }
}

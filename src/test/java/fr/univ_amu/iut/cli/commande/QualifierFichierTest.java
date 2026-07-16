package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Traduction du mot-clé de verdict par fichier de `qualifier-fichier` (#1512). `verdictFichierDepuis`
/// est une fonction pure : `bon` / `mauvais` / `inexploitable` (toute casse) donnent le bon
/// [VerdictFichier], l'inconnu lève une erreur d'usage.
class QualifierFichierTest {

    @Test
    @DisplayName("bon / mauvais / inexploitable (toute casse) donnent le bon verdict par fichier")
    void verdict_fichier_depuis_mots_cles() {
        assertThat(QualifierFichier.verdictFichierDepuis("bon")).isEqualTo(VerdictFichier.BON);
        assertThat(QualifierFichier.verdictFichierDepuis("MAUVAIS")).isEqualTo(VerdictFichier.MAUVAIS);
        assertThat(QualifierFichier.verdictFichierDepuis("Inexploitable")).isEqualTo(VerdictFichier.INEXPLOITABLE);
    }

    @Test
    @DisplayName("Un mot-clé inconnu lève une erreur d'usage listant les valeurs acceptées")
    void verdict_fichier_inconnu_leve_erreur_usage() {
        assertThatThrownBy(() -> QualifierFichier.verdictFichierDepuis("bof"))
                .isInstanceOf(ErreurUsage.class)
                .hasMessageContaining("bon, mauvais, inexploitable");
    }
}

package fr.univ_amu.iut.commun;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation.Severite;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'échelle de sévérité, et le fait que **l'ordre de déclaration la porte**.
///
/// [CompteRendu#severite()] prend le maximum par `ordinal()` : réordonner l'enum changerait
/// silencieusement quel constat qualifie un compte rendu entier, sans qu'aucun test existant ne rougisse.
/// Ce fichier transforme cette convention tacite en fait vérifié.
class SeveriteTest {

    @Test
    @DisplayName("l'ordre de déclaration va du plus faible au plus fort")
    void ordre_croissant() {
        assertThat(Severite.values())
                .containsExactly(Severite.SUCCES, Severite.INFO, Severite.AVERTISSEMENT, Severite.ERREUR);
    }

    @Test
    @DisplayName("un avertissement ne masque pas une erreur, et une erreur qualifie le compte rendu entier")
    void la_plus_forte_qualifie_l_ensemble() {
        CompteRendu mele = CompteRendu.de(
                "Bilan",
                List.of(
                        Constat.de("tout s'est bien passé", Severite.SUCCES),
                        Constat.de("une nuit était déjà importée", Severite.AVERTISSEMENT),
                        Constat.de("un fichier a été rejeté", Severite.ERREUR)));

        assertThat(mele.severite()).isEqualTo(Severite.ERREUR);

        CompteRendu sansEchec = CompteRendu.de(
                "Bilan",
                List.of(
                        Constat.de("tout s'est bien passé", Severite.SUCCES),
                        Constat.de("une nuit était déjà importée", Severite.AVERTISSEMENT)));

        // Sans le nouveau niveau, ce cas n'avait que deux issues, toutes deux fausses : se présenter en
        // succès (l'avertissement disparaît) ou en erreur (rien n'a échoué).
        assertThat(sansEchec.severite()).isEqualTo(Severite.AVERTISSEMENT);
    }

    @Test
    @DisplayName("la fabrique d'avertissement porte le niveau, pas un glyphe dans le texte")
    void fabrique_avertissement() {
        RetourOperation retour = RetourOperation.avertissement("Cette nuit a déjà été importée.");

        assertThat(retour.severite()).isEqualTo(Severite.AVERTISSEMENT);
        assertThat(retour.present()).isTrue();
        // Le point de tout l'exercice : le message ne porte plus de marqueur. La vue rend la sévérité
        // deux fois (couleur et icône) depuis la valeur, et ne peut donc pas se contredire.
        assertThat(retour.texte()).doesNotContain("⚠");
    }
}

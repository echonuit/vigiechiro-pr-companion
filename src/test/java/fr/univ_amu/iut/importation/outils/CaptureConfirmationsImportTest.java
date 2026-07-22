package fr.univ_amu.iut.importation.outils;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Pré-enroulage des comptes rendus capturés (#2243).
///
/// **Pourquoi ce test existe.** Hors `showAndWait`, un libellé `wrapText` long ne s'enroule pas au
/// snapshot : il se coupe par une ellipse. Le remède est de pré-découper les textes du compte rendu
/// avant de le rendre. Or le garde-fou de fidélité d'`ApercuFx` **ne voit pas** cette troncature-là
/// (#2265) : si le pré-enroulage disparaissait, la capture redeviendrait tronquée **sans qu'aucune
/// chaîne ne rougisse**, et la documentation publierait une image qui ment.
///
/// Ce test est donc la **seule** protection de #2243.
class CaptureConfirmationsImportTest {

    /// Un détail plus long qu'une ligne de capture : c'est le cas qui se tronquait (#2243).
    private static final String DETAIL_LONG =
            "Dont 87 validation(s) Tadarida (correction, référence, commentaire) définitivement perdue(s).";

    private static CompteRendu compteRendu(String detail) {
        Constat perte = new Constat(
                "Suppression DÉFINITIVE du passage existant et de ses 342 séquence(s).",
                Severite.ERREUR,
                List.of(Detail.de(detail)));
        return new CompteRendu("", "", List.of(perte), "Action irréversible. Confirmer l'écrasement ?");
    }

    @Test
    @DisplayName("Un détail trop long pour une ligne est coupé en plusieurs, sans qu'un mot change")
    void detail_long_est_enroule_sans_changer_un_mot() {
        Detail enroule = CaptureConfirmationsImport.enrouler(compteRendu(DETAIL_LONG))
                .constats()
                .getFirst()
                .details()
                .getFirst();

        assertThat(enroule.sujet())
                .as("sans retour à la ligne, le libellé reste sur une ligne et le snapshot la coupe")
                .contains("\n");
        assertThat(enroule.sujet().replace('\n', ' '))
                .as("l'enroulement coupe aux espaces : aucun mot n'est ajouté, retiré ni modifié")
                .isEqualTo(DETAIL_LONG);
    }

    @Test
    @DisplayName("Un texte qui tient sur une ligne n'est pas touché, et la sévérité est préservée")
    void texte_court_intact_et_severite_preservee() {
        CompteRendu enroule = CaptureConfirmationsImport.enrouler(compteRendu("Dont 2 validation(s)."));

        Constat constat = enroule.constats().getFirst();
        assertThat(constat.details().getFirst().sujet())
                .as("rien à couper : le texte doit ressortir tel quel")
                .isEqualTo("Dont 2 validation(s).");
        assertThat(constat.severite())
                .as("le pré-enroulage ne touche qu'au texte, jamais à la sévérité qui pilote l'icône")
                .isEqualTo(Severite.ERREUR);
        assertThat(enroule.conclusion()).isEqualTo("Action irréversible. Confirmer l'écrasement ?");
    }
}

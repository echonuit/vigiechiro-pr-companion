package fr.univ_amu.iut.multisite;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'état d'analyse d'une nuit **récupérée** (#2775).
///
/// C'est la régression la plus coûteuse qu'aurait causée le lot 1, et la plus discrète. Avant lui, une
/// nuit rapatriée portait « Déposé », donc son état d'analyse se calculait et elle apparaissait dans la
/// vue « Résultats à importer » - le chemin par lequel on récupère ses résultats Tadarida. L'en sortir
/// l'aurait rendue `SANS_OBJET`, c'est-à-dire **invisible**, sans qu'aucun test ne rougisse.
class EtatAnalyseNuitRecupereeTest {

    @Test
    @DisplayName("#2775 : une nuit récupérée a un état d'analyse, comme une nuit déposée")
    void nuit_recuperee_a_un_etat_d_analyse() {
        assertThat(EtatAnalyse.deduire(StatutWorkflow.RECUPERE, Optional.empty(), false))
                .as("elle est sur la plateforme : son analyse serveur existe, ou reste à demander")
                .isEqualTo(EtatAnalyse.JAMAIS_RELEVE);

        assertThat(EtatAnalyse.deduire(StatutWorkflow.DEPOSE, Optional.empty(), false))
                .as("même réponse que pour une nuit que nous avons déposée")
                .isEqualTo(EtatAnalyse.JAMAIS_RELEVE);
    }

    @Test
    @DisplayName("#2775 : une nuit qui n'est pas encore partie n'a toujours pas d'analyse à relever")
    void nuit_locale_reste_sans_objet() {
        for (StatutWorkflow statut : StatutWorkflow.values()) {
            if (statut.estSurLaPlateforme()) {
                continue;
            }
            assertThat(EtatAnalyse.deduire(statut, Optional.empty(), false))
                    .as("« %s » : rien n'a encore été soumis au serveur", statut.libelle())
                    .isEqualTo(EtatAnalyse.SANS_OBJET);
        }
    }
}

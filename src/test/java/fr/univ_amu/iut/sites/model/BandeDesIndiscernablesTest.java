package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.CarreCandidat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La bande des indiscernables et ses **deux** seuils (#4671, passe 7).
///
/// Le test qui compte est [#les_deux_seuils_different] : il n'existe que pour empêcher qu'on les
/// unifie, ce qui est précisément la raison d'être du type.
class BandeDesIndiscernablesTest {

    @Test
    @DisplayName("les deux seuils DIVERGENT, et ce test n'existe que pour l'exiger")
    void les_deux_seuils_different() {
        // Proposer un numéro faux et plausible se paie cher : il est validé sans se relire, et contamine
        // ensuite le préfixe de tous les fichiers. Taire un contrôle ne coûte qu'un contrôle. Les unifier
        // sur l'une ou l'autre valeur ferait donc payer un usage pour l'autre.
        assertThat(BandeDesIndiscernables.POUR_CONTROLER)
                .as("le contrôle est le plus prudent des deux, délibérément")
                .isGreaterThan(BandeDesIndiscernables.POUR_PROPOSER);
    }

    @Test
    @DisplayName("chaque seuil garde sa valeur, encadrée de part et d'autre")
    void chaque_seuil_est_encadre() {
        // La borne stricte elle-même n'est pas testable - un écart exactement égal au seuil n'est pas
        // atteignable en doubles - donc c'est la VALEUR qu'on encadre.
        assertThat(BandeDesIndiscernables.POUR_PROPOSER).isBetween(40.0, 60.0);
        assertThat(BandeDesIndiscernables.POUR_CONTROLER).isBetween(80.0, 140.0);
    }

    @Test
    @DisplayName("la bande retient le plus proche et ce qui ne s'en distingue pas")
    void la_bande_retient_les_indiscernables() {
        List<CarreCandidat> candidats = List.of(
                new CarreCandidat("040110", 970), new CarreCandidat("040111", 1010), new CarreCandidat("040112", 2000));

        assertThat(BandeDesIndiscernables.dans(candidats, 50))
                .extracting(CarreCandidat::numero)
                .containsExactly("040110", "040111");
        assertThat(BandeDesIndiscernables.dans(candidats, 20))
                .extracting(CarreCandidat::numero)
                .as("un seuil plus serré ne retient que le premier")
                .containsExactly("040110");
    }

    @Test
    @DisplayName("aucun candidat : une bande vide, et pas une exception")
    void aucun_candidat_rend_une_bande_vide() {
        assertThat(BandeDesIndiscernables.dans(List.of(), 100)).isEmpty();
    }
}

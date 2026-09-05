package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.qualification.model.FenetreDeCouverture;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les deux sources de la fenêtre de couverture, et surtout le **repli** (clôture de #5065, passe 6).
///
/// La mutation a montré que les deux chemins de repli n'étaient couverts par rien : les tests du
/// service fabriquaient les mesures à la main et les contournaient. Or c'est précisément le
/// comportement qui a été **arbitré** en ouvrant #5055, donc celui qu'il faut tenir.
class FenetreDeCouvertureTest {

    private static Passage passageDeLaNuit(String date, String debut, String fin) {
        return new Passage(1L, 1, 2026, date, debut, fin, null, null, null, null, null, null, 1L, "1925492", null);
    }

    @Test
    @DisplayName("Sans point, la fenêtre se replie sur les heures déclarées et le dit")
    void sans_point_la_fenetre_se_replie() {
        Optional<FenetreDeCouverture.Fenetre> fenetre =
                FenetreDeCouverture.deReference(null, passageDeLaNuit("2026-06-20", "20:00:00", "06:00:00"));

        assertThat(fenetre).isPresent();
        assertThat(fenetre.get().depuisLesEphemerides())
                .as("sans coordonnées, la fenêtre ne peut PAS venir des éphémérides, et le drapeau est"
                        + " ce qui permet à l'explication de le dire plutôt que de se taire")
                .isFalse();
        assertThat(fenetre.get().bornes()[0]).isEqualTo(LocalDateTime.of(2026, 6, 20, 20, 0));
    }

    @Test
    @DisplayName("Avec un point géolocalisé, la fenêtre vient du protocole")
    void avec_un_point_la_fenetre_vient_du_protocole() {
        PointDEcoute point = new PointDEcoute(1L, "A1", 43.5, 5.4, null, 1L, false);

        Optional<FenetreDeCouverture.Fenetre> fenetre =
                FenetreDeCouverture.deReference(point, passageDeLaNuit("2026-06-20", "20:00:00", "06:00:00"));

        assertThat(fenetre).isPresent();
        assertThat(fenetre.get().depuisLesEphemerides()).isTrue();
        assertThat(fenetre.get().bornes()[0])
                .as("le coucher au point vaut 21:23 le 20 juin : la fenêtre exigée commence trente"
                        + " minutes avant, et non à l'heure déclarée")
                .isEqualTo(LocalDateTime.of(2026, 6, 20, 20, 53, 43));
    }

    @Test
    @DisplayName("Des heures illisibles ne rendent aucune fenêtre, plutôt qu'une fenêtre fausse")
    void des_heures_illisibles_ne_rendent_rien() {
        assertThat(FenetreDeCouverture.deReference(null, passageDeLaNuit("2026-06-20", "n'importe quoi", "06:00:00")))
                .as("une fenêtre inventée ferait juger la couverture contre rien, et le feu passerait"
                        + " au vert par ignorance")
                .isEmpty();
    }

    @Test
    @DisplayName("Un point géolocalisé dont les éphémérides ne se calculent pas se replie aussi")
    void la_nuit_polaire_se_replie() {
        // Le second repli, et la mutation a montré qu'il n'était couvert par rien : avoir des
        // coordonnées ne garantit pas une nuit. Au-delà du cercle polaire en juin, le soleil ne se
        // couche pas, et `AnalyseCoherenceHoraire` rend indisponible plutôt que d'inventer une heure.
        PointDEcoute svalbard = new PointDEcoute(1L, "A1", 78.2, 15.6, null, 1L, false);

        Optional<FenetreDeCouverture.Fenetre> fenetre =
                FenetreDeCouverture.deReference(svalbard, passageDeLaNuit("2026-06-20", "20:00:00", "06:00:00"));

        assertThat(fenetre).isPresent();
        assertThat(fenetre.get().depuisLesEphemerides())
                .as("sans nuit calculable, la mesure se replie sur les heures déclarées comme si le"
                        + " point n'était pas géolocalisé, et le dit de la même façon")
                .isFalse();
    }

    @Test
    @DisplayName("Une plage qui ne franchit pas minuit reste dans sa journée")
    void une_plage_dans_la_journee_ne_gagne_pas_un_jour() {
        Optional<FenetreDeCouverture.Fenetre> fenetre =
                FenetreDeCouverture.deReference(null, passageDeLaNuit("2026-06-20", "20:00:00", "23:00:00"));

        assertThat(fenetre).isPresent();
        assertThat(fenetre.get().bornes()[1])
                .as("23:00 est APRÈS 20:00 : ajouter un jour ferait durer la fenêtre vingt-sept heures,"
                        + " et toute nuit paraîtrait alors incomplète")
                .isEqualTo(LocalDateTime.of(2026, 6, 20, 23, 0));
    }
}

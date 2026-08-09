package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// État observable du tableau de bord d'accueil (#1376). Pas de TestFX ni de base : les indicateurs
/// sont un contrat à cinq accesseurs, et c'est précisément ce qui permet de les doubler ici.
class AccueilViewModelTest {

    /// Indicateur dont la valeur est **pilotée par le test** : c'est ce qui permet de vérifier qu'une
    /// relecture a bien eu lieu, et pas seulement que la liste existe.
    private record IndicateurDouble(int ordre, String libelle, LongSupplier source) implements IndicateurAccueil {

        @Override
        public String iconeLiteral() {
            return "fas-moon";
        }

        @Override
        public String couleur() {
            return "#a29bfe";
        }

        @Override
        public long valeur() {
            return source.getAsLong();
        }
    }

    @Test
    @DisplayName("à la construction, les compteurs sont déjà lus")
    void a_la_construction_les_compteurs_sont_deja_lus() {
        RevisionDonnees revision = new RevisionDonnees(Runnable::run);

        AccueilViewModel viewModel =
                new AccueilViewModel(Set.of(new IndicateurDouble(10, "Sites", () -> 3L)), revision);

        // Sans cette lecture initiale, l'accueil resterait vide au démarrage sur une base pleine :
        // il n'y a aucune mutation à observer quand on vient d'ouvrir l'application.
        assertThat(viewModel.compteurs()).hasSize(1);
        assertThat(viewModel.compteurs().getFirst().valeur()).isEqualTo(3L);
        assertThat(viewModel.aDesDonnees()).isTrue();
    }

    @Test
    @DisplayName("une révision relit les compteurs")
    void une_revision_relit_les_compteurs() {
        RevisionDonnees revision = new RevisionDonnees(Runnable::run);
        long[] sites = {0L};
        AccueilViewModel viewModel =
                new AccueilViewModel(Set.of(new IndicateurDouble(10, "Sites", () -> sites[0])), revision);
        assertThat(viewModel.aDesDonnees()).isFalse();

        sites[0] = 12L;
        revision.mutationStructurelleValidee();

        assertThat(viewModel.compteurs().getFirst().valeur()).isEqualTo(12L);
        assertThat(viewModel.aDesDonnees()).isTrue();
    }

    @Test
    @DisplayName("base vide : aucun compteur non nul, le bandeau n'a rien à montrer")
    void base_vide_rien_a_montrer() {
        AccueilViewModel viewModel = new AccueilViewModel(
                Set.of(new IndicateurDouble(10, "Sites", () -> 0L), new IndicateurDouble(20, "Points", () -> 0L)),
                new RevisionDonnees(Runnable::run));

        assertThat(viewModel.compteurs()).as("les compteurs existent").hasSize(2);
        assertThat(viewModel.aDesDonnees())
                .as("mais aucun n'est renseigné : l'accueil reste épuré")
                .isFalse();
    }

    @Test
    @DisplayName("les compteurs sortent triés par ordre(), quel que soit l'ordre du Set")
    void les_compteurs_sortent_tries() {
        AccueilViewModel viewModel = new AccueilViewModel(
                Set.of(
                        new IndicateurDouble(30, "Observations", () -> 1L),
                        new IndicateurDouble(10, "Sites", () -> 1L),
                        new IndicateurDouble(20, "Points", () -> 1L)),
                new RevisionDonnees(Runnable::run));

        assertThat(viewModel.compteurs())
                .extracting(compteur -> compteur.indicateur().libelle())
                .containsExactly("Sites", "Points", "Observations");
    }
}

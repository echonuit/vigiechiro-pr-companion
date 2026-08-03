package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.commun.viewmodel.ResteDeRestauration;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Renommer une clé de critère sans **casser les vues déjà enregistrées** (#3096).
///
/// Une vue mémorisée nomme ses critères en clair dans `vue_sauvegardee`. Renommer une clé rendrait donc
/// caduques toutes les vues enregistrées avant : elles porteraient un nom que le catalogue n'offre
/// plus, le critère ne serait pas posé, et la vue filtrerait moins large - le défaut de #3093, provoqué
/// cette fois par nous.
///
/// [CritereFiltre#nomsHerites()] évite la migration de base : le critère déclare les noms qu'il a
/// portés, et la restauration les accepte.
@ExtendWith(ApplicationExtension.class)
class NomsHeritesTest {

    private static final String ANCIENNE = "statut";
    private static final String NOUVELLE = "statut_workflow";

    /// Un critère renommé, qui déclare son ancien nom.
    private static CritereFiltre<String> critereRenomme() {
        return new CritereFiltre<String>() {
            @Override
            public String nom() {
                return NOUVELLE;
            }

            @Override
            public List<String> nomsHerites() {
                return List.of(ANCIENNE);
            }

            @Override
            public String libelle() {
                return "Statut";
            }

            @Override
            public javafx.scene.Node editeur(
                    java.util.function.Consumer<java.util.function.Predicate<String>> applique) {
                applique.accept(ligne -> ligne.startsWith("D"));
                return new TextField();
            }

            @Override
            public List<String> valeurCourante(javafx.scene.Node editeur) {
                return List.of();
            }

            @Override
            public List<String> restaurerValeurs(javafx.scene.Node editeur, List<String> valeurs) {
                return List.of();
            }
        };
    }

    private static GestionnaireFiltres<String> gestionnaire(FilteredList<String> affichees) {
        return new GestionnaireFiltres<>(
                new TextField(),
                new MenuButton(),
                new FlowPane(),
                new Filtres<>(affichees, () -> {}),
                List.of(critereRenomme()),
                (ligne, aiguille) -> ligne.contains(aiguille));
    }

    @Test
    @DisplayName("#3096 : une vue enregistrée sous l'ANCIENNE clé se rejoue encore")
    void une_vue_sous_l_ancienne_cle_se_rejoue() {
        ObservableList<String> source = FXCollections.observableArrayList("Déposé", "Importé");
        FilteredList<String> affichees = new FilteredList<>(source);
        GestionnaireFiltres<String> gestionnaire = gestionnaire(affichees);

        ResteDeRestauration reste =
                gestionnaire.restaurer(new DescripteurFiltre("", List.of(new DescripteurCritere(ANCIENNE, List.of()))));

        assertThat(reste.criteresInconnus())
                .as("l'ancienne clé est reconnue : sans cela la vue serait annoncée amputée à tort")
                .isEmpty();
        assertThat(affichees)
                .as("et le critère est réellement posé, pas seulement accepté")
                .containsExactly("Déposé");
    }

    @Test
    @DisplayName("#3096 : la NOUVELLE clé fonctionne évidemment aussi")
    void la_nouvelle_cle_fonctionne() {
        ObservableList<String> source = FXCollections.observableArrayList("Déposé", "Importé");
        FilteredList<String> affichees = new FilteredList<>(source);
        GestionnaireFiltres<String> gestionnaire = gestionnaire(affichees);

        ResteDeRestauration reste =
                gestionnaire.restaurer(new DescripteurFiltre("", List.of(new DescripteurCritere(NOUVELLE, List.of()))));

        assertThat(reste.criteresInconnus()).isEmpty();
        assertThat(affichees).containsExactly("Déposé");
    }

    @Test
    @DisplayName("#3096 : une clé qui n'est ni l'ancienne ni la nouvelle reste inconnue")
    void une_cle_etrangere_reste_inconnue() {
        // Le pendant : `nomsHerites` ne doit pas devenir un fourre-tout qui accepterait tout et
        // rendrait le compte rendu de #3093 muet.
        ObservableList<String> source = FXCollections.observableArrayList("Déposé", "Importé");
        FilteredList<String> affichees = new FilteredList<>(source);
        GestionnaireFiltres<String> gestionnaire = gestionnaire(affichees);

        ResteDeRestauration reste =
                gestionnaire.restaurer(new DescripteurFiltre("", List.of(new DescripteurCritere("proba", List.of()))));

        assertThat(reste.criteresInconnus()).containsExactly("proba");
        assertThat(affichees).hasSize(2);
    }
}

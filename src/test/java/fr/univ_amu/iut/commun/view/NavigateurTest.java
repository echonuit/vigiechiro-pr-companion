package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Tests unitaires du [Navigateur] (socle) : mémorisation de l'accueil et retour à celui-ci.
/// [ApplicationExtension] initialise le toolkit JavaFX (construction de nœuds) ; aucune scène
/// n'est affichée.
@ExtendWith(ApplicationExtension.class)
class NavigateurTest {

    @Start
    void start(Stage stage) {
        // Toolkit JavaFX initialisé ; aucune scène nécessaire pour ces tests.
    }

    @Test
    @DisplayName("afficherAccueil revient à la vue mémorisée et réinitialise le fil d'Ariane")
    void afficherAccueil_revient_a_l_accueil() {
        NavigationViewModel navigation = new NavigationViewModel();
        Navigateur navigateur = new Navigateur(navigation);
        Parent accueil = new Group();
        navigateur.memoriserAccueil(accueil);
        navigateur.afficher(new Group(), "sites", "Mes sites de suivi");

        navigateur.afficherAccueil();

        assertThat(navigateur.getVueCentrale()).isSameAs(accueil);
        assertThat(navigation.filArianeProperty().get()).isEqualTo("Accueil");
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("accueil");
    }

    @Test
    @DisplayName("afficherAccueil est sans effet tant qu'aucun accueil n'a été mémorisé")
    void afficherAccueil_sans_memorisation_est_neutre() {
        Navigateur navigateur = new Navigateur(new NavigationViewModel());
        Parent vue = new Group();
        navigateur.afficher(vue);

        navigateur.afficherAccueil();

        assertThat(navigateur.getVueCentrale()).isSameAs(vue);
    }

    @Test
    @DisplayName("#54 : afficherAccueil est neutralisé tant que la navigation est verrouillée")
    void afficherAccueil_neutralise_si_navigation_verrouillee() {
        NavigationViewModel navigation = new NavigationViewModel();
        Navigateur navigateur = new Navigateur(navigation);
        navigateur.memoriserAccueil(new Group());
        Parent ecranFeature = new Group();
        navigateur.afficher(ecranFeature, "import", "Importer une nuit");

        navigation.setNavigationVerrouillee(true);
        navigateur.afficherAccueil(); // doit être ignoré : opération en cours

        assertThat(navigateur.getVueCentrale()).isSameAs(ecranFeature);
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("import");

        navigation.setNavigationVerrouillee(false);
        navigateur.afficherAccueil(); // une fois déverrouillé, le retour fonctionne

        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("accueil");
    }
}

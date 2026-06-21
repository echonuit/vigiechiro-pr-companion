package fr.univ_amu.iut.validation.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de la feature `validation` : charge l'écran **M-Vision-Tadarida** pour un
/// passage et l'affiche dans la zone centrale du chrome.
///
/// Même patron que [fr.univ_amu.iut.diagnostic.view.NavigationDiagnostic] : le [FXMLLoader] reçoit
/// la `controllerFactory` Guice, si bien que [ValidationController] obtient son ViewModel par
/// injection. Fournit le contrat socle [OuvrirValidation] (bindé par `ValidationModule`) : le
/// `view` de `passage` (M-Passage) l'injecte sans dépendre de cette feature.
@Singleton
public class NavigationValidation implements OuvrirValidation {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationValidation(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche la validation Tadarida du passage `passage` dans la zone centrale du chrome (le contexte
    /// alimente le fil d'Ariane).
    @Override
    public void ouvrir(ContextePassage passage) {
        FXMLLoader loader = new FXMLLoader(NavigationValidation.class.getResource("Validation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            ValidationController controleur = loader.getController();
            controleur.ouvrirSur(passage);
            navigateur.empiler(vue, "validation", "Validation Tadarida", controleur);
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirActivite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de l'écran **Activité de la nuit** (#2352) : charge M-Activite pour un passage et
/// l'affiche dans la zone centrale du chrome.
///
/// Même patron que [NavigationDiagnostic] : le [FXMLLoader] reçoit la `controllerFactory` Guice, si bien
/// que [ActiviteController] obtient son ViewModel par injection. Fournit le contrat socle
/// [OuvrirActivite] (bindé par `ActiviteModule`), que le `view` de `passage` (M-Passage) injecte sans
/// dépendre de cette feature.
@Singleton
public class NavigationActivite implements OuvrirActivite {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationActivite(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche l'activité du passage `passage` dans la zone centrale du chrome (le contexte alimente le
    /// fil d'Ariane).
    @Override
    public void ouvrir(ContextePassage passage) {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationActivite.class, "Activite.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            ActiviteController controleur = loader.getController();
            controleur.ouvrirSur(passage);
            navigateur.empiler(vue, "activite", "Activité de la nuit", controleur);
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirSynthese;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de l'écran **Synthèse de la nuit** (#2351) : charge M-Synthese pour un passage
/// et l'affiche dans la zone centrale du chrome, empilée sur le passage.
///
/// Même patron que [NavigationActivite] : le [FXMLLoader] reçoit la `controllerFactory` Guice, si bien
/// que [SyntheseController] obtient son ViewModel par injection.
@Singleton
public class NavigationSynthese implements OuvrirSynthese {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationSynthese(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    @Override
    public void ouvrir(ContextePassage passage) {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationSynthese.class, "Synthese.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            SyntheseController controleur = loader.getController();
            controleur.ouvrirSur(passage);
            navigateur.empiler(vue, "synthese", "Synthèse de la nuit", controleur);
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

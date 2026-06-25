package fr.univ_amu.iut.importation.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirImportation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de la feature `importation` : charge la vue **M-Import** et l'affiche dans
/// la zone centrale du chrome.
///
/// Même patron que [fr.univ_amu.iut.sites.view.NavigationSites] : le [FXMLLoader] reçoit la
/// `controllerFactory` branchée sur Guice (`injector::getInstance`), si bien que
/// [ImportationController] obtient son ViewModel par injection. Dépend du socle [Navigateur]
/// (`commun.view`), dépendance autorisée car `commun` est le socle partagé (pas une autre feature).
@Singleton
public class NavigationImportation implements OuvrirImportation {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationImportation(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche l'assistant « Importer une nuit » dans la zone centrale du chrome (ouverture globale,
    /// déclenchée par la carte d'accueil).
    public void ouvrir() {
        afficher(null);
    }

    /// Ouvre l'assistant avec le site `idSite` pré-sélectionné dans le rattachement (raccourci depuis
    /// la fiche d'un site).
    @Override
    public void ouvrirPourSite(Long idSite) {
        afficher(idSite);
    }

    private void afficher(Long idSitePreselectionne) {
        FXMLLoader loader = new FXMLLoader(NavigationImportation.class.getResource("Importation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            ImportationController controleur = loader.getController();
            if (idSitePreselectionne != null) {
                controleur.preselectionnerSite(idSitePreselectionne);
            }
            navigateur.ouvrirRacine(vue, "import", "Importer une nuit", controleur);
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

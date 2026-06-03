package fr.univ_amu.iut.bibliotheque.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.Navigateur;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de la feature `bibliotheque` : charge la vue FXML et la publie dans la zone
/// centrale du chrome via le [Navigateur] du socle.
///
/// Même patron que [fr.univ_amu.iut.sites.view.NavigationSites] : seul point de la feature qui sait
/// charger un FXML, avec la `controllerFactory` branchée sur Guice (`injector::getInstance`) pour
/// que [BibliothequeController] reçoive son ViewModel par injection. L'écran n'a pas de paramètre :
/// le controller déclenche lui-même le chargement des données en `initialize()`.
@Singleton
public class NavigationBibliotheque {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationBibliotheque(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche l'écran **M-Bibliotheque** (bibliothèque de sons de référence) dans la zone centrale.
    public void ouvrirAccueil() {
        FXMLLoader loader = new FXMLLoader(NavigationBibliotheque.class.getResource("Bibliotheque.fxml"));
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            navigateur.afficher(vue, "bibliotheque", "Bibliothèque de sons");
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

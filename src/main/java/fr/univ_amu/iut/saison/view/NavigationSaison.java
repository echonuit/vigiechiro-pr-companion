package fr.univ_amu.iut.saison.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Navigateur;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de la feature `saison` : charge la vue **M-Saison** et la publie dans la zone
/// centrale du chrome via le [Navigateur] du socle.
///
/// Même patron que [fr.univ_amu.iut.multisite.view.NavigationMultisite] : seul point de la feature qui
/// sait charger un FXML, avec la `controllerFactory` branchée sur Guice (`injector::getInstance`) pour
/// que [SaisonController] reçoive son ViewModel et les contrats d'ouverture par injection. L'écran est
/// **sans paramètre** : il est ouvert depuis la carte d'accueil, et le controller déclenche lui-même le
/// chargement des données en `initialize()`. Aucun contrat `Ouvrir*` : aucune autre feature n'ouvre
/// « Ma saison ».
@Singleton
public class NavigationSaison {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationSaison(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche l'écran **M-Saison** (solde de la saison) dans la zone centrale du chrome, en tête
    /// d'historique (`ouvrirRacine` : on y arrive depuis l'accueil).
    public void ouvrir() {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationSaison.class, "Saison.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            SaisonController controleur = loader.getController();
            navigateur.ouvrirRacine(vue, "saison", "Ma saison", controleur);
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

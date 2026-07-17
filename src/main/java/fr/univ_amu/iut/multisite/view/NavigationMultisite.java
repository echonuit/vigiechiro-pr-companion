package fr.univ_amu.iut.multisite.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Modales;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/// Façade de navigation de la feature `multisite` : charge la vue FXML et la publie dans la zone
/// centrale du chrome via le [Navigateur] du socle.
///
/// Même patron que [fr.univ_amu.iut.sites.view.NavigationSites] : seul point de la feature qui sait
/// charger un FXML, avec la `controllerFactory` branchée sur Guice (`injector::getInstance`) pour
/// que [MultisiteController] reçoive son ViewModel et le contrat [fr.univ_amu.iut.commun.view.OuvrirPassage]
/// par injection. L'écran n'a pas de paramètre : le controller déclenche lui-même le chargement
/// des données en `initialize()`.
@Singleton
public class NavigationMultisite implements OuvrirMultisite {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationMultisite(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche l'écran **M-Multisite** (vue agrégée des passages) dans la zone centrale du chrome.
    public void ouvrirAccueil() {
        publier();
    }

    /// Ouvre la vue multi-sites et **focalise** la carte sur le carré `numeroCarre` (« voir sur la carte »).
    @Override
    public void ouvrirSurCarre(String numeroCarre) {
        publier().focaliserSur(numeroCarre);
    }

    /// Ouvre la vue multi-sites et **focalise** la carte sur un **point précis** (carré + GPS, #154).
    @Override
    public void ouvrirSurPoint(String numeroCarre, double latitude, double longitude) {
        publier().focaliserSurPoint(numeroCarre, latitude, longitude);
    }

    /// Ouvre la vue multi-sites, focalise sur le carré et **active l'édition** pour *placer* un point sans GPS.
    @Override
    public void ouvrirSurCarrePourPlacer(String numeroCarre) {
        publier().focaliserSurCarrePourPlacer(numeroCarre);
    }

    /// Ouvre la modale **« Reconstruire un passage manquant »** (#1396) au-dessus de la vue multi-sites.
    /// `apresSucces` n'est exécuté qu'**après une reconstruction** : la table se recharge, et la nuit
    /// rapatriée y apparaît.
    public void ouvrirModaleReconstruction(Window parent, Runnable apresSucces) {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationMultisite.class, "ReconstructionModale.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            ReconstructionModaleController controleur = loader.getController();
            controleur.demarrer(apresSucces);
            Stage modale = new Stage();
            modale.initOwner(parent);
            modale.initModality(Modality.WINDOW_MODAL);
            modale.setTitle("Reconstruire un passage manquant");
            modale.setScene(new Scene(vue));
            Modales.fermerParEchap(modale);
            // Rafraîchir l'écran appelant à TOUTE fermeture (bouton « Fermer », croix, Échap), et non au seul
            // bouton : sinon fermer par la croix laissait la table périmée, sans la nuit reconstruite (#1647).
            modale.setOnHidden(evenement -> controleur.rafraichirSiReconstruit());
            modale.show();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }

    /// Charge `Multisite.fxml`, le publie dans la zone centrale et renvoie son controller.
    private MultisiteController publier() {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationMultisite.class, "Multisite.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            MultisiteController controleur = loader.getController();
            navigateur.ouvrirRacine(vue, "multisite", "Carte & passages", controleur);
            return controleur;
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

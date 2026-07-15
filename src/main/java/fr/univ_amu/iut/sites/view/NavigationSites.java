package fr.univ_amu.iut.sites.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Modales;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/// Façade de navigation de la feature `sites` : centralise le chargement des vues FXML et
/// l'enchaînement des écrans.
///
/// C'est le **seul** point de la feature qui sait charger un FXML : chaque [FXMLLoader] reçoit la
/// `controllerFactory` branchée sur Guice (`injector::getInstance`), exactement comme le bootstrap
/// [fr.univ_amu.iut.App] le fait pour le chrome. Les controllers obtiennent ainsi leurs ViewModels
/// par injection, sans connaître l'injecteur.
///
/// La navigation entre vues principales (accueil ↔ détail) passe par le service [Navigateur] du
/// socle (échange de la zone centrale du chrome). L'édition d'un point s'ouvre dans un [Stage]
/// modal distinct. C'est cette classe qui rend la feature `sites` dépendante du socle `commun.view`
/// (dépendance autorisée : `commun` est le socle partagé, pas une autre feature).
@Singleton
public class NavigationSites implements OuvrirSite {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationSites(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche l'écran d'accueil **M-Sites** dans la zone centrale du chrome.
    public void ouvrirAccueil() {
        FXMLLoader loader = charger("MesSites.fxml");
        Parent vue = lire(loader);
        navigateur.ouvrirRacine(vue, "sites", "Mes sites", loader.getController());
    }

    /// Affiche l'écran de détail **M-Site-detail** du site donné (clic sur une carte).
    public void ouvrirDetail(Site site) {
        Objects.requireNonNull(site, "site");
        FXMLLoader loader = charger("SiteDetail.fxml");
        Parent vue = lire(loader);
        SiteDetailController controller = loader.getController();
        controller.afficher(site);
        navigateur.empiler(vue, "site-detail", "Carré " + site.numeroCarre(), controller);
    }

    // ----- Contrat socle OuvrirSite (segments « Mes sites » / « Carré N » cliquables d'un autre fil) -----

    /// {@inheritDoc} Ouvre la liste « Mes sites » (= [#ouvrirAccueil]).
    @Override
    public void ouvrirListe() {
        ouvrirAccueil();
    }

    /// {@inheritDoc} Résout le site par son numéro de carré (utilisateur courant) puis ouvre son détail.
    @Override
    public void ouvrirDetail(String numeroCarre) {
        String idUtilisateur = injector.getInstance(Key.get(String.class, Names.named("idUtilisateurCourant")));
        injector.getInstance(ServiceSites.class).listerSites(idUtilisateur).stream()
                .filter(site -> site.numeroCarre().equals(numeroCarre))
                .findFirst()
                .ifPresent(this::ouvrirDetail);
    }

    /// Ouvre la modale d'**ajout** d'un point d'écoute pour `site`.
    ///
    /// @param parent fenêtre propriétaire (pour la modalité)
    /// @param site site auquel rattacher le nouveau point
    /// @param apresSucces action exécutée après un ajout réussi (typiquement rafraîchir le détail)
    public void ouvrirModaleCreationPoint(Window parent, Site site, Runnable apresSucces) {
        FXMLLoader loader = charger("ModalePoint.fxml");
        Parent vue = lire(loader);
        ModalePointController controller = loader.getController();
        controller.demarrerCreation(site, apresSucces);
        afficherModale(parent, vue, "Point d'écoute");
    }

    /// Ouvre la modale d'**édition** du point `point` (champs pré-remplis).
    public void ouvrirModaleEditionPoint(Window parent, Site site, PointDEcoute point, Runnable apresSucces) {
        FXMLLoader loader = charger("ModalePoint.fxml");
        Parent vue = lire(loader);
        ModalePointController controller = loader.getController();
        controller.demarrerEdition(site, point, apresSucces);
        afficherModale(parent, vue, "Point d'écoute");
    }

    /// Ouvre la modale de **déclaration** d'un site (#1431), en remplacement du `Dialog` bâti à la main
    /// dans `MesSitesController` : le geste devient jouable dans un test, et sa validation aussi.
    ///
    /// @param parent fenêtre propriétaire (pour la modalité)
    /// @param apresSucces action exécutée après une déclaration réussie (rafraîchir la liste)
    public void ouvrirModaleCreationSite(Window parent, Runnable apresSucces) {
        FXMLLoader loader = charger("ModaleSite.fxml");
        Parent vue = lire(loader);
        ModaleSiteController controller = loader.getController();
        controller.demarrerCreation(apresSucces);
        afficherModale(parent, vue, "Site de suivi");
    }

    /// Ouvre la modale d'**édition** du site `site` (champs pré-remplis).
    public void ouvrirModaleEditionSite(Window parent, Site site, Runnable apresSucces) {
        FXMLLoader loader = charger("ModaleSite.fxml");
        Parent vue = lire(loader);
        ModaleSiteController controller = loader.getController();
        controller.demarrerEdition(site, apresSucces);
        afficherModale(parent, vue, "Site de suivi");
    }

    private void afficherModale(Window parent, Parent vue, String titreFenetre) {
        Stage modale = new Stage();
        modale.initOwner(parent);
        modale.initModality(Modality.WINDOW_MODAL);
        modale.setTitle(titreFenetre);
        modale.setScene(new Scene(vue));
        Modales.fermerParEchap(modale);
        modale.show();
    }

    private FXMLLoader charger(String fxml) {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationSites.class, fxml);
        loader.setControllerFactory(injector::getInstance);
        return loader;
    }

    private static Parent lire(FXMLLoader loader) {
        try {
            return loader.load();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.PreferenceSourceEspece;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import java.util.function.Supplier;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.stage.Window;

/// Câblage du menu ☰ (outils) du chrome : sauvegarde/restauration de la base (#148), purge des
/// originaux importés, connexion VigieChiro (#741) et préférence de source des fiches espèces (#849).
/// Extrait de [MainController] pour l'y garder focalisé sur le chrome (SRP), en regroupant les actions
/// [ActionsSauvegarde] / [ActionsPurge] déjà existantes et l'entrée de connexion.
///
/// L'entrée « Connexion » affiche l'état courant (via [OuvrirConnexion#libelleMenu()]) : son libellé
/// est réévalué **à chaque ouverture du menu** ([MenuButton#setOnShowing]), sans appel réseau, pour
/// refléter une (dé)connexion faite entre-temps dans la modale.
final class MenuOutils {

    private final ActionsSauvegarde sauvegarde;
    private final ActionsPurge purge;
    private final OuvrirConnexion connexion;

    MenuOutils(
            MenuButton menu,
            MenuItem itemConnexion,
            CheckMenuItem itemSourceWikipedia,
            ServiceSauvegarde serviceSauvegarde,
            ServicePurgeOriginaux servicePurge,
            OuvrirConnexion connexion,
            PreferenceSourceEspece preferenceSourceEspece,
            Supplier<Window> fenetre,
            Runnable retourAccueil) {
        // Après une restauration/purge réussie, retour à l'accueil : un écran ouvert lirait sinon un état
        // périmé de la base restaurée / des volumes purgés.
        this.sauvegarde = new ActionsSauvegarde(serviceSauvegarde, fenetre, retourAccueil);
        this.purge = new ActionsPurge(servicePurge, fenetre, retourAccueil);
        this.connexion = connexion;
        itemConnexion.setText(connexion.libelleMenu());
        menu.setOnShowing(evenement -> itemConnexion.setText(connexion.libelleMenu()));
        // Préférence source des fiches espèces (#849) : état initial depuis le réglage persisté, puis
        // mémorisation à chaque bascule (effet immédiat sur les prochaines fiches ouvertes).
        itemSourceWikipedia.setSelected(preferenceSourceEspece.prefereWikipedia());
        itemSourceWikipedia
                .selectedProperty()
                .addListener((obs, avant, apres) -> preferenceSourceEspece.definirPrefereWikipedia(apres));
    }

    void sauvegarder() {
        sauvegarde.sauvegarder();
    }

    void restaurer() {
        sauvegarde.restaurer();
    }

    void purger() {
        purge.purger();
    }

    void ouvrirConnexion() {
        connexion.ouvrir();
    }
}

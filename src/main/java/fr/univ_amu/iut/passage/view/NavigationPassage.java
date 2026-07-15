package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Modales;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/// Façade de navigation de la feature `passage` : charge l'écran pivot **M-Passage** pour un
/// passage donné et l'affiche dans la zone centrale du chrome.
///
/// Même patron que [fr.univ_amu.iut.qualification.view.NavigationQualification] : le [FXMLLoader]
/// reçoit la `controllerFactory` branchée sur Guice, si bien que [PassageController] obtient son
/// ViewModel par injection. La vue est ensuite ouverte sur le passage avec son [ContexteSite]
/// (carré/code/nom fournis par l'écran appelant, M-Site-detail, pour éviter une dépendance
/// `passage → sites`). Dépend du socle [Navigateur] (`commun.view`).
@Singleton
public class NavigationPassage implements OuvrirPassage {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationPassage(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche le détail du passage `idPassage` (avec son contexte site) dans la zone centrale.
    @Override
    public void ouvrir(Long idPassage, ContexteSite contexte) {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationPassage.class, "Passage.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            PassageController controleur = loader.getController();
            controleur.ouvrirSur(idPassage, contexte);
            navigateur.empiler(vue, "passage", controleur.libelleFil(), controleur);
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }

    /// Revient à l'accueil global du chrome (cartes des features). Utilisé après une action qui
    /// détruit l'écran courant (ex. suppression du passage). Délègue au socle [Navigateur].
    public void ouvrirAccueil() {
        navigateur.afficherAccueil();
    }

    /// Actualise le libellé de l'étape de navigation du passage (fil d'Ariane, bouton ← Retour) une
    /// fois son chargement **asynchrone** terminé (#1213) : l'écran est empilé avant que son numéro ne
    /// soit connu. Sans effet si l'écran n'est pas (ou plus) au sommet de l'historique.
    void actualiserFil(PassageController controleur, String libelle) {
        navigateur.actualiserLibelleCourant(controleur, libelle);
    }

    /// Ouvre la modale **« Modifier le passage »** (E2.S8) dans une fenêtre modale appartenant à
    /// `parent` : rattachement (année + n°) **et** conditions de dépôt (météo, matériel du micro). Le
    /// carré et le code point (inchangés) sont fournis par M-Passage. Après une modification réussie,
    /// `apresSucces` est exécuté (rafraîchir l'écran appelant).
    public void ouvrirModaleRattachement(
            Window parent, Long idPassage, String carre, String codePoint, Runnable apresSucces) {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationPassage.class, "RattachementModale.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            RattachementModaleController controleur = loader.getController();
            controleur.demarrer(idPassage, carre, codePoint, apresSucces);
            Stage modale = new Stage();
            modale.initOwner(parent);
            modale.initModality(Modality.WINDOW_MODAL);
            modale.setTitle("Modifier le passage");
            modale.setScene(new Scene(vue));
            Modales.fermerParEchap(modale);
            modale.show();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

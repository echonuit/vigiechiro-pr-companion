package fr.univ_amu.iut.qualification.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.Modales;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/// Façade de navigation de la feature `qualification` : charge la vue **M-Qualification** pour un
/// passage donné et l'affiche dans la zone centrale du chrome.
///
/// Même patron que [fr.univ_amu.iut.importation.view.NavigationImportation] : le [FXMLLoader]
/// reçoit la `controllerFactory` branchée sur Guice (`injector::getInstance`), si bien que
/// [QualificationController] obtient ses deux ViewModel par injection. La vue est ensuite ouverte
/// sur le passage `idPassage` (fourni par l'écran appelant, M-Passage). Dépend du socle
/// [Navigateur] (`commun.view`), dépendance autorisée car `commun` est le socle partagé.
///
/// Fournit le contrat socle [OuvrirVerification] (bindé par `QualificationModule`) : l'écran
/// M-Passage l'injecte sans dépendre de la feature `qualification` (pas de cycle).
@Singleton
public class NavigationQualification implements OuvrirVerification {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationQualification(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche l'écran « Vérifier l'enregistrement » pour le passage `passage` dans la zone centrale du
    /// chrome (les deux ViewModel sont ouverts sur ce passage ; le contexte alimente le fil d'Ariane).
    @Override
    public void ouvrir(ContextePassage passage) {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationQualification.class, "Qualification.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            QualificationController controleur = loader.getController();
            controleur.ouvrirSur(passage);
            navigateur.empiler(vue, "qualification", "Vérifier l'enregistrement", controleur);
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }

    /// Ouvre la modale **« Personnaliser la sélection d'écoute »** (R12, #1431), en remplacement du
    /// `Dialog` bâti à la main dans [QualificationController] : le geste - qui **efface la progression
    /// d'écoute** - devient jouable dans un test.
    ///
    /// La sélection est celle de l'écran APPELANT, qui la passe. Prise de l'injecteur elle serait
    /// **neuve** - ce modèle est non-singleton - et la modale écrirait dans un orphelin sans que
    /// l'écran n'en voie rien (#4734). Les autres modales reçoivent déjà leur objet ; celle-ci était
    /// la seule à ne rien recevoir.
    ///
    /// @param parent fenêtre propriétaire (pour la modalité)
    /// @param selection la sélection d'écoute de l'écran appelant, celle que la modale doit modifier
    public void ouvrirModaleSelection(Window parent, SelectionEcouteViewModel selection) {
        Objects.requireNonNull(selection, "selection");
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationQualification.class, "ModaleSelection.fxml");
        loader.setControllerFactory(type -> type == ModaleSelectionController.class
                ? new ModaleSelectionController(selection)
                : injector.getInstance(type));
        try {
            Parent vue = loader.load();
            ((ModaleSelectionController) loader.getController()).demarrer();
            Stage modale = new Stage();
            modale.initOwner(parent);
            Modales.centrerSur(modale, parent);
            modale.initModality(Modality.WINDOW_MODAL);
            modale.setTitle("Sélection d'écoute");
            modale.setScene(Habillage.scene(vue));
            Modales.fermerParEchap(modale);
            modale.show();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

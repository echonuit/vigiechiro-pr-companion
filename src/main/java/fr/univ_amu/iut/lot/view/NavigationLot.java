package fr.univ_amu.iut.lot.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de la feature `lot` : charge l'écran **M-Lot** pour un passage et l'affiche
/// dans la zone centrale du chrome.
///
/// Même patron que [fr.univ_amu.iut.validation.view.NavigationValidation] : le [FXMLLoader] reçoit
/// la `controllerFactory` Guice, si bien que [LotController] obtient son ViewModel par injection.
/// Fournit le contrat socle [OuvrirLot] (bindé par `LotModule`) : le `view` de `passage` (M-Passage)
/// l'injecte sans dépendre de cette feature.
@Singleton
public class NavigationLot implements OuvrirLot {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationLot(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche la préparation et le dépôt du lot du passage `idPassage` dans la zone centrale.
    @Override
    public void ouvrir(Long idPassage) {
        FXMLLoader loader = new FXMLLoader(NavigationLot.class.getResource("Lot.fxml"));
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            LotController controleur = loader.getController();
            controleur.ouvrirSur(idPassage);
            navigateur.empiler(vue, "lot", "Préparer le dépôt", controleur);
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

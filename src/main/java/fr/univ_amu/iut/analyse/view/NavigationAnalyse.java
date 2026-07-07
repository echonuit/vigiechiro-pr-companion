package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de la feature `analyse` : charge `Analyse.fxml` et le publie dans la zone
/// centrale du chrome via le [Navigateur] du socle. Même patron que [NavigationMultisite] : seul point
/// de la feature qui charge un FXML, avec la `controllerFactory` branchée sur Guice pour que
/// [AnalyseController] reçoive son ViewModel. L'écran n'a pas de paramètre (chargement initial dans
/// `initialize()`).
@Singleton
public class NavigationAnalyse implements OuvrirAnalyse {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationAnalyse(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche l'écran **« Espèces & observations »** dans la zone centrale du chrome, puis rejoue les
    /// `filtres` transportés (le cas échéant) et bascule sur la carte si `afficherCarte`. Les filtres sont
    /// appliqués **après** publication de la vue, une fois ses contrôles attachés à la scène.
    public void ouvrir(DescripteurFiltre filtres, boolean afficherCarte) {
        FXMLLoader loader = new FXMLLoader(NavigationAnalyse.class.getResource("Analyse.fxml"));
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            AnalyseController controleur = loader.getController();
            navigateur.ouvrirRacine(vue, "analyse", "Espèces & observations", controleur);
            controleur.appliquer(filtres, afficherCarte);
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

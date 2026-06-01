package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.passage.viewmodel.ContexteSite;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de la feature `passage` : charge l'écran pivot **M-Passage** pour un
/// passage donné et l'affiche dans la zone centrale du chrome.
///
/// Même patron que [fr.univ_amu.iut.qualification.view.NavigationQualification] : le [FXMLLoader]
/// reçoit la `controllerFactory` branchée sur Guice, si bien que [PassageController] obtient son
/// ViewModel par injection. La vue est ensuite ouverte sur le passage avec son [ContexteSite]
/// (carré/code/nom fournis par l'écran appelant, M-Site-detail, pour éviter une dépendance
/// `passage → sites`). Dépend du socle [Navigateur] (`commun.view`).
@Singleton
public class NavigationPassage {

  private final Injector injector;
  private final Navigateur navigateur;

  @Inject
  public NavigationPassage(Injector injector, Navigateur navigateur) {
    this.injector = Objects.requireNonNull(injector, "injector");
    this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
  }

  /// Affiche le détail du passage `idPassage` (avec son contexte site) dans la zone centrale.
  public void ouvrir(Long idPassage, ContexteSite contexte) {
    FXMLLoader loader = new FXMLLoader(NavigationPassage.class.getResource("Passage.fxml"));
    loader.setControllerFactory(injector::getInstance);
    try {
      Parent vue = loader.load();
      PassageController controleur = loader.getController();
      controleur.ouvrirSur(idPassage, contexte);
      navigateur.afficher(vue, "passage", "Détail du passage");
    } catch (IOException echec) {
      throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
    }
  }
}

package fr.univ_amu.iut.importation.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.Navigateur;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de la feature `importation` : charge la vue **M-Import** et l'affiche dans
/// la zone centrale du chrome.
///
/// Même patron que [fr.univ_amu.iut.sites.view.NavigationSites] : le [FXMLLoader] reçoit la
/// `controllerFactory` branchée sur Guice (`injector::getInstance`), si bien que
/// [ImportationController] obtient son ViewModel par injection. Dépend du socle [Navigateur]
/// (`commun.view`), dépendance autorisée car `commun` est le socle partagé (pas une autre feature).
@Singleton
public class NavigationImportation {

  private final Injector injector;
  private final Navigateur navigateur;

  @Inject
  public NavigationImportation(Injector injector, Navigateur navigateur) {
    this.injector = Objects.requireNonNull(injector, "injector");
    this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
  }

  /// Affiche l'assistant « Importer une nuit » dans la zone centrale du chrome.
  public void ouvrir() {
    FXMLLoader loader = new FXMLLoader(NavigationImportation.class.getResource("Importation.fxml"));
    loader.setControllerFactory(injector::getInstance);
    try {
      Parent vue = loader.load();
      navigateur.afficher(vue, "import", "Importer une nuit");
    } catch (IOException echec) {
      throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
    }
  }
}

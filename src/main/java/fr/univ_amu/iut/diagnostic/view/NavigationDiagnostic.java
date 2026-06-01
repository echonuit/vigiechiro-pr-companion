package fr.univ_amu.iut.diagnostic.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de la feature `diagnostic` : charge l'écran **M-Diagnostic** pour un
/// passage et l'affiche dans la zone centrale du chrome.
///
/// Même patron que [fr.univ_amu.iut.qualification.view.NavigationQualification] : le [FXMLLoader]
/// reçoit la `controllerFactory` Guice, si bien que [DiagnosticController] obtient son ViewModel
/// par injection. Fournit le contrat socle [OuvrirDiagnostic] (bindé par `DiagnosticModule`) : le
/// `view` de `passage` (M-Passage) l'injecte sans dépendre de cette feature.
@Singleton
public class NavigationDiagnostic implements OuvrirDiagnostic {

  private final Injector injector;
  private final Navigateur navigateur;

  @Inject
  public NavigationDiagnostic(Injector injector, Navigateur navigateur) {
    this.injector = Objects.requireNonNull(injector, "injector");
    this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
  }

  /// Affiche le diagnostic matériel du passage `idPassage` dans la zone centrale du chrome.
  @Override
  public void ouvrir(Long idPassage) {
    FXMLLoader loader = new FXMLLoader(NavigationDiagnostic.class.getResource("Diagnostic.fxml"));
    loader.setControllerFactory(injector::getInstance);
    try {
      Parent vue = loader.load();
      DiagnosticController controleur = loader.getController();
      controleur.ouvrirSur(idPassage);
      navigateur.afficher(vue, "diagnostic", "Diagnostic matériel");
    } catch (IOException echec) {
      throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
    }
  }
}

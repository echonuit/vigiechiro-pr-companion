package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Parent;

/// Service de navigation du socle : pilote l'échange de la zone centrale du chrome principal.
///
/// Singleton Guice partagé par tout l'applicatif. Il n'a **pas** de référence directe au
/// `BorderPane` : il expose une propriété observable [#vueCentraleProperty()] que le
/// [MainController] lie au `centerProperty()` de la racine. Changer la vue centrale revient
/// donc à publier une nouvelle valeur dans cette propriété, ce qui reste testable sans IHM.
///
/// Pour l'instant le service gère uniquement le swap du centre. Les features l'utiliseront
/// (`afficher(...)`) pour prendre la main sur la zone centrale ; le chargement d'un FXML par
/// feature s'ajoutera ici quand les features arriveront.
@Singleton
public class Navigateur {

  private final NavigationViewModel navigation;
  private final ObjectProperty<Parent> vueCentrale =
      new SimpleObjectProperty<>(this, "vueCentrale");
  private Parent accueil;

  @Inject
  public Navigateur(NavigationViewModel navigation) {
    this.navigation = navigation;
  }

  /// Propriété observable de la vue centrale courante. Le [MainController] y lie le centre
  /// du `BorderPane` : toute publication d'une nouvelle valeur remplace l'affichage central.
  public ObjectProperty<Parent> vueCentraleProperty() {
    return vueCentrale;
  }

  public Parent getVueCentrale() {
    return vueCentrale.get();
  }

  /// Affiche `vue` dans la zone centrale du chrome principal (remplace la vue précédente).
  public void afficher(Parent vue) {
    vueCentrale.set(vue);
  }

  /// Affiche `vue` et synchronise le fil d'Ariane du [NavigationViewModel] en une étape.
  /// Pratique pour les features : un seul appel met à jour le contenu et le chrome.
  public void afficher(Parent vue, String vueCourante, String libelleFilAriane) {
    navigation.naviguerVers(vueCourante, libelleFilAriane);
    vueCentrale.set(vue);
  }

  /// Mémorise la vue d'accueil (zone centrale initiale du chrome), pour pouvoir y revenir ensuite
  /// via [#afficherAccueil]. Appelée une fois par le [MainController] au démarrage.
  public void memoriserAccueil(Parent accueil) {
    this.accueil = accueil;
  }

  /// Réaffiche l'accueil global (cartes des features) et réinitialise le fil d'Ariane à son état de
  /// départ. Permet à une feature de « revenir à l'accueil » sans dépendre d'une autre feature (ex.
  /// après suppression de l'écran courant). Sans effet si l'accueil n'a pas encore été mémorisé.
  public void afficherAccueil() {
    if (accueil != null) {
      afficher(accueil, "accueil", "Accueil");
    }
  }
}

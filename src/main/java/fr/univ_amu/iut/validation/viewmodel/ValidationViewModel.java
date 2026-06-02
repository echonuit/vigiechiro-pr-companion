package fr.univ_amu.iut.validation.viewmodel;

import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ObservationStatut;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.VueValidation;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran **M-Vision-Tadarida** (validation taxonomique des résultats Tadarida d'un
/// passage, parcours P7).
///
/// Ouvert sur un `idPassage`, il lit [ServiceValidation#chargerValidation(Long)] et expose la liste
/// des observations (avec leur statut de revue), la sélection courante et son détail, ainsi que les
/// compteurs de progression (validées / corrigées / total). VM agnostique de l'IHM (règle ArchUnit
/// `viewmodel_sans_javafx_ui`) : seuls `javafx.beans`/`javafx.collections`. Non-singleton.
///
/// La revue proprement dite (valider / corriger, R15/R16) sera branchée dans un incrément ultérieur
/// (PR-V4) ; cet incrément couvre la lecture : liste, sélection, détail, compteurs.
public class ValidationViewModel {

  /// Affichage des valeurs optionnelles absentes (probabilité, taxon observateur non saisi).
  private static final String NON_RENSEIGNE = "non renseigné";

  private final ServiceValidation service;

  /// Identifiant du jeu de résultats courant (`identification_results`), `null` si aucun import.
  /// Conservé pour les actions d'export et de revue des incréments suivants.
  private Long idResultats;

  private final ObservableList<ObservationStatut> observations =
      FXCollections.observableArrayList();
  private final ObjectProperty<ObservationStatut> selection =
      new SimpleObjectProperty<>(this, "selection");

  private final ReadOnlyIntegerWrapper nombreTotal =
      new ReadOnlyIntegerWrapper(this, "nombreTotal", 0);
  private final ReadOnlyIntegerWrapper nombreValidees =
      new ReadOnlyIntegerWrapper(this, "nombreValidees", 0);
  private final ReadOnlyIntegerWrapper nombreCorrigees =
      new ReadOnlyIntegerWrapper(this, "nombreCorrigees", 0);
  private final ReadOnlyStringWrapper progression =
      new ReadOnlyStringWrapper(this, "progression", "");

  private final ReadOnlyStringWrapper detail = new ReadOnlyStringWrapper(this, "detail", "");
  private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

  public ValidationViewModel(ServiceValidation service) {
    this.service = Objects.requireNonNull(service, "service");
    selection.addListener((obs, ancien, nouveau) -> majDetail(nouveau));
  }

  /// Ouvre la validation du passage `idPassage`. Une erreur (passage/résultats illisibles) est
  /// restituée dans [#messageProperty()] sans lever, l'écran restant vide. Un passage sans CSV
  /// importé n'est pas une erreur : la liste est vide et un message d'état neutre l'explique.
  public void ouvrirSur(Long idPassage) {
    reinitialiser();
    try {
      appliquer(service.chargerValidation(idPassage));
    } catch (RuntimeException echec) {
      reinitialiser();
      message.set(echec.getMessage());
    }
  }

  private void appliquer(VueValidation vue) {
    idResultats = vue.idResultats();
    observations.setAll(vue.observations());
    majCompteurs();
    message.set(
        vue.observations().isEmpty() ? "Aucun résultat Tadarida importé pour ce passage." : "");
  }

  private void majCompteurs() {
    int validees = compter(StatutObservation.VALIDEE);
    int corrigees = compter(StatutObservation.CORRIGEE);
    int total = observations.size();
    nombreTotal.set(total);
    nombreValidees.set(validees);
    nombreCorrigees.set(corrigees);
    progression.set(total == 0 ? "" : (validees + corrigees) + " / " + total + " revues");
  }

  private int compter(StatutObservation statut) {
    return (int) observations.stream().filter(o -> o.statut() == statut).count();
  }

  private void majDetail(ObservationStatut courant) {
    if (courant == null) {
      detail.set("");
      return;
    }
    Observation o = courant.observation();
    detail.set(
        "Tadarida : "
            + o.taxonTadarida()
            + " ("
            + proba(o.probTadarida())
            + ")\nObservateur : "
            + valeurOuAbsente(o.taxonObservateur())
            + " ("
            + proba(o.probObservateur())
            + ")\nFréquence médiane : "
            + frequence(o.frequenceMedianeHz())
            + "\nStatut : "
            + libelle(courant.statut()));
  }

  private void reinitialiser() {
    idResultats = null;
    selection.set(null);
    observations.clear();
    detail.set("");
    nombreTotal.set(0);
    nombreValidees.set(0);
    nombreCorrigees.set(0);
    progression.set("");
    message.set("");
  }

  private static String proba(Double probabilite) {
    return probabilite == null ? NON_RENSEIGNE : Math.round(probabilite * 100) + " %";
  }

  private static String valeurOuAbsente(String code) {
    return code == null || code.isBlank() ? NON_RENSEIGNE : code;
  }

  private static String frequence(Integer hz) {
    return hz == null ? NON_RENSEIGNE : hz + " Hz";
  }

  /// Libellé d'affichage du statut de revue d'une observation.
  static String libelle(StatutObservation statut) {
    return switch (statut) {
      case NON_TOUCHEE -> "À revoir";
      case VALIDEE -> "Validée";
      case CORRIGEE -> "Corrigée";
    };
  }

  /// Observations du passage (avec statut de revue), dans l'ordre d'import.
  public ObservableList<ObservationStatut> observations() {
    return observations;
  }

  /// Observation sélectionnée dans la liste (liée au modèle de sélection de la table par la vue).
  public ObjectProperty<ObservationStatut> selectionProperty() {
    return selection;
  }

  /// Détail multi-ligne de l'observation sélectionnée, vide quand aucune n'est sélectionnée.
  public ReadOnlyStringProperty detailProperty() {
    return detail.getReadOnlyProperty();
  }

  /// Nombre total d'observations du passage.
  public ReadOnlyIntegerProperty nombreTotalProperty() {
    return nombreTotal.getReadOnlyProperty();
  }

  /// Nombre d'observations validées (R15 : taxon observateur = taxon Tadarida).
  public ReadOnlyIntegerProperty nombreValideesProperty() {
    return nombreValidees.getReadOnlyProperty();
  }

  /// Nombre d'observations corrigées (R16 : taxon observateur différent de Tadarida).
  public ReadOnlyIntegerProperty nombreCorrigeesProperty() {
    return nombreCorrigees.getReadOnlyProperty();
  }

  /// Avancement de la revue (`N / T revues`), vide tant qu'aucune observation n'est chargée.
  public ReadOnlyStringProperty progressionProperty() {
    return progression.getReadOnlyProperty();
  }

  /// Message d'état (erreur de chargement, ou absence de résultats importés), vide en nominal.
  public ReadOnlyStringProperty messageProperty() {
    return message.getReadOnlyProperty();
  }

  /// Identifiant du jeu de résultats Tadarida chargé, `null` si aucun import pour ce passage.
  public Long idResultats() {
    return idResultats;
  }
}

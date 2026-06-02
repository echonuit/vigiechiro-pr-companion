package fr.univ_amu.iut.validation.viewmodel;

import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ObservationStatut;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.VueValidation;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
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
/// La revue est portée par [#valider()] (R15) et [#corriger(Taxon)] (R16) : chaque action
/// délègue au service, puis recharge la vue. L'export `_Vu` et l'import CSV restent à part.
public class ValidationViewModel {

  /// Affichage des valeurs optionnelles absentes (probabilité, taxon observateur non saisi).
  private static final String NON_RENSEIGNE = "non renseigné";

  private final ServiceValidation service;

  /// Passage courant, conservé pour recharger la vue après une action de revue (valider/corriger).
  private Long idPassage;

  /// Identifiant du jeu de résultats courant (`identification_results`), `null` si aucun import.
  /// Conservé pour les actions d'export et de revue des incréments suivants.
  private Long idResultats;

  private final ObservableList<ObservationStatut> observations =
      FXCollections.observableArrayList();
  private final ObservableList<Taxon> taxons = FXCollections.observableArrayList();
  private final ObjectProperty<ObservationStatut> selection =
      new SimpleObjectProperty<>(this, "selection");
  private final ReadOnlyBooleanWrapper selectionPresente =
      new ReadOnlyBooleanWrapper(this, "selectionPresente", false);

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
    selection.addListener((obs, ancien, nouveau) -> majSelection(nouveau));
  }

  /// Ouvre la validation du passage `idPassage`. Une erreur (passage/résultats illisibles) est
  /// restituée dans [#messageProperty()] sans lever, l'écran restant vide. Un passage sans CSV
  /// importé n'est pas une erreur : la liste est vide et un message d'état neutre l'explique.
  public void ouvrirSur(Long idPassage) {
    this.idPassage = idPassage;
    reinitialiser();
    try {
      taxons.setAll(service.taxonsDisponibles());
      appliquer(service.chargerValidation(idPassage));
    } catch (RuntimeException echec) {
      reinitialiser();
      message.set(echec.getMessage());
    }
  }

  /// Valide l'observation sélectionnée (R15 : retient la proposition Tadarida), puis recharge.
  /// Sans sélection, l'appel est ignoré. Une erreur métier est restituée dans [#messageProperty()].
  ///
  /// @return `true` si la validation a été appliquée
  public boolean valider() {
    ObservationStatut courant = selection.get();
    if (courant == null || courant.observation().id() == null) {
      return false;
    }
    return appliquerAction(() -> service.valider(courant.observation().id()));
  }

  /// Corrige l'observation sélectionnée (R16 : retient le `taxon` de l'observateur, distinct de
  /// Tadarida) puis recharge la vue. Sans sélection ni taxon, l'appel est ignoré.
  ///
  /// Corriger vers la proposition Tadarida elle-même est refusé : ce serait une **validation**, pas
  /// une correction (le service la reclasserait `NON_TOUCHEE`, laissant la ligne « À revoir »
  /// malgré une saisie manuelle). On invite alors à utiliser [#valider()].
  ///
  /// @param taxon taxon retenu par l'observateur
  /// @return `true` si la correction a été appliquée
  public boolean corriger(Taxon taxon) {
    ObservationStatut courant = selection.get();
    if (courant == null || courant.observation().id() == null || taxon == null) {
      return false;
    }
    if (taxon.code().equals(courant.observation().taxonTadarida())) {
      message.set(
          "Pour retenir la proposition Tadarida, utilisez « Valider » : corriger attend"
              + " un autre taxon.");
      return false;
    }
    return appliquerAction(() -> service.corriger(courant.observation().id(), taxon.code(), null));
  }

  private boolean appliquerAction(Runnable action) {
    try {
      action.run();
      appliquer(service.chargerValidation(idPassage));
      return true;
    } catch (RuntimeException echec) {
      message.set(echec.getMessage());
      return false;
    }
  }

  private void appliquer(VueValidation vue) {
    idResultats = vue.idResultats();
    observations.setAll(vue.observations());
    majCompteurs();
    message.set(messageEtat(vue));
  }

  /// État neutre de l'écran : distingue l'absence d'import (`idResultats == null`) d'un CSV
  /// effectivement importé mais sans aucune détection (en-tête seul) ; vide en présence
  /// d'observations.
  private static String messageEtat(VueValidation vue) {
    if (vue.idResultats() == null) {
      return "Aucun résultat Tadarida importé pour ce passage.";
    }
    if (vue.observations().isEmpty()) {
      return "Résultats Tadarida importés, mais aucune détection à valider.";
    }
    return "";
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

  private void majSelection(ObservationStatut courant) {
    selectionPresente.set(courant != null);
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
            + libelleStatut(courant.statut()));
  }

  private void reinitialiser() {
    idResultats = null;
    selection.set(null);
    observations.clear();
    taxons.clear();
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

  /// Libellé d'affichage du statut de revue d'une observation. Source unique partagée par le détail
  /// (ce VM) et la colonne « Statut » de la table (le controller de la vue).
  public static String libelleStatut(StatutObservation statut) {
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

  /// `true` dès qu'une observation est sélectionnée (activation des boutons valider/corriger).
  public ReadOnlyBooleanProperty selectionPresenteProperty() {
    return selectionPresente.getReadOnlyProperty();
  }

  /// Taxons connus en base, pour le sélecteur de correction (R16).
  public ObservableList<Taxon> taxons() {
    return taxons;
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

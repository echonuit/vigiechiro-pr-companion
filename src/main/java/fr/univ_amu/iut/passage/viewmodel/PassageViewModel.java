package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran pivot **M-Passage** : fiche d'identité d'un passage, **stepper** de statut
/// workflow et statistiques (volumes, durée audible, nombre de séquences).
///
/// Ouvert sur un `idPassage` + un [ContexteSite] (carré/code/nom fournis par la navigation, pour
/// éviter une dépendance `passage → sites`). Le calcul passe par la projection
/// [ServicePassage#detailPassage(Long)]. VM agnostique de l'IHM (règle ArchUnit
/// `viewmodel_sans_javafx_ui`) : seuls `javafx.beans`/`javafx.collections`. Non-singleton.
public class PassageViewModel {

  private final ServicePassage service;

  private final ReadOnlyStringWrapper titreContexte =
      new ReadOnlyStringWrapper(this, "titreContexte", "");
  private final ReadOnlyStringWrapper plageHoraire =
      new ReadOnlyStringWrapper(this, "plageHoraire", "");
  private final ReadOnlyStringWrapper enregistreur =
      new ReadOnlyStringWrapper(this, "enregistreur", "");
  private final ReadOnlyObjectWrapper<StatutWorkflow> statut =
      new ReadOnlyObjectWrapper<>(this, "statut");
  private final ReadOnlyObjectWrapper<Verdict> verdict =
      new ReadOnlyObjectWrapper<>(this, "verdict");
  private final ReadOnlyStringWrapper volumeBruts =
      new ReadOnlyStringWrapper(this, "volumeBruts", "");
  private final ReadOnlyStringWrapper volumeTransformes =
      new ReadOnlyStringWrapper(this, "volumeTransformes", "");
  private final ReadOnlyStringWrapper dureeAudible =
      new ReadOnlyStringWrapper(this, "dureeAudible", "");
  private final ReadOnlyIntegerWrapper nombreSequences =
      new ReadOnlyIntegerWrapper(this, "nombreSequences", 0);
  private final ObservableList<EtapeWorkflow> etapes = FXCollections.observableArrayList();
  private final ReadOnlyBooleanWrapper verificationDisponible =
      new ReadOnlyBooleanWrapper(this, "verificationDisponible", false);
  private final ReadOnlyBooleanWrapper validationVerrouillee =
      new ReadOnlyBooleanWrapper(this, "validationVerrouillee", true);
  private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

  public PassageViewModel(ServicePassage service) {
    this.service = Objects.requireNonNull(service, "service");
  }

  /// Ouvre l'écran sur le passage `idPassage`, avec le contexte site fourni par la navigation.
  /// Une erreur (passage introuvable) est restituée dans [#messageProperty()] sans lever.
  public void ouvrirSur(Long idPassage, ContexteSite contexte) {
    reinitialiser();
    try {
      appliquer(service.detailPassage(idPassage), contexte);
      message.set("");
    } catch (RuntimeException echec) {
      reinitialiser();
      message.set(echec.getMessage());
    }
  }

  private void appliquer(DetailPassage detail, ContexteSite contexte) {
    titreContexte.set(
        "Carré "
            + contexte.numeroCarre()
            + " / "
            + contexte.codePoint()
            + " / N° "
            + detail.numeroPassage()
            + " ("
            + detail.annee()
            + ")");
    plageHoraire.set(
        detail.dateEnregistrement() + "  " + detail.heureDebut() + " → " + detail.heureFin());
    enregistreur.set("PR " + detail.idEnregistreur());
    statut.set(detail.statut());
    verdict.set(detail.verdict());
    volumeBruts.set(formatOctets(detail.volumeOriginauxOctets()));
    volumeTransformes.set(formatOctets(detail.volumeSequencesOctets()));
    dureeAudible.set(formatDuree(detail.dureeAudibleSecondes()));
    nombreSequences.set(detail.nombreSequences());
    etapes.setAll(construireEtapes(detail.statut()));
    verificationDisponible.set(detail.statut().ordinal() >= StatutWorkflow.TRANSFORME.ordinal());
    validationVerrouillee.set(detail.statut() != StatutWorkflow.DEPOSE);
  }

  private void reinitialiser() {
    titreContexte.set("");
    plageHoraire.set("");
    enregistreur.set("");
    statut.set(null);
    verdict.set(null);
    volumeBruts.set("");
    volumeTransformes.set("");
    dureeAudible.set("");
    nombreSequences.set(0);
    etapes.clear();
    verificationDisponible.set(false);
    validationVerrouillee.set(true);
  }

  private static List<EtapeWorkflow> construireEtapes(StatutWorkflow courant) {
    List<EtapeWorkflow> liste = new ArrayList<>();
    for (StatutWorkflow etape : StatutWorkflow.values()) {
      EtatEtape etat;
      if (etape.ordinal() < courant.ordinal()) {
        etat = EtatEtape.FRANCHIE;
      } else if (etape == courant) {
        etat = EtatEtape.COURANTE;
      } else {
        etat = EtatEtape.A_VENIR;
      }
      liste.add(new EtapeWorkflow(etape, etat));
    }
    return liste;
  }

  private static String formatOctets(long octets) {
    long valeur = Math.max(0, octets);
    if (valeur >= 1_073_741_824L) {
      return String.format(Locale.FRANCE, "%.1f Go", valeur / 1_073_741_824.0);
    }
    if (valeur >= 1_048_576L) {
      return String.format(Locale.FRANCE, "%.0f Mo", valeur / 1_048_576.0);
    }
    return String.format(Locale.FRANCE, "%d Ko", valeur / 1024);
  }

  private static String formatDuree(double secondes) {
    long total = Math.round(secondes);
    long heures = total / 3600;
    long minutes = (total % 3600) / 60;
    return heures > 0 ? heures + " h " + minutes + " min" : minutes + " min " + (total % 60) + " s";
  }

  /// Titre d'identité du passage (`Carré 640380 / A1 / N° 2 (2026)`).
  public ReadOnlyStringProperty titreContexteProperty() {
    return titreContexte.getReadOnlyProperty();
  }

  /// Plage horaire de la nuit (`date  début → fin`).
  public ReadOnlyStringProperty plageHoraireProperty() {
    return plageHoraire.getReadOnlyProperty();
  }

  /// Enregistreur (`PR <n° de série>`).
  public ReadOnlyStringProperty enregistreurProperty() {
    return enregistreur.getReadOnlyProperty();
  }

  /// Statut workflow courant du passage.
  public ReadOnlyObjectProperty<StatutWorkflow> statutProperty() {
    return statut.getReadOnlyProperty();
  }

  /// Verdict de vérification, ou `null` tant qu'aucun n'est posé.
  public ReadOnlyObjectProperty<Verdict> verdictProperty() {
    return verdict.getReadOnlyProperty();
  }

  /// Volume des enregistrements bruts, formaté (`Ko`/`Mo`/`Go`).
  public ReadOnlyStringProperty volumeBrutsProperty() {
    return volumeBruts.getReadOnlyProperty();
  }

  /// Volume des séquences transformées, formaté.
  public ReadOnlyStringProperty volumeTransformesProperty() {
    return volumeTransformes.getReadOnlyProperty();
  }

  /// Durée audible cumulée, formatée (`Xh Ymin` ou `X min Y s`).
  public ReadOnlyStringProperty dureeAudibleProperty() {
    return dureeAudible.getReadOnlyProperty();
  }

  /// Nombre de séquences d'écoute de la session.
  public ReadOnlyIntegerProperty nombreSequencesProperty() {
    return nombreSequences.getReadOnlyProperty();
  }

  /// Étapes du stepper de statut (5 statuts, du plus ancien au dépôt), avec leur état.
  public ObservableList<EtapeWorkflow> etapes() {
    return etapes;
  }

  /// `true` si la vérification par échantillonnage est possible (passage au moins transformé).
  public ReadOnlyBooleanProperty verificationDisponibleProperty() {
    return verificationDisponible.getReadOnlyProperty();
  }

  /// `true` tant que la validation Tadarida est verrouillée (passage non encore déposé).
  public ReadOnlyBooleanProperty validationVerrouilleeProperty() {
    return validationVerrouillee.getReadOnlyProperty();
  }

  /// Message d'erreur (passage introuvable), vide en fonctionnement nominal.
  public ReadOnlyStringProperty messageProperty() {
    return message.getReadOnlyProperty();
  }
}

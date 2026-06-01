package fr.univ_amu.iut.qualification.viewmodel;

import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.qualification.model.ContexteVerification;
import fr.univ_amu.iut.qualification.model.GenerateurSelection;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de la **sélection d'écoute** de l'écran M-Qualification (vérification par
/// échantillonnage, P3).
///
/// Porte l'identité de la nuit affichée dans le bandeau (carré/point/passage, plage horaire,
/// volumétrie), la **liste de la sélection** échantillonnée (séquences retenues, R12), la
/// progression d'écoute (R10) et les paramètres de (re)génération (méthode + taille, R12). Le
/// verdict est porté à part par [QualificationViewModel] : le controller câble les deux sur le
/// même `idPassage`.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls `javafx.beans` et
/// `javafx.collections` sont importés, jamais `javafx.scene`. Non-singleton (un VM frais par FXML).
public class SelectionEcouteViewModel {

  private final ServiceQualification service;
  private Long idPassage;
  private Long idSelection;

  // Bandeau identité de la nuit (lecture seule, dérivé de ContexteVerification).
  private final ReadOnlyStringWrapper titreContexte =
      new ReadOnlyStringWrapper(this, "titreContexte", "");
  private final ReadOnlyStringWrapper plageHoraire =
      new ReadOnlyStringWrapper(this, "plageHoraire", "");
  private final ReadOnlyStringWrapper volumetrie =
      new ReadOnlyStringWrapper(this, "volumetrie", "");

  // Liste de la sélection + progression d'écoute + séquence courante.
  private final ObservableList<SequenceEnSelection> lignes = FXCollections.observableArrayList();
  private final ObjectProperty<SequenceEnSelection> sequenceCourante =
      new SimpleObjectProperty<>(this, "sequenceCourante");
  private final ReadOnlyObjectWrapper<Path> cheminSequenceCourante =
      new ReadOnlyObjectWrapper<>(this, "cheminSequenceCourante");
  private final ReadOnlyDoubleWrapper progression =
      new ReadOnlyDoubleWrapper(this, "progression", 0.0);
  private final ReadOnlyStringWrapper progressionTexte =
      new ReadOnlyStringWrapper(this, "progressionTexte", "");
  private final ObjectProperty<MethodeSelection> methode =
      new SimpleObjectProperty<>(this, "methode", MethodeSelection.REPARTITION_TEMPORELLE);
  private final IntegerProperty taille =
      new SimpleIntegerProperty(this, "taille", GenerateurSelection.TAILLE_DEFAUT);

  private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

  public SelectionEcouteViewModel(ServiceQualification service) {
    this.service = Objects.requireNonNull(service, "service");
    sequenceCourante.addListener((obs, ancien, nouveau) -> majCheminCourant(nouveau));
  }

  /// Ouvre la sélection d'écoute du passage `idPassage` : bandeau de contexte et liste de la
  /// sélection (constituée à la volée si absente, R12). Appelée par la navigation après le
  /// chargement du FXML. Une erreur (passage introuvable, sans séquence) est restituée dans
  /// [#messageProperty()] sans lever.
  public void ouvrirSur(Long idPassage) {
    this.idPassage = idPassage;
    try {
      appliquerContexte(service.chargerContexte(idPassage));
      SelectionDEcoute selection = service.ouvrirVerification(idPassage);
      this.idSelection = selection.id();
      lignes.setAll(service.detaillerSelection(idSelection));
      recalculerProgression();
      message.set("");
    } catch (RuntimeException echec) {
      message.set(echec.getMessage());
    }
  }

  /// Sélectionne une ligne de la liste (met à jour le chemin du fichier courant pour l'écoute).
  public void selectionner(SequenceEnSelection ligne) {
    sequenceCourante.set(ligne);
  }

  /// Marque la séquence courante comme écoutée (flag `listened`). Appelée au début de la lecture
  /// (R10). Sans effet si aucune séquence n'est sélectionnée ou si elle est déjà écoutée.
  public void marquerCouranteEcoutee() {
    SequenceEnSelection courante = sequenceCourante.get();
    if (courante == null || idSelection == null || courante.ecoutee()) {
      return;
    }
    service.marquerSequenceEcoutee(idSelection, courante.sequence().id());
    int index = lignes.indexOf(courante);
    if (index >= 0) {
      SequenceEnSelection ecoutee =
          new SequenceEnSelection(courante.sequence(), courante.position(), true);
      lignes.set(index, ecoutee);
      sequenceCourante.set(ecoutee);
    }
    recalculerProgression();
  }

  /// Régénère la sélection avec la méthode et la taille choisies (R12). Recharge la liste et remet
  /// la progression à zéro. Erreur restituée dans le message.
  public void regenerer() {
    try {
      SelectionDEcoute selection = service.creerSelection(idPassage, methode.get(), taille.get());
      this.idSelection = selection.id();
      lignes.setAll(service.detaillerSelection(idSelection));
      sequenceCourante.set(null);
      recalculerProgression();
      message.set("");
    } catch (RuntimeException echec) {
      message.set(echec.getMessage());
    }
  }

  private void appliquerContexte(ContexteVerification contexte) {
    titreContexte.set(
        "Carré "
            + contexte.numeroCarre()
            + " / "
            + contexte.codePoint()
            + " / N° "
            + contexte.numeroPassage()
            + " ("
            + contexte.annee()
            + ")");
    plageHoraire.set(contexte.date() + "  " + contexte.heureDebut() + " → " + contexte.heureFin());
    volumetrie.set(
        contexte.sequencesTotales()
            + " séquences · durée audible "
            + formatDuree(contexte.dureeAudibleSecondes()));
  }

  private void majCheminCourant(SequenceEnSelection ligne) {
    String chemin = ligne == null ? null : ligne.sequence().cheminFichier();
    cheminSequenceCourante.set(chemin == null ? null : Path.of(chemin));
  }

  private void recalculerProgression() {
    int total = lignes.size();
    long ecoutees = lignes.stream().filter(SequenceEnSelection::ecoutee).count();
    progression.set(total == 0 ? 0.0 : (double) ecoutees / total);
    progressionTexte.set(
        total == 0
            ? "Aucune séquence"
            : ecoutees
                + " / "
                + total
                + " écoutées ("
                + Math.round(progression.get() * 100)
                + " %)");
  }

  private static String formatDuree(double secondes) {
    long total = Math.round(secondes);
    long heures = total / 3600;
    long minutes = (total % 3600) / 60;
    return heures > 0 ? heures + " h " + minutes + " min" : minutes + " min " + (total % 60) + " s";
  }

  /// Titre de contexte du bandeau (ex. `Carré 640380 / A1 / N° 2 (2026)`).
  public ReadOnlyStringProperty titreContexteProperty() {
    return titreContexte.getReadOnlyProperty();
  }

  /// Plage horaire de la nuit (`date  début → fin`).
  public ReadOnlyStringProperty plageHoraireProperty() {
    return plageHoraire.getReadOnlyProperty();
  }

  /// Volumétrie de la nuit (`N séquences · durée audible Xh Ymin`).
  public ReadOnlyStringProperty volumetrieProperty() {
    return volumetrie.getReadOnlyProperty();
  }

  /// Liste observable de la sélection d'écoute (séquences retenues, ordonnées par position).
  public ObservableList<SequenceEnSelection> lignes() {
    return lignes;
  }

  /// Séquence sélectionnée dans la liste (écoute en cours).
  public ObjectProperty<SequenceEnSelection> sequenceCouranteProperty() {
    return sequenceCourante;
  }

  /// Chemin du fichier WAV de la séquence courante (pour le composant audio), `null` si aucune.
  public ReadOnlyObjectProperty<Path> cheminSequenceCouranteProperty() {
    return cheminSequenceCourante.getReadOnlyProperty();
  }

  /// Progression d'écoute de la sélection, de 0 à 1 (pilote la barre).
  public ReadOnlyDoubleProperty progressionProperty() {
    return progression.getReadOnlyProperty();
  }

  /// Libellé de progression (`12 / 30 écoutées (40 %)`).
  public ReadOnlyStringProperty progressionTexteProperty() {
    return progressionTexte.getReadOnlyProperty();
  }

  /// Méthode d'échantillonnage choisie pour la (re)génération (R12).
  public ObjectProperty<MethodeSelection> methodeProperty() {
    return methode;
  }

  /// Taille de sélection choisie pour la (re)génération.
  public IntegerProperty tailleProperty() {
    return taille;
  }

  /// Message d'erreur (passage introuvable, sans séquence), vide en fonctionnement nominal.
  public ReadOnlyStringProperty messageProperty() {
    return message.getReadOnlyProperty();
  }
}

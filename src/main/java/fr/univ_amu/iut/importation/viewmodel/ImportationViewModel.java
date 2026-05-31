package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.RapportInspection;
import fr.univ_amu.iut.importation.model.ServiceImport;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;

/// ViewModel de l'assistant **M-Import** (« Importer une nuit »).
///
/// Cette première tranche couvre les **étapes 1 et 2** de la maquette : choisir un dossier source
/// puis l'**inspecter en lecture seule** (R9 : aucun fichier n'est modifié). L'inspection délègue
/// à [ServiceImport#inspecter] et expose le [RapportInspection] sous forme de propriétés
/// observables dérivées, auxquelles la vue se lie. Le rattachement (site/point/préfixe) et
/// l'exécution de l'import viendront dans les tranches suivantes de la feature.
///
/// Conformément à la règle ArchUnit `viewmodel_sans_javafx_ui`, seul `javafx.beans` est importé ici
/// (jamais `javafx.scene`/`javafx.fxml`/`javafx.stage`).
public class ImportationViewModel {

  private final ServiceImport service;

  /// Étape 1 : dossier source choisi (carte SD ou copie disque), modifiable par la vue (champ +
  /// bouton « Parcourir »).
  private final ObjectProperty<Path> dossierSource =
      new SimpleObjectProperty<>(this, "dossierSource");

  /// Étape 2 : résultat d'inspection, exposé en propriétés dérivées (lecture seule pour la vue).
  private final ReadOnlyBooleanWrapper inspecte =
      new ReadOnlyBooleanWrapper(this, "inspecte", false);
  private final ReadOnlyBooleanWrapper aUnJournal =
      new ReadOnlyBooleanWrapper(this, "aUnJournal", false);
  private final ReadOnlyBooleanWrapper aUnReleveClimatique =
      new ReadOnlyBooleanWrapper(this, "aUnReleveClimatique", false);
  private final ReadOnlyIntegerWrapper nombreOriginaux =
      new ReadOnlyIntegerWrapper(this, "nombreOriginaux", 0);
  private final ReadOnlyObjectWrapper<EtatNommage> etatNommage =
      new ReadOnlyObjectWrapper<>(this, "etatNommage", null);
  private final ReadOnlyStringWrapper resumeJournal =
      new ReadOnlyStringWrapper(this, "resumeJournal", "");
  private final ReadOnlyStringWrapper messageErreur =
      new ReadOnlyStringWrapper(this, "messageErreur", "");

  public ImportationViewModel(ServiceImport service) {
    this.service = Objects.requireNonNull(service, "service");
  }

  /// Dossier source à inspecter puis importer (lié au champ + bouton « Parcourir » de la vue).
  public ObjectProperty<Path> dossierSourceProperty() {
    return dossierSource;
  }

  /// `true` dès qu'une inspection a réussi (pilote l'affichage de la section « Inspection »).
  public ReadOnlyBooleanProperty inspecteProperty() {
    return inspecte.getReadOnlyProperty();
  }

  public boolean estInspecte() {
    return inspecte.get();
  }

  /// `true` si un journal du capteur (LogPR) a été détecté dans le dossier.
  public ReadOnlyBooleanProperty aUnJournalProperty() {
    return aUnJournal.getReadOnlyProperty();
  }

  /// `true` si un relevé climatique (THLog) est présent (R20 : son absence est signalée).
  public ReadOnlyBooleanProperty aUnReleveClimatiqueProperty() {
    return aUnReleveClimatique.getReadOnlyProperty();
  }

  /// Nombre d'enregistrements originaux (WAV) détectés dans le dossier.
  public ReadOnlyIntegerProperty nombreOriginauxProperty() {
    return nombreOriginaux.getReadOnlyProperty();
  }

  /// État du nommage des fichiers (`BRUT`, `PREFIXE`, `VIDE`) : pilotera le scénario de renommage.
  public ReadOnlyObjectProperty<EtatNommage> etatNommageProperty() {
    return etatNommage.getReadOnlyProperty();
  }

  /// Résumé lisible du journal détecté (ex. `PR n° 1925492`), vide si aucun journal.
  public ReadOnlyStringProperty resumeJournalProperty() {
    return resumeJournal.getReadOnlyProperty();
  }

  /// Message d'erreur d'inspection (ex. chemin invalide), vide si l'inspection a réussi.
  public ReadOnlyStringProperty messageErreurProperty() {
    return messageErreur.getReadOnlyProperty();
  }

  /// Inspecte le dossier source courant **en lecture seule** (R9) et met à jour les propriétés
  /// d'inspection. Sur un dossier non choisi ou un chemin invalide, renseigne
  /// [#messageErreurProperty()] et laisse `inspecte` à `false`.
  public void inspecter() {
    Path dossier = dossierSource.get();
    if (dossier == null) {
      echouer("Choisissez d'abord un dossier source.");
      return;
    }
    try {
      RapportInspection rapport = service.inspecter(dossier);
      aUnJournal.set(rapport.aUnJournal());
      aUnReleveClimatique.set(rapport.aUnReleveClimatique());
      nombreOriginaux.set(rapport.nombreOriginaux());
      etatNommage.set(rapport.etatNommage());
      resumeJournal.set(
          rapport.journalOptionnel().map(journal -> "PR n° " + journal.numeroSerie()).orElse(""));
      messageErreur.set("");
      inspecte.set(true);
    } catch (RuntimeException echec) {
      echouer(echec.getMessage());
    }
  }

  private void echouer(String message) {
    // Réinitialise tout l'état d'inspection : sinon, après une inspection réussie, un échec
    // ultérieur laisserait les propriétés dérivées sur les valeurs (obsolètes) de l'ancien dossier.
    inspecte.set(false);
    aUnJournal.set(false);
    aUnReleveClimatique.set(false);
    nombreOriginaux.set(0);
    etatNommage.set(null);
    resumeJournal.set("");
    messageErreur.set(message);
  }
}

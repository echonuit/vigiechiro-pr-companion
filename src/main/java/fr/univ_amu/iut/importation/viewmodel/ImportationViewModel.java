package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.RapportInspection;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'assistant **M-Import** (« Importer une nuit »).
///
/// Couvre les **étapes 1 à 4** de la maquette :
///  1. choisir un dossier source ;
///  2. l'**inspecter en lecture seule** (R9) via [ServiceImport#inspecter] ;
///  3. **rattacher** la nuit à un site / point / année / n° de passage et prévisualiser le préfixe
///     Vigie-Chiro qui sera appliqué (R6) ;
///  4. **lancer l'import** via [ServiceImport#importer], puis exposer le résultat et l'état.
///
/// [#importer()] est **synchrone** : la vue le lancera sur un fil d'arrière-plan pour ne pas
/// figer l'IHM (progression à venir). Les sites/points de l'utilisateur courant viennent de
/// [ServiceSites] : une dépendance
/// `importation → sites` sur le `model` d'une autre feature (autorisée par ArchUnit, jamais
/// sur son `view`/`viewmodel`). Seul `javafx.beans`/`javafx.collections` est importé ici,
/// jamais `javafx.scene` (règle `viewmodel_sans_javafx_ui`).
public class ImportationViewModel {

  private final ServiceImport serviceImport;
  private final ServiceSites serviceSites;
  private final String idUtilisateur;

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

  /// Étape 3 : rattachement de la nuit (site / point / année / n° de passage).
  private final ObservableList<Site> sites = FXCollections.observableArrayList();
  private final ObjectProperty<Site> siteSelectionne =
      new SimpleObjectProperty<>(this, "siteSelectionne");
  private final ObservableList<PointDEcoute> points = FXCollections.observableArrayList();
  private final ObjectProperty<PointDEcoute> pointSelectionne =
      new SimpleObjectProperty<>(this, "pointSelectionne");
  private final IntegerProperty annee = new SimpleIntegerProperty(this, "annee");
  private final IntegerProperty numeroPassage = new SimpleIntegerProperty(this, "numeroPassage", 1);
  private final ReadOnlyStringWrapper apercuPrefixe =
      new ReadOnlyStringWrapper(this, "apercuPrefixe", "");
  private final BooleanBinding peutImporter;

  /// Rapport d'inspection courant, conservé pour l'aperçu du préfixe (exemple de nom d'origine) et
  /// les tranches suivantes (extraction du quadruplet, exécution de l'import).
  private RapportInspection rapport;

  /// Étape 4 : exécution de l'import (état + résultat), pilotée par [#importer()].
  private final ReadOnlyObjectWrapper<EtatImport> etat =
      new ReadOnlyObjectWrapper<>(this, "etat", EtatImport.PRET);
  private final ReadOnlyObjectWrapper<ResultatImport> resultat =
      new ReadOnlyObjectWrapper<>(this, "resultat", null);

  public ImportationViewModel(
      ServiceImport serviceImport,
      ServiceSites serviceSites,
      Horloge horloge,
      String idUtilisateur) {
    this.serviceImport = Objects.requireNonNull(serviceImport, "serviceImport");
    this.serviceSites = Objects.requireNonNull(serviceSites, "serviceSites");
    this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
    Objects.requireNonNull(horloge, "horloge");

    // Valeur initiale avant d'installer les écouteurs (évite un recalcul d'aperçu prématuré).
    annee.set(horloge.aujourdhui().getYear());

    // Changer de dossier source invalide l'inspection précédente : un nouveau dossier doit être
    // ré-inspecté (sinon le bouton Importer resterait actif et l'aperçu garderait l'ancien
    // rapport).
    dossierSource.addListener((obs, ancien, nouveau) -> reinitialiserInspection());

    // Changer de site recharge ses points et réinitialise le point sélectionné.
    siteSelectionne.addListener(
        (obs, ancien, nouveau) -> {
          points.setAll(nouveau == null ? List.of() : serviceSites.listerPoints(nouveau.id()));
          pointSelectionne.set(null);
          majApercu();
        });
    pointSelectionne.addListener((obs, ancien, nouveau) -> majApercu());
    annee.addListener((obs, ancien, nouveau) -> majApercu());
    numeroPassage.addListener((obs, ancien, nouveau) -> majApercu());

    peutImporter =
        Bindings.createBooleanBinding(
            () ->
                inspecte.get()
                    && siteSelectionne.get() != null
                    && pointSelectionne.get() != null
                    && numeroPassage.get() >= 1,
            inspecte,
            siteSelectionne,
            pointSelectionne,
            numeroPassage);
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

  /// Liste observable des sites de l'utilisateur, alimentée par [#chargerSites()] (combobox Site).
  public ObservableList<Site> sites() {
    return sites;
  }

  /// Site auquel rattacher la nuit (sélection dans la combobox).
  public ObjectProperty<Site> siteSelectionneProperty() {
    return siteSelectionne;
  }

  /// Points du site sélectionné (recalculée à chaque changement de site).
  public ObservableList<PointDEcoute> points() {
    return points;
  }

  /// Point d'écoute auquel rattacher la nuit.
  public ObjectProperty<PointDEcoute> pointSelectionneProperty() {
    return pointSelectionne;
  }

  /// Année du passage (préremplie à l'année de l'horloge applicative).
  public IntegerProperty anneeProperty() {
    return annee;
  }

  /// Numéro de passage dans l'année pour ce point (défaut 1, éditable).
  public IntegerProperty numeroPassageProperty() {
    return numeroPassage;
  }

  /// Aperçu du nom préfixé appliqué aux fichiers (R6), recalculé dès qu'un champ change ; vide tant
  /// que le site ou le point n'est pas choisi.
  public ReadOnlyStringProperty apercuPrefixeProperty() {
    return apercuPrefixe.getReadOnlyProperty();
  }

  /// Conjonction d'activation du bouton « Importer cette nuit » : dossier inspecté + site + point +
  /// n° de passage valides.
  public BooleanBinding peutImporter() {
    return peutImporter;
  }

  /// État de l'exécution de l'import (PRET / EN_COURS / TERMINE / ECHEC) : pilote l'affichage de la
  /// vue (assistant, progression, résumé, erreur).
  public ReadOnlyObjectProperty<EtatImport> etatProperty() {
    return etat.getReadOnlyProperty();
  }

  /// Résultat du dernier import réussi (passage/session créés, compteurs, anomalies) ; `null` tant
  /// qu'aucun import n'a abouti.
  public ReadOnlyObjectProperty<ResultatImport> resultatProperty() {
    return resultat.getReadOnlyProperty();
  }

  /// Recharge les sites de l'utilisateur courant (à l'ouverture de l'écran ou après création d'un
  /// site).
  public void chargerSites() {
    sites.setAll(serviceSites.listerSites(idUtilisateur));
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
      rapport = serviceImport.inspecter(dossier);
      aUnJournal.set(rapport.aUnJournal());
      aUnReleveClimatique.set(rapport.aUnReleveClimatique());
      nombreOriginaux.set(rapport.nombreOriginaux());
      etatNommage.set(rapport.etatNommage());
      resumeJournal.set(
          rapport.journalOptionnel().map(journal -> "PR n° " + journal.numeroSerie()).orElse(""));
      messageErreur.set("");
      inspecte.set(true);
      majApercu();
    } catch (RuntimeException echec) {
      echouer(echec.getMessage());
    }
  }

  /// Lance l'import de la nuit **de façon synchrone** (copie protégée R9 + renommage R6/R7 +
  /// transformation R10/R11). Pratique pour les tests et le chemin simple ; pour ne pas figer
  /// l'IHM, la vue préfère le découpage `preparerImport` (instantané) + `executerImport`
  /// (hors-thread) + `marquerEnCours`/`marquerTermine`/`marquerEchec` (sur le fil JavaFX).
  public void importer() {
    if (!peutImporter.get()) {
      messageErreur.set(
          "Complétez le rattachement (dossier inspecté, site, point) avant d'importer.");
      return;
    }
    DemandeImport demande = preparerImport();
    marquerEnCours();
    try {
      marquerTermine(executerImport(demande));
    } catch (RuntimeException echec) {
      marquerEchec(echec.getMessage());
    }
  }

  /// Passe l'état à `EN_COURS` et efface le message. À appeler sur le fil JavaFX, avant de
  /// lancer l'exécution en arrière-plan.
  public void marquerEnCours() {
    messageErreur.set("");
    etat.set(EtatImport.EN_COURS);
  }

  /// Capture (sur le fil JavaFX) les entrées du rattachement courant dans un instantané immuable,
  /// pour les passer à [#executerImport(DemandeImport)] sans relire de `Property` hors-thread.
  /// Précondition : rattachement complet ([#peutImporter()] vrai), garanti par l'appelant.
  public DemandeImport preparerImport() {
    Site site = siteSelectionne.get();
    PointDEcoute point = pointSelectionne.get();
    return new DemandeImport(
        dossierSource.get(),
        point.id(),
        new Prefixe(site.numeroCarre(), annee.get(), numeroPassage.get(), point.code()));
  }

  /// Exécute le travail lourd de l'import (copie + renommage + transformation) via
  /// [ServiceImport#importer], à partir d'un instantané. **Ne lit aucune `Property` et ne mute
  /// rien** : sûr sur un fil d'arrière-plan.
  ///
  /// @return le résultat de l'import
  /// @throws RuntimeException si l'import échoue (refus métier R5, journal manquant…)
  public ResultatImport executerImport(DemandeImport demande) {
    return serviceImport.importer(demande.dossier(), demande.idPoint(), demande.prefixe());
  }

  /// Instantané immuable des entrées d'un import, capturé sur le fil JavaFX par preparerImport.
  public record DemandeImport(Path dossier, Long idPoint, Prefixe prefixe) {}

  /// Applique un import réussi (résultat exposé, état `TERMINE`). À appeler sur le fil JavaFX
  /// (depuis `Platform.runLater`).
  public void marquerTermine(ResultatImport resultatImport) {
    resultat.set(resultatImport);
    messageErreur.set("");
    etat.set(EtatImport.TERMINE);
  }

  /// Applique un échec d'import : efface le résultat, renseigne le message, état `ECHEC`. À
  /// appeler sur le fil JavaFX (depuis `Platform.runLater`).
  public void marquerEchec(String message) {
    resultat.set(null);
    messageErreur.set(message);
    etat.set(EtatImport.ECHEC);
  }

  private void echouer(String message) {
    reinitialiserInspection();
    messageErreur.set(message);
  }

  /// Remet l'état d'inspection à zéro (plus de rapport courant). Appelé quand l'inspection échoue
  /// **et** quand le dossier source change : un nouveau dossier doit être ré-inspecté avant tout
  /// import, donc `inspecte` repasse à `false` (et `peutImporter` se désactive). Sans cela, les
  /// propriétés dérivées resteraient sur les valeurs (obsolètes) du dossier précédent.
  private void reinitialiserInspection() {
    rapport = null;
    inspecte.set(false);
    aUnJournal.set(false);
    aUnReleveClimatique.set(false);
    nombreOriginaux.set(0);
    etatNommage.set(null);
    resumeJournal.set("");
    messageErreur.set("");
    etat.set(EtatImport.PRET);
    resultat.set(null);
    majApercu();
  }

  /// Recalcule l'aperçu du préfixe (appliqué à un exemple de nom d'origine) ; vide tant que le
  /// site ou le point n'est pas choisi.
  private void majApercu() {
    Site site = siteSelectionne.get();
    PointDEcoute point = pointSelectionne.get();
    if (site == null || point == null) {
      apercuPrefixe.set("");
      return;
    }
    Prefixe prefixe =
        new Prefixe(site.numeroCarre(), annee.get(), numeroPassage.get(), point.code());
    apercuPrefixe.set(prefixe.nommerOriginal(exempleNomOriginal()));
  }

  private String exempleNomOriginal() {
    if (rapport != null && !rapport.originaux().isEmpty()) {
      return rapport.originaux().get(0).getFileName().toString();
    }
    return "PaRec…_AAAAMMJJ_HHMMSS.wav";
  }
}

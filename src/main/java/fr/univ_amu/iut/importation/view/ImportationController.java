package fr.univ_amu.iut.importation.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;
import javafx.util.converter.NumberStringConverter;

/// Controller de l'assistant **M-Import** (`Importation.fxml`).
///
/// Pur câblage (patron CM4) : lie les contrôles des 4 sections (dossier / inspection /
/// rattachement / action) aux propriétés de l'[ImportationViewModel]. Aucun accès base de données
/// ni logique métier ici (règle ArchUnit `view_sans_jdbc`) : « Parcourir » délègue à
/// [ImportationViewModel#inspecter()] ; « Importer » lance le travail lourd hors du fil JavaFX.
public class ImportationController {

  private final ImportationViewModel viewModel;

  @FXML private TextField champDossier;
  @FXML private VBox sectionInspection;
  @FXML private Label labelJournal;
  @FXML private Label labelReleve;
  @FXML private Label labelOriginaux;
  @FXML private Label labelNommage;
  @FXML private ComboBox<Site> comboSites;
  @FXML private ComboBox<PointDEcoute> comboPoints;
  @FXML private TextField champAnnee;
  @FXML private TextField champPassage;
  @FXML private Label labelApercu;
  @FXML private Button boutonParcourir;
  @FXML private Button boutonImporter;
  @FXML private ProgressIndicator indicateurProgression;
  @FXML private Label labelMessage;
  @FXML private Label labelStatut;

  @Inject
  public ImportationController(ImportationViewModel viewModel) {
    this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
  }

  @FXML
  private void initialize() {
    // 1. Dossier source (affichage en lecture seule du chemin choisi).
    champDossier
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () -> {
                  Path dossier = viewModel.dossierSourceProperty().get();
                  return dossier == null ? "" : dossier.toString();
                },
                viewModel.dossierSourceProperty()));

    // 2. Inspection : section visible une fois le dossier inspecté.
    sectionInspection.visibleProperty().bind(viewModel.inspecteProperty());
    sectionInspection.managedProperty().bind(viewModel.inspecteProperty());
    labelJournal
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () ->
                    viewModel.aUnJournalProperty().get()
                        ? "✓ Journal du capteur : " + viewModel.resumeJournalProperty().get()
                        : "⚠ Aucun journal LogPR détecté",
                viewModel.aUnJournalProperty(),
                viewModel.resumeJournalProperty()));
    labelReleve
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () ->
                    viewModel.aUnReleveClimatiqueProperty().get()
                        ? "✓ Relevé climatique détecté"
                        : "⚠ Relevé climatique absent (R20)",
                viewModel.aUnReleveClimatiqueProperty()));
    labelOriginaux
        .textProperty()
        .bind(
            viewModel.nombreOriginauxProperty().asString("✓ %d enregistrement(s) WAV détecté(s)"));
    labelNommage
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () -> "État du nommage : " + libelleNommage(viewModel.etatNommageProperty().get()),
                viewModel.etatNommageProperty()));

    // 3. Rattachement : combos site/point + année/passage + aperçu du préfixe.
    comboSites.setItems(viewModel.sites());
    comboSites.setConverter(convertisseur(this::libelleSite));
    comboSites.valueProperty().bindBidirectional(viewModel.siteSelectionneProperty());
    comboPoints.setItems(viewModel.points());
    comboPoints.setConverter(convertisseur(PointDEcoute::code));
    comboPoints.valueProperty().bindBidirectional(viewModel.pointSelectionneProperty());
    Bindings.bindBidirectional(
        champAnnee.textProperty(), viewModel.anneeProperty(), new NumberStringConverter("0"));
    Bindings.bindBidirectional(
        champPassage.textProperty(),
        viewModel.numeroPassageProperty(),
        new NumberStringConverter("0"));
    labelApercu.textProperty().bind(viewModel.apercuPrefixeProperty());

    // 4. Action : pendant l'import (EN_COURS), l'indicateur tourne et le formulaire est gelé pour
    // éviter toute modification du rattachement en cours de traitement.
    var enCours = viewModel.etatProperty().isEqualTo(EtatImport.EN_COURS);
    indicateurProgression.visibleProperty().bind(enCours);
    indicateurProgression.managedProperty().bind(enCours);
    boutonImporter.disableProperty().bind(viewModel.peutImporter().not().or(enCours));
    boutonParcourir.disableProperty().bind(enCours);
    comboSites.disableProperty().bind(enCours);
    comboPoints.disableProperty().bind(enCours);
    champAnnee.disableProperty().bind(enCours);
    champPassage.disableProperty().bind(enCours);
    labelMessage.textProperty().bind(viewModel.messageErreurProperty());
    labelStatut
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                this::libelleStatut, viewModel.etatProperty(), viewModel.resultatProperty()));

    viewModel.chargerSites();
  }

  /// « Parcourir » : ouvre le sélecteur de dossier natif puis lance l'inspection (lecture seule).
  @FXML
  private void parcourir() {
    DirectoryChooser selecteur = new DirectoryChooser();
    selecteur.setTitle("Dossier de la nuit (carte SD ou copie sur disque)");
    File dossier = selecteur.showDialog(champDossier.getScene().getWindow());
    if (dossier != null) {
      viewModel.dossierSourceProperty().set(dossier.toPath());
      viewModel.inspecter();
    }
  }

  /// « Importer cette nuit » : exécute le travail lourd sur un **virtual thread** (Java 25) pour ne
  /// pas figer le fil JavaFX, puis applique le résultat (succès ou échec) via `Platform.runLater`.
  @FXML
  private void importer() {
    if (!viewModel.peutImporter().get()) {
      return;
    }
    var demande = viewModel.preparerImport();
    viewModel.marquerEnCours();
    Thread.ofVirtual()
        .name("import-vigiechiro")
        .start(
            () -> {
              try {
                ResultatImport resultatImport = viewModel.executerImport(demande);
                Platform.runLater(() -> viewModel.marquerTermine(resultatImport));
              } catch (RuntimeException echec) {
                Platform.runLater(() -> viewModel.marquerEchec(echec.getMessage()));
              }
            });
  }

  private String libelleSite(Site site) {
    return site.nomConvivial() == null
        ? "Carré " + site.numeroCarre()
        : "Carré " + site.numeroCarre() + " — " + site.nomConvivial();
  }

  private static String libelleNommage(EtatNommage etat) {
    if (etat == null) {
      return "—";
    }
    return switch (etat) {
      case BRUT -> "fichiers bruts (seront renommés)";
      case PREFIXE -> "fichiers déjà préfixés";
      case VIDE -> "aucun fichier";
    };
  }

  private String libelleStatut() {
    if (viewModel.etatProperty().get() != EtatImport.TERMINE) {
      return "";
    }
    ResultatImport resultat = viewModel.resultatProperty().get();
    if (resultat == null) {
      return "";
    }
    return "✓ Import terminé : "
        + resultat.nombreSequences()
        + " séquence(s) produite(s) à partir de "
        + resultat.nombreOriginaux()
        + " original(aux).";
  }

  private static <T> StringConverter<T> convertisseur(Function<T, String> versTexte) {
    return new StringConverter<>() {
      @Override
      public String toString(T valeur) {
        return valeur == null ? "" : versTexte.apply(valeur);
      }

      @Override
      public T fromString(String texte) {
        return null;
      }
    };
  }
}

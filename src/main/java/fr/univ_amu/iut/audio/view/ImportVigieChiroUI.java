package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.BilanImport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;

/// Câblage et déclenchement de l'**import des résultats VigieChiro** (axe 4.2) sur la vue audio. Isolé du
/// [SonsValidationController] (même patron que [ImportTadarida] / `DepotFichier`) pour le garder léger
/// (contrainte NCSS) : le controller ne conserve que ses champs `@FXML` et deux délégations.
final class ImportVigieChiroUI {

    private ImportVigieChiroUI() {}

    /// Câble l'item de menu (libellé Importer / Réimporter, désactivé pendant un import) et le libellé de
    /// restitution (avancement / bilan / erreur), sur les propriétés des deux ViewModel.
    static void cabler(
            MenuItem item, Label message, ImportVigieChiroViewModel importVigieChiro, AudioViewModel viewModel) {
        item.textProperty()
                .bind(Bindings.when(viewModel.resultatsDisponiblesProperty())
                        .then("🔁 Réimporter depuis VigieChiro…")
                        .otherwise("☁ Importer depuis VigieChiro…"));
        item.disableProperty().bind(importVigieChiro.enCoursProperty());
        message.textProperty().bind(importVigieChiro.messageProperty());
        message.visibleProperty().bind(importVigieChiro.messageProperty().isNotEmpty());
        message.managedProperty().bind(importVigieChiro.messageProperty().isNotEmpty());
    }

    /// Lance l'import des résultats VigieChiro du passage de `source`. Deux cas : si le passage est déjà
    /// **rattaché** à une participation (dépôt-app antérieur), on importe directement ; sinon on récupère les
    /// participations du compte et on demande **à laquelle rattacher** ce passage (nuit créée à la main),
    /// avant d'importer. Sans passage (source non ciblée), ne fait rien.
    static void lancer(
            ImportVigieChiroViewModel importVigieChiro, AudioViewModel viewModel, SourceObservations source) {
        ContextePassage contexte = source.contexteDuPassage();
        if (contexte == null) {
            return;
        }
        Long idPassage = contexte.idPassage();
        if (importVigieChiro.rattache(idPassage)) {
            importerRattache(importVigieChiro, viewModel, source, idPassage);
        } else {
            rattacherPuisImporter(importVigieChiro, viewModel, source, idPassage);
        }
    }

    /// Passage déjà rattaché : confirmation FX si un jeu existe déjà, puis import hors fil.
    private static void importerRattache(
            ImportVigieChiroViewModel importVigieChiro,
            AudioViewModel viewModel,
            SourceObservations source,
            Long idPassage) {
        boolean remplacer = viewModel.resultatsDisponiblesProperty().get();
        if (remplacer && !confirmerRemplacement()) {
            return;
        }
        importerHorsFil(importVigieChiro, viewModel, source, idPassage, remplacer);
    }

    /// Passage non rattaché : récupère les participations **hors fil**, demande laquelle rattacher (au fil
    /// JavaFX), stocke le lien, puis importe. Liste vide → message ; annulation → efface l'état « en cours ».
    private static void rattacherPuisImporter(
            ImportVigieChiroViewModel importVigieChiro,
            AudioViewModel viewModel,
            SourceObservations source,
            Long idPassage) {
        importVigieChiro.marquerEnCours();
        Thread.ofVirtual().name("participations-vigiechiro").start(() -> {
            List<ParticipationVigieChiro> participations = importVigieChiro.participations();
            Platform.runLater(() -> {
                if (participations.isEmpty()) {
                    importVigieChiro.echec("Aucune participation VigieChiro sur votre compte :"
                            + " déposez d'abord cette nuit sur la plateforme.");
                    return;
                }
                Optional<ParticipationVigieChiro> choix = choisirParticipation(participations);
                if (choix.isEmpty()) {
                    importVigieChiro.echec(""); // annulé : on efface l'état « en cours »
                    return;
                }
                importVigieChiro.rattacher(idPassage, choix.orElseThrow().id());
                importerHorsFil(importVigieChiro, viewModel, source, idPassage, false);
            });
        });
    }

    /// Récupère les résultats + importe **hors fil JavaFX**, applique le bilan et recharge la liste.
    private static void importerHorsFil(
            ImportVigieChiroViewModel importVigieChiro,
            AudioViewModel viewModel,
            SourceObservations source,
            Long idPassage,
            boolean remplacer) {
        importVigieChiro.marquerEnCours();
        Thread.ofVirtual().name("import-vigiechiro").start(() -> {
            try {
                BilanImport bilan = importVigieChiro.importer(idPassage, remplacer);
                Platform.runLater(() -> {
                    importVigieChiro.appliquerBilan(bilan);
                    viewModel.ouvrirSur(source); // recharge la liste avec les observations importées
                });
            } catch (RuntimeException echec) {
                Platform.runLater(() -> importVigieChiro.echec(echec.getMessage()));
            }
        });
    }

    /// Boîte de choix de la participation à rattacher (libellé : localité · date · site). Renvoie le choix
    /// ou vide si l'utilisateur annule.
    private static Optional<ParticipationVigieChiro> choisirParticipation(
            List<ParticipationVigieChiro> participations) {
        Map<String, ParticipationVigieChiro> parLibelle = new LinkedHashMap<>();
        for (ParticipationVigieChiro participation : participations) {
            parLibelle.put(libelle(participation), participation);
        }
        String premier = parLibelle.keySet().iterator().next();
        ChoiceDialog<String> dialogue = new ChoiceDialog<>(premier, parLibelle.keySet());
        dialogue.setHeaderText("Ce passage n'est pas encore rattaché à une participation VigieChiro.");
        dialogue.setContentText("Rattacher à :");
        return dialogue.showAndWait().map(parLibelle::get);
    }

    /// Libellé lisible d'une participation pour le choix : `localité · date · titre du site`.
    private static String libelle(ParticipationVigieChiro participation) {
        StringBuilder libelle = new StringBuilder(participation.point() != null ? participation.point() : "?");
        if (participation.dateDebut() != null) {
            libelle.append(" · ").append(participation.dateDebut());
        }
        if (participation.siteTitre() != null) {
            libelle.append(" · ").append(participation.siteTitre());
        }
        return libelle.toString();
    }

    /// Confirme le **remplacement** d'un jeu de résultats existant avant un réimport (un seul jeu par
    /// passage ; les validations en cours seraient perdues).
    private static boolean confirmerRemplacement() {
        Alert alerte = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Des résultats Tadarida existent déjà pour ce passage. Les remplacer par ceux de VigieChiro ?"
                        + " Les validations en cours sur ce passage seront perdues.",
                ButtonType.OK,
                ButtonType.CANCEL);
        alerte.setHeaderText("Réimporter depuis VigieChiro ?");
        return alerte.showAndWait().filter(bouton -> bouton == ButtonType.OK).isPresent();
    }
}

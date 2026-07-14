package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.view.DemandeurDeChoix;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import java.util.List;
import java.util.Optional;
import javafx.beans.binding.Bindings;
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
    /// avant d'importer. Sans passage (source non ciblée), ne fait rien. Les appels réseau passent par
    /// `executeur` (socle #1255 : hors du fil JavaFX en production, synchrone en test).
    static void lancer(
            ImportVigieChiroViewModel importVigieChiro,
            AudioViewModel viewModel,
            SourceObservations source,
            ExecuteurTache executeur,
            Confirmateur confirmateur,
            DemandeurDeChoix<ParticipationVigieChiro> demandeur) {
        ContextePassage contexte = source.contexteDuPassage();
        if (contexte == null) {
            return;
        }
        Long idPassage = contexte.idPassage();
        if (importVigieChiro.rattache(idPassage)) {
            importerRattache(importVigieChiro, viewModel, source, idPassage, executeur, confirmateur);
        } else {
            rattacherPuisImporter(importVigieChiro, viewModel, source, idPassage, executeur, demandeur);
        }
    }

    /// Passage déjà rattaché : confirmation si un jeu existe déjà, puis import hors fil.
    private static void importerRattache(
            ImportVigieChiroViewModel importVigieChiro,
            AudioViewModel viewModel,
            SourceObservations source,
            Long idPassage,
            ExecuteurTache executeur,
            Confirmateur confirmateur) {
        boolean remplacer = viewModel.resultatsDisponiblesProperty().get();
        if (remplacer && !confirmerRemplacement(confirmateur)) {
            return;
        }
        importerHorsFil(importVigieChiro, viewModel, source, idPassage, remplacer, executeur);
    }

    /// Passage non rattaché : récupère les participations **hors fil**, demande laquelle rattacher (au fil
    /// JavaFX), stocke le lien, puis importe. Liste vide → message ; annulation → efface l'état « en cours » ;
    /// erreur réseau → restituée par le libellé d'import (auparavant elle mourait avec le thread, laissant
    /// « en cours » affiché pour toujours).
    private static void rattacherPuisImporter(
            ImportVigieChiroViewModel importVigieChiro,
            AudioViewModel viewModel,
            SourceObservations source,
            Long idPassage,
            ExecuteurTache executeur,
            DemandeurDeChoix<ParticipationVigieChiro> demandeur) {
        importVigieChiro.marquerEnCours();
        executeur.executer(
                importVigieChiro::participations,
                reponse -> {
                    // #1370 : une panne ne ressemble plus à « aucune participation sur votre compte ».
                    if (!(reponse
                            instanceof
                            ReponseApi.Succes<List<ParticipationVigieChiro>>(
                                    List<ParticipationVigieChiro> participations))) {
                        importVigieChiro.echec("Impossible de lister vos participations VigieChiro : "
                                + reponse.echec().orElse("issue inattendue"));
                        return;
                    }
                    if (participations.isEmpty()) {
                        importVigieChiro.echec("Aucune participation VigieChiro sur votre compte :"
                                + " déposez d'abord cette nuit sur la plateforme.");
                        return;
                    }
                    Optional<ParticipationVigieChiro> choix = choisirParticipation(participations, demandeur);
                    if (choix.isEmpty()) {
                        importVigieChiro.echec(""); // annulé : on efface l'état « en cours »
                        return;
                    }
                    importVigieChiro.rattacher(idPassage, choix.orElseThrow().id());
                    importerHorsFil(importVigieChiro, viewModel, source, idPassage, false, executeur);
                },
                erreur -> importVigieChiro.echec(erreur.getMessage()));
    }

    /// Récupère les résultats + importe **hors fil JavaFX**, applique le bilan et recharge la liste.
    private static void importerHorsFil(
            ImportVigieChiroViewModel importVigieChiro,
            AudioViewModel viewModel,
            SourceObservations source,
            Long idPassage,
            boolean remplacer,
            ExecuteurTache executeur) {
        importVigieChiro.marquerEnCours();
        executeur.executer(
                () -> importVigieChiro.importer(idPassage, remplacer),
                bilan -> {
                    importVigieChiro.appliquerBilan(bilan);
                    viewModel.ouvrirSur(source); // recharge la liste avec les observations importées
                },
                erreur -> importVigieChiro.echec(erreur.getMessage()));
    }

    /// Choix de la participation à rattacher (libellé : localité · date · site), par le port
    /// [DemandeurDeChoix] (#1431) et non plus un `ChoiceDialog` en dur : c'est ce qui rend jouable
    /// l'import d'un passage **non rattaché** - le cas le plus fréquent d'une nuit créée à la main.
    private static Optional<ParticipationVigieChiro> choisirParticipation(
            List<ParticipationVigieChiro> participations, DemandeurDeChoix<ParticipationVigieChiro> demandeur) {
        return demandeur.choisir(
                "Ce passage n'est pas encore rattaché à une participation VigieChiro.",
                "Rattacher à :",
                participations,
                ImportVigieChiroUI::libelle);
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
    private static boolean confirmerRemplacement(Confirmateur confirmateur) {
        return confirmateur.confirmer(
                "Des résultats Tadarida existent déjà pour ce passage. Les remplacer par ceux de VigieChiro ?"
                        + " Les validations en cours sur ce passage seront perdues.");
    }
}

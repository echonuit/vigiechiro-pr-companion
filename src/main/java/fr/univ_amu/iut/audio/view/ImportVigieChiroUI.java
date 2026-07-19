package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.view.DemandeurDeChoix;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.SuiviOperation;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.stage.Window;

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
                        .then("Réimporter depuis Vigie-Chiro…")
                        .otherwise("Importer depuis Vigie-Chiro…"));
        item.disableProperty().bind(importVigieChiro.enCoursProperty());
        message.textProperty().bind(importVigieChiro.messageProperty());
        message.visibleProperty().bind(importVigieChiro.messageProperty().isNotEmpty());
        message.managedProperty().bind(importVigieChiro.messageProperty().isNotEmpty());
    }

    /// Lance l'import des résultats VigieChiro du passage de `source`. Deux cas : si le passage est déjà
    /// **rattaché** à une participation (dépôt-app antérieur), on importe directement ; sinon on récupère les
    /// participations du compte et on demande **à laquelle rattacher** ce passage (nuit créée à la main),
    /// avant d'importer. Sans passage (source non ciblée), ne fait rien.
    ///
    /// La brève lecture des participations passe par `occupation` (voile « … en cours »). L'**import**
    /// lui-même — qui peut brasser des milliers de fichiers — passe par `dialogue` : une **modale de
    /// progression annulable** ([SuiviOperation], #1622) au-dessus de `proprietaire`, qui montre l'avancement
    /// page par page et laisse renoncer, plutôt qu'un voile opaque.
    static void lancer(
            ImportVigieChiroViewModel importVigieChiro,
            AudioViewModel viewModel,
            SourceObservations source,
            IndicateurOccupation occupation,
            SuiviOperation dialogue,
            Supplier<Window> proprietaire,
            Confirmateur confirmateur,
            DemandeurDeChoix<ParticipationVigieChiro> demandeur) {
        ContextePassage contexte = source.contexteDuPassage();
        if (contexte == null) {
            return;
        }
        Long idPassage = contexte.idPassage();
        if (importVigieChiro.rattache(idPassage)) {
            importerRattache(importVigieChiro, viewModel, source, idPassage, dialogue, proprietaire, confirmateur);
        } else {
            rattacherPuisImporter(
                    importVigieChiro, viewModel, source, idPassage, occupation, dialogue, proprietaire, demandeur);
        }
    }

    /// Passage déjà rattaché : confirmation si un jeu existe déjà, puis import dans la modale de progression.
    private static void importerRattache(
            ImportVigieChiroViewModel importVigieChiro,
            AudioViewModel viewModel,
            SourceObservations source,
            Long idPassage,
            SuiviOperation dialogue,
            Supplier<Window> proprietaire,
            Confirmateur confirmateur) {
        DecisionRemplacementJeu.resoudre(
                        viewModel.resultatsDisponiblesProperty().get(), confirmateur, "ceux de Vigie-Chiro")
                .ifPresent(remplacer -> importerHorsFil(
                        importVigieChiro, viewModel, source, idPassage, remplacer, dialogue, proprietaire));
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
            IndicateurOccupation occupation,
            SuiviOperation dialogue,
            Supplier<Window> proprietaire,
            DemandeurDeChoix<ParticipationVigieChiro> demandeur) {
        importVigieChiro.marquerEnCours();
        occupation.occuper(
                "Lecture de vos participations Vigie-Chiro…",
                importVigieChiro::participations,
                reponse -> {
                    // #1370 : une panne ne ressemble plus à « aucune participation sur votre compte ».
                    if (!(reponse
                            instanceof
                            ReponseApi.Succes<List<ParticipationVigieChiro>>(
                                    List<ParticipationVigieChiro> participations))) {
                        importVigieChiro.echec("Impossible de lister vos participations Vigie-Chiro : "
                                + reponse.echec().orElse("issue inattendue"));
                        return;
                    }
                    if (participations.isEmpty()) {
                        importVigieChiro.echec("Aucune participation Vigie-Chiro sur votre compte :"
                                + " déposez d'abord cette nuit sur la plateforme.");
                        return;
                    }
                    Optional<ParticipationVigieChiro> choix = choisirParticipation(participations, demandeur);
                    if (choix.isEmpty()) {
                        importVigieChiro.echec(""); // annulé : on efface l'état « en cours »
                        return;
                    }
                    importVigieChiro.rattacher(idPassage, choix.orElseThrow().id());
                    importerHorsFil(importVigieChiro, viewModel, source, idPassage, false, dialogue, proprietaire);
                },
                erreur -> importVigieChiro.echec(erreur.getMessage()));
    }

    /// Importe **hors fil JavaFX** dans la **modale de progression annulable** : le suivi par page avance la
    /// barre, l'annulation efface l'état « en cours », le succès applique le bilan et recharge la liste.
    private static void importerHorsFil(
            ImportVigieChiroViewModel importVigieChiro,
            AudioViewModel viewModel,
            SourceObservations source,
            Long idPassage,
            boolean remplacer,
            SuiviOperation dialogue,
            Supplier<Window> proprietaire) {
        importVigieChiro.marquerEnCours();
        dialogue.lancer(
                proprietaire.get(),
                "Import des observations depuis Vigie-Chiro",
                (progres, jeton) -> importVigieChiro.importer(idPassage, remplacer, progres, jeton),
                bilan -> {
                    importVigieChiro.appliquerBilan(bilan);
                    viewModel.ouvrirSur(source); // recharge la liste avec les observations importées
                },
                () -> importVigieChiro.echec(""), // annulé : on efface l'état « en cours »
                erreur -> importVigieChiro.echec(erreur.getMessage()));
    }

    /// Choix de la participation à rattacher (libellé : localité · date · site), par le port
    /// [DemandeurDeChoix] (#1431) et non plus un `ChoiceDialog` en dur : c'est ce qui rend jouable
    /// l'import d'un passage **non rattaché** - le cas le plus fréquent d'une nuit créée à la main.
    private static Optional<ParticipationVigieChiro> choisirParticipation(
            List<ParticipationVigieChiro> participations, DemandeurDeChoix<ParticipationVigieChiro> demandeur) {
        return demandeur.choisir(
                "Ce passage n'est pas encore rattaché à une participation Vigie-Chiro.",
                "Rattacher à :",
                participations,
                ImportVigieChiroUI::libelle);
    }

    /// Libellé lisible d'une participation pour le choix : `localité · date · titre du site`.
    private static String libelle(ParticipationVigieChiro participation) {
        // Les parties absentes disparaissent, séparateur compris : un « ? · · Étang » trahirait un trou
        // que l'utilisateur ne peut pas combler. Le point garde son « ? » parce qu'il identifie la ligne.
        return Stream.of(
                        participation.point() != null ? participation.point() : "?",
                        participation.dateDebut(),
                        participation.siteTitre())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" · "));
    }
}

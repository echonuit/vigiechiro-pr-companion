package fr.univ_amu.iut.multisite.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.RapportReconstruction;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de la modale **« Reconstruire un passage manquant »** (#1396, suite de #1305).
///
/// Les nuits déposées depuis un autre poste (ou avant l'application, ou après une réinstallation)
/// existent **sur la plateforme** et nulle part ici : elles ne figurent donc dans aucune liste de
/// l'application. Ce ViewModel les fait apparaître, et permet d'en rapatrier une en **passage archivé**.
///
/// Il vit dans `multisite` et non dans `passage` : une feature peut dépendre du `model` d'une autre,
/// jamais de sa `view` ni de son `viewmodel` (`ArchitectureTest`). Le service est un `Optional` : hors
/// connexion (ou feature « Import VigieChiro » éteinte), il est **absent**, et l'action se retire au lieu
/// d'échouer.
///
/// Les méthodes marquées **bloquantes** font du réseau : à exécuter **hors du fil JavaFX** ; les
/// méthodes de restitution mutent des propriétés observables et s'exécutent **sur** le fil JavaFX.
public class ReconstructionViewModel {

    private final Optional<ServiceReconstructionPassages> service;

    private final ObservableList<ParticipationOrpheline> orphelines = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper compteRendu = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper reconstruit = new ReadOnlyBooleanWrapper(false);

    @Inject
    public ReconstructionViewModel(Optional<ServiceReconstructionPassages> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /// Vrai si la reconstruction est possible dans ce contexte (connecté à VigieChiro). Faux, l'appelant
    /// **retire** l'action plutôt que d'offrir un bouton qui échouerait.
    public boolean disponible() {
        return service.isPresent();
    }

    /// Participations sans équivalent local, dans l'ordre où la plateforme les rend.
    public ObservableList<ParticipationOrpheline> orphelines() {
        return orphelines;
    }

    /// Message d'état de la modale : combien de nuits manquent, ou pourquoi la lecture a échoué.
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    /// Compte rendu de la reconstruction, **lacunes comprises** : un passage reconstruit est moins riche
    /// qu'un passage archivé par purge, et le taire laisserait croire à une équivalence.
    public ReadOnlyStringProperty compteRenduProperty() {
        return compteRendu.getReadOnlyProperty();
    }

    /// Vrai dès qu'une nuit a été reconstruite : l'appelant sait alors qu'il doit **recharger** sa table.
    public ReadOnlyBooleanProperty reconstruitProperty() {
        return reconstruit.getReadOnlyProperty();
    }

    /// **Bloquant** (réseau) : lit les participations du compte et retient celles qui n'ont pas de passage
    /// ici.
    public List<ParticipationOrpheline> charger() {
        return exiger().orphelines();
    }

    /// Publie la liste chargée (**fil JavaFX**).
    public void appliquer(List<ParticipationOrpheline> chargees) {
        orphelines.setAll(chargees);
        message.set(
                chargees.isEmpty()
                        ? "Aucune nuit manquante : toutes vos participations VigieChiro ont un passage ici."
                        : chargees.size() + " nuit(s) déposée(s) sur VigieChiro n'existent pas sur cette machine.");
    }

    /// **Bloquant** (réseau) : reconstruit la participation choisie en passage archivé.
    public RapportReconstruction reconstruire(ParticipationOrpheline orpheline) {
        Objects.requireNonNull(orpheline, "orpheline");
        return exiger().reconstruire(orpheline.idParticipation());
    }

    /// Publie le compte rendu et retire la nuit reconstruite de la liste des manquantes (**fil JavaFX**).
    public void restituer(ParticipationOrpheline orpheline, RapportReconstruction rapport) {
        orphelines.remove(orpheline);
        reconstruit.set(true);
        compteRendu.set("Nuit du " + orpheline.dateDebut() + " reconstruite (passage archivé) : "
                + rapport.sequencesRecreees() + " séquence(s), " + rapport.observationsImportees()
                + " observation(s) rapatriée(s)."
                + System.lineSeparator()
                + "Ce que la plateforme ne sait pas et qui manquera donc :"
                + System.lineSeparator()
                + String.join(
                        System.lineSeparator(),
                        rapport.lacunes().stream()
                                .map(lacune -> "  - " + lacune)
                                .toList())
                + System.lineSeparator()
                + "Le passage est consultable mais pas écoutable. Si vous retrouvez les fichiers d'origine,"
                + " ouvrez-le et utilisez « Réactiver ce passage ».");
        message.set("");
    }

    /// Route un échec vers le message de la modale : un refus (point inconnu, hors connexion, analyse non
    /// terminée) **dit quoi faire**, il ne doit pas remonter en exception muette depuis le fil de fond.
    public void signalerErreur(Throwable erreur) {
        String detail = erreur.getMessage();
        message.set(detail != null && !detail.isBlank() ? detail : "Reconstruction impossible.");
    }

    private ServiceReconstructionPassages exiger() {
        return service.orElseThrow(() -> new RegleMetierException("La reconstruction a besoin de la connexion"
                + " VigieChiro : connectez-vous (menu ☰ > Se connecter à VigieChiro) puis recommencez."));
    }
}

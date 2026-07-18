package fr.univ_amu.iut.multisite.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.viewmodel.ProgressionOperation;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.RapportReconstruction;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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

    /// Ce que la modale **constate** de la liste chargée (combien de nuits manquent) : un **état**, pas
    /// le compte rendu d'une action. Il décrit ce qu'on voit, il ne se ferme pas, et il survit à la
    /// fermeture du bandeau.
    private final ReadOnlyStringWrapper etatListe = new ReadOnlyStringWrapper("");

    /// Compte rendu de la dernière **opération** (reconstruction réussie, refus, annulation), avec sa
    /// sévérité (#1917). Il remplace trois propriétés distinctes - `message`, `erreur`, `compteRendu` -
    /// dont les **noms** portaient la sévérité : elle vit désormais dans la valeur, et les trois cas se
    /// rendent par un seul bandeau au lieu de trois libellés empilés.
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);
    private final ReadOnlyBooleanWrapper reconstruit = new ReadOnlyBooleanWrapper(false);

    /// Suivi de la **progression déterminée** de la reconstruction (barre + libellé + ETA), via le socle
    /// [ProgressionOperation] partagé avec l'import et le dépôt : la vue lie sa barre à
    /// `progression().fractionProperty()` et son libellé à `progression().messageProperty()`. Pour un import
    /// groupé (#1708), c'est la progression **de la nuit en cours**.
    private final ProgressionOperation progression = new ProgressionOperation();

    /// Progression **globale** de l'import groupé (#1708) : « Nuit X / N ». Distincte de [#progression] (qui
    /// suit la nuit courante) pour donner à l'utilisateur les **deux** niveaux d'avancement sur un lot long.
    private final ProgressionOperation progressionGlobale = new ProgressionOperation();

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

    /// Constat de la modale : combien de nuits manquent (ou qu'il n'en manque aucune). Un **état** de la
    /// liste chargée, permanent et non fermable.
    public ReadOnlyStringProperty etatListeProperty() {
        return etatListe.getReadOnlyProperty();
    }

    /// Compte rendu de la dernière opération, avec sa sévérité (ADR 0023) : reconstruction réussie
    /// (**lacunes comprises** : un passage reconstruit est moins riche qu'un passage archivé par purge,
    /// et le taire laisserait croire à une équivalence), refus, ou annulation.
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Efface le retour (l'utilisateur a lu le bandeau et le ferme).
    public void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }

    /// Vrai dès qu'une nuit a été reconstruite : l'appelant sait alors qu'il doit **recharger** sa table.
    public ReadOnlyBooleanProperty reconstruitProperty() {
        return reconstruit.getReadOnlyProperty();
    }

    /// Suivi de la progression de l'opération longue, à lier depuis la vue (barre + libellé, #1522). Pour un
    /// import groupé, c'est la progression **de la nuit en cours**.
    public ProgressionOperation progression() {
        return progression;
    }

    /// Progression **globale** de l'import groupé (« Nuit X / N »), à lier depuis la vue à une seconde barre
    /// (#1708) : l'utilisateur suit à la fois où en est le lot **et** où en est la nuit courante.
    public ProgressionOperation progressionGlobale() {
        return progressionGlobale;
    }

    /// **Bloquant** (réseau) : lit les participations du compte et retient celles qui n'ont pas de passage
    /// ici.
    public List<ParticipationOrpheline> charger() {
        return exiger().orphelines();
    }

    /// Publie la liste chargée (**fil JavaFX**).
    public void appliquer(List<ParticipationOrpheline> chargees) {
        orphelines.setAll(chargees);
        // Ne touche PAS au retour : l'appelant recharge la liste APRÈS une reconstruction, et effacer ici
        // supprimerait le bilan qu'on vient de produire (le compte rendu survivait déjà au rechargement
        // avant #1917, puisqu'il vivait dans une propriété distincte).
        etatListe.set(
                chargees.isEmpty()
                        ? "Aucune nuit manquante : toutes vos participations Vigie-Chiro ont un passage ici."
                        : chargees.size() + " nuit(s) déposée(s) sur Vigie-Chiro n'existent pas sur cette machine.");
    }

    /// **Bloquant** (réseau + base) : reconstruit la participation choisie en passage archivé, en relayant
    /// sa **progression** et en honorant le **jeton d'annulation** (#1252, #1522). L'orpheline est passée
    /// telle quelle au service, qui en tire carré et localité sans re-télécharger la liste des
    /// participations.
    public RapportReconstruction reconstruire(
            ParticipationOrpheline orpheline, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(orpheline, "orpheline");
        return exiger().reconstruire(orpheline, progres, jeton);
    }

    /// Publie le compte rendu et retire la nuit reconstruite de la liste des manquantes (**fil JavaFX**).
    public void restituer(ParticipationOrpheline orpheline, RapportReconstruction rapport) {
        orphelines.remove(orpheline);
        reconstruit.set(true);
        String rendu = "Nuit du " + orpheline.dateDebut() + " reconstruite (passage archivé) : "
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
                + " ouvrez-le et utilisez « Réactiver ce passage ».";
        retour.set(RetourOperation.succes(rendu));
    }

    /// **Bloquant** (réseau + base) : reconstruit **toutes** les nuits de `aTraiter`. La boucle et la
    /// **politique best-effort** vivent au service ([ServiceReconstructionPassages#reconstruireTout],
    /// harmonisation passe 7) : l'IHM ne fournit que ses **barres** - la progression du lot (`progresGlobal`,
    /// « Nuit X / N ») et de la nuit courante (`progresNuit`) - et **ignore** l'issue par nuit, car c'est le
    /// **bilan** renvoyé qui pilote la restitution ([#restituerLot]).
    public ServiceReconstructionPassages.BilanReconstructionGroupe reconstruireTout(
            List<ParticipationOrpheline> aTraiter,
            Consumer<Progression> progresGlobal,
            Consumer<Progression> progresNuit,
            JetonAnnulation jeton) {
        Objects.requireNonNull(aTraiter, "aTraiter");
        return exiger().reconstruireTout(aTraiter, progresGlobal, progresNuit, issue -> {}, jeton);
    }

    /// Publie le compte rendu d'un import groupé (**fil JavaFX**) : combien de nuits reconstruites, combien
    /// ignorées, et le rappel que les passages restaurés sont consultables mais pas écoutables. Marque
    /// [#reconstruitProperty] dès qu'au moins une nuit a été reconstruite, pour que l'appelant recharge sa
    /// table. La liste des orphelines restantes est rechargée par l'appelant ([#charger]).
    public void restituerLot(ServiceReconstructionPassages.BilanReconstructionGroupe bilan) {
        Objects.requireNonNull(bilan, "bilan");
        reconstruit.set(bilan.reussies() > 0);
        String rendu = bilan.reussies() + " nuit(s) reconstruite(s) : " + bilan.sequences() + " séquence(s), "
                + bilan.observations() + " observation(s) rapatriée(s).";
        if (bilan.ignorees() > 0) {
            rendu += System.lineSeparator() + bilan.ignorees()
                    + " nuit(s) ignorée(s) (point d'écoute inconnu ici, ou analyse non terminée) : elles restent"
                    + " dans la liste.";
        }
        rendu += System.lineSeparator()
                + "Les passages reconstruits sont consultables mais pas écoutables (le dépôt ZIP ne restitue"
                + " pas l'audio). Réactivez-les si vous retrouvez les fichiers d'origine.";
        retour.set(RetourOperation.succes(rendu));
    }

    /// Route un échec vers le message de la modale : un refus (point inconnu, hors connexion, analyse non
    /// terminée) **dit quoi faire**, il ne doit pas remonter en exception muette depuis le fil de fond.
    public void signalerErreur(Throwable echec) {
        String detail = echec.getMessage();
        retour.set(RetourOperation.erreur(detail != null && !detail.isBlank() ? detail : null));
    }

    /// Annulation demandée par l'utilisateur : un état **neutre**, pas une erreur. Rien n'a été créé (la
    /// reconstruction se compense côté service), et on le dit plutôt que de laisser un écran figé.
    public void signalerAnnulation() {
        // Ni un succès (rien n'a été créé), ni une erreur (rien n'a raté, l'utilisateur a arrêté).
        retour.set(RetourOperation.info("Reconstruction annulée : aucun passage n'a été créé."));
    }

    private ServiceReconstructionPassages exiger() {
        return service.orElseThrow(() -> new RegleMetierException("La reconstruction a besoin de la connexion"
                + " Vigie-Chiro : connectez-vous (menu ☰ > Se connecter à Vigie-Chiro) puis recommencez."));
    }
}

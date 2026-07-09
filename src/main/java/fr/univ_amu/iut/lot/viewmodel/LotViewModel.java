package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.ControleCoherence;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran **M-Lot** (préparation et dépôt d'un passage, parcours P4, épopée E4).
///
/// Ouvert sur un `idPassage`, il lit [ServiceLot#consulterLot(Long)] et pilote le **dépôt en deux
/// temps** : [#preparer()] (Vérifié → Prêt à déposer, R14 + cohérence) puis [#deposer()] (Prêt à
/// déposer → Déposé). Chaque action délègue au service puis recharge l'état. VM agnostique de l'IHM
/// (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls `javafx.beans`/`javafx.collections`.
/// Non-singleton.
public class LotViewModel {

    private final ServiceLot service;
    private Long idPassage;

    /// Statut workflow du lot couramment chargé, mémorisé pour recomposer le stepper après une génération
    /// d'archives (qui ne recharge pas l'état). `null` tant qu'aucun lot n'est ouvert.
    private StatutWorkflow statutCourant;

    private final ReadOnlyStringWrapper statut = new ReadOnlyStringWrapper(this, "statut", "");
    private final ReadOnlyStringWrapper cheminDossier = new ReadOnlyStringWrapper(this, "cheminDossier", "");
    private final ReadOnlyStringWrapper cheminDepot = new ReadOnlyStringWrapper(this, "cheminDepot", "");
    private final ObservableList<EtapeDepot> etapes = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper recap = new ReadOnlyStringWrapper(this, "recap", "");
    private final ObservableList<ControleCoherence> controles = FXCollections.observableArrayList();
    private final ReadOnlyBooleanWrapper peutPreparer = new ReadOnlyBooleanWrapper(this, "peutPreparer", false);
    private final ReadOnlyBooleanWrapper peutDeposer = new ReadOnlyBooleanWrapper(this, "peutDeposer", false);
    private final ReadOnlyBooleanWrapper depose = new ReadOnlyBooleanWrapper(this, "depose", false);
    private final ReadOnlyBooleanWrapper peutGenererArchives =
            new ReadOnlyBooleanWrapper(this, "peutGenererArchives", false);

    /// Suppression des archives possible (#…) : vraie une fois le passage **déposé** et s'il reste des
    /// archives ZIP sur disque (nettoyage post-dépôt pour libérer l'espace, archives régénérables).
    private final ReadOnlyBooleanWrapper peutSupprimerArchives =
            new ReadOnlyBooleanWrapper(this, "peutSupprimerArchives", false);

    /// Génération des archives en cours (#251) : posée pendant le travail hors fil JavaFX pour afficher
    /// un état « en cours » et désactiver le bouton (l'opération peut être longue sur une grosse nuit).
    private final ReadOnlyBooleanWrapper generationEnCours =
            new ReadOnlyBooleanWrapper(this, "generationEnCours", false);
    private final ObservableList<String> archives = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper titreArchives = new ReadOnlyStringWrapper(this, "titreArchives", "");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    /// Progression déterminée de la génération des archives (#769) : fraction + libellé « Compression X/N »
    /// avec estimation du temps restant. Alimentée par le callback du service (relayé au fil JavaFX).
    private final ProgressionLot progression = new ProgressionLot();

    public LotViewModel(ServiceLot service) {
        this.service = Objects.requireNonNull(service, "service");
        // Titre reflétant le **plafond configuré** (#110) : en Mo base 1000 (cohérent avec la contrainte
        // « 700 Mo » Tadarida), et non Formats.octetsLisibles qui raisonne en base 1024.
        long plafondMo = service.plafondArchiveOctets() / 1_000_000;
        titreArchives.set("🗜 Archives de dépôt Tadarida (≤ " + plafondMo + " Mo)");
    }

    /// Ouvre l'écran de dépôt du passage `idPassage`. Une erreur (passage introuvable) est restituée
    /// dans [#messageProperty()] sans lever, l'écran restant vide.
    public void ouvrirSur(Long idPassage) {
        this.idPassage = idPassage;
        reinitialiser();
        try {
            appliquer(service.consulterLot(idPassage));
        } catch (RuntimeException echec) {
            reinitialiser();
            message.set(echec.getMessage());
        }
    }

    /// Prépare le lot (R14 + cohérence) : Vérifié → Prêt à déposer, puis recharge. Sans passage
    /// ouvert, l'appel est ignoré. Une erreur métier est restituée dans [#messageProperty()].
    ///
    /// @return `true` si la préparation a réussi
    public boolean preparer() {
        return appliquerAction(() -> service.preparerLot(idPassage));
    }

    /// Marque le passage déposé après téléversement manuel : Prêt à déposer → Déposé, puis recharge.
    ///
    /// @return `true` si le dépôt a été enregistré
    public boolean deposer() {
        return appliquerAction(() -> service.marquerDepose(idPassage));
    }

    /// Supprime les **archives ZIP de dépôt** (`depot/*.zip`) une fois le passage déposé, pour libérer
    /// l'espace disque (elles sont sur Vigie-Chiro et régénérables). Recharge l'état ensuite : le bouton se
    /// désactive de lui-même (plus d'archives sur disque). Annonce l'espace libéré.
    ///
    /// @return `true` si la suppression a réussi
    public boolean supprimerArchives() {
        if (idPassage == null) {
            return false;
        }
        try {
            long liberes = service.supprimerArchivesDepot(idPassage);
            archives.clear();
            appliquer(service.consulterLot(idPassage));
            message.set("Archives de dépôt supprimées (" + Formats.octetsLisibles(liberes) + " libérés).");
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    /// Génère les **archives ZIP de dépôt** (#110) de façon **synchrone** (tests + CLI) : enchaîne
    /// [#marquerGenerationEnCours()], [#calculerArchivesDepot(Consumer)] et [#appliquerGeneration] /
    /// [#echecGeneration]. La progression n'est pas suivie ici (callback inerte) ; la vue préfère le
    /// découpage avec suivi pour exécuter le calcul **hors fil JavaFX** sans figer l'IHM.
    ///
    /// @return `true` si au moins une archive a été générée
    public boolean genererArchives() {
        if (idPassage == null) {
            return false;
        }
        marquerGenerationEnCours();
        try {
            appliquerGeneration(calculerArchivesDepot(progression -> {}));
            return true;
        } catch (RuntimeException echec) {
            echecGeneration(echec.getMessage());
            return false;
        }
    }

    /// Passe à l'état « génération en cours » (#251) et l'annonce : **à appeler sur le fil JavaFX**, juste
    /// avant de lancer [#calculerArchivesDepot(Consumer)] sur un fil d'arrière-plan. Amorce aussi le suivi
    /// de progression (#769 : barre + estimation de durée) en posant la référence temporelle de l'ETA.
    public void marquerGenerationEnCours() {
        generationEnCours.set(true);
        message.set("Génération des archives de dépôt en cours…");
        progression.demarrer("Préparation des archives…");
    }

    /// Calcule les archives ZIP de dépôt (appel service, potentiellement **long**). **Aucune** mutation de
    /// propriété observable ici : sûr à exécuter **hors fil JavaFX**. `progres` remonte l'avancement
    /// (#769) ; il DOIT relayer les mutations vers le fil JavaFX (`Platform.runLater`). Le résultat est
    /// appliqué ensuite par [#appliquerGeneration] (sur le fil JavaFX). Sans passage ouvert, liste vide.
    public List<ArchiveDepot> calculerArchivesDepot(Consumer<Progression> progres) {
        if (idPassage == null) {
            return List.of();
        }
        return service.genererArchivesDepot(idPassage, progres);
    }

    /// Applique le résultat d'une génération réussie (#251) : **à appeler sur le fil JavaFX**. Publie la
    /// liste, fait avancer le stepper de ② « Générer » vers ③ « Téléverser », et lève l'état « en cours ».
    public void appliquerGeneration(List<ArchiveDepot> produites) {
        archives.setAll(produites.stream().map(FormatsLot::archiveLisible).toList());
        if (statutCourant != null) {
            majEtapes(statutCourant);
        }
        message.set(produites.size() + " archive(s) de dépôt générée(s) dans le sous-dossier « depot/ ».");
        generationEnCours.set(false);
        progression.reinitialiser();
    }

    /// Restitue l'échec d'une génération (#251) : **à appeler sur le fil JavaFX**. Affiche le message et
    /// lève l'état « en cours ».
    public void echecGeneration(String messageErreur) {
        message.set(messageErreur);
        generationEnCours.set(false);
        progression.reinitialiser();
    }

    /// Suivi de progression de la génération des archives (#769) : fraction pour la barre déterminée +
    /// libellé « Compression X/N » avec estimation du temps restant, à lier dans la vue.
    public ProgressionLot progression() {
        return progression;
    }

    private boolean appliquerAction(Runnable action) {
        if (idPassage == null) {
            return false;
        }
        try {
            action.run();
            appliquer(service.consulterLot(idPassage));
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    private void appliquer(EtatLot etat) {
        statutCourant = etat.statut();
        statut.set(etat.statut().libelle());
        String dossier = etat.cheminDossier() == null ? "" : etat.cheminDossier();
        cheminDossier.set(dossier);
        // Cible réelle du téléversement (#251) : le sous-dossier « depot/ » de la session, où vivent les
        // archives ZIP, et non le dossier de session entier.
        cheminDepot.set(dossier.isEmpty() ? "" : dossier + "/depot");
        recap.set(FormatsLot.recapLisible(etat));
        controles.setAll(etat.controles());
        // Réhydrate la liste des archives depuis le DISQUE (#…) : à la réouverture d'un passage déjà généré,
        // on réaffiche les archives présentes dans depot/ (et on réactive « Ouvrir le dossier » / « Supprimer »),
        // là où l'ancienne liste, seulement peuplée en session, restait vide après navigation.
        archives.setAll(service.archivesDepot(etat.cheminDossier()).stream()
                .map(FormatsLot::archiveLisible)
                .toList());
        peutPreparer.set(etat.statut() == StatutWorkflow.VERIFIE && !etat.aDesEchecs());
        peutDeposer.set(etat.statut() == StatutWorkflow.PRET_A_DEPOSER);
        depose.set(etat.statut() == StatutWorkflow.DEPOSE);
        // Les archives de dépôt (#110) se génèrent dès que le lot est prêt (séquences figées) : Prêt à
        // déposer ou déjà déposé.
        peutGenererArchives.set(
                etat.statut() == StatutWorkflow.PRET_A_DEPOSER || etat.statut() == StatutWorkflow.DEPOSE);
        // Suppression des archives (#…) : seulement une fois le passage DÉPOSÉ et s'il reste des archives
        // sur disque (déjà téléversées, régénérables).
        peutSupprimerArchives.set(etat.statut() == StatutWorkflow.DEPOSE && !archives.isEmpty());
        majEtapes(etat.statut());
        message.set(FormatsLot.messageEtat(etat));
    }

    /// Recompose le stepper du dépôt (#251) depuis [EtapesDepot], selon le statut et la génération
    /// d'archives. Appelé à chaque (re)chargement d'état et après une génération.
    private void majEtapes(StatutWorkflow statut) {
        etapes.setAll(EtapesDepot.calculer(statut, !archives.isEmpty()));
    }

    private void reinitialiser() {
        statutCourant = null;
        statut.set("");
        cheminDossier.set("");
        cheminDepot.set("");
        etapes.clear();
        recap.set("");
        controles.clear();
        peutPreparer.set(false);
        peutDeposer.set(false);
        depose.set(false);
        peutGenererArchives.set(false);
        peutSupprimerArchives.set(false);
        generationEnCours.set(false);
        archives.clear();
        message.set("");
    }

    /// Libellé du statut workflow courant du passage.
    public ReadOnlyStringProperty statutProperty() {
        return statut.getReadOnlyProperty();
    }

    /// Chemin du dossier de session (R22), vide si pas de session. Emplacement où vit le sous-dossier
    /// `depot/` ; ce qu'on téléverse, ce sont les archives ZIP de [#cheminDepotProperty()].
    public ReadOnlyStringProperty cheminDossierProperty() {
        return cheminDossier.getReadOnlyProperty();
    }

    /// Chemin du sous-dossier `depot/` à téléverser sur Vigie-Chiro (#251) : c'est là que sont écrites
    /// les archives ZIP de dépôt. Vide tant qu'aucune session n'est chargée.
    public ReadOnlyStringProperty cheminDepotProperty() {
        return cheminDepot.getReadOnlyProperty();
    }

    /// Étapes ordonnées du dépôt pour le stepper (#251) : ① Préparer · ② Générer les archives ·
    /// ③ Téléverser · ④ Marquer déposé, chacune avec son état d'avancement. Vide si pas de lot ouvert.
    public ObservableList<EtapeDepot> etapes() {
        return etapes;
    }

    /// Récapitulatif du lot (`N séquences · X Mo`).
    public ReadOnlyStringProperty recapProperty() {
        return recap.getReadOnlyProperty();
    }

    /// Checklist des contrôles de cohérence (#254) : ✓ satisfait, ✗ bloquant, ⚠ avertissement. Affichée
    /// à l'étape « Préparer le lot », même quand tout est satisfait.
    public ObservableList<ControleCoherence> controles() {
        return controles;
    }

    /// `true` si le lot peut être préparé (passage Vérifié et aucune alerte bloquante).
    public ReadOnlyBooleanProperty peutPreparerProperty() {
        return peutPreparer.getReadOnlyProperty();
    }

    /// `true` si le passage peut être marqué déposé (statut Prêt à déposer).
    public ReadOnlyBooleanProperty peutDeposerProperty() {
        return peutDeposer.getReadOnlyProperty();
    }

    /// `true` si le passage est déjà déposé.
    public ReadOnlyBooleanProperty deposeProperty() {
        return depose.getReadOnlyProperty();
    }

    /// `true` si les archives de dépôt peuvent être générées (lot préparé : Prêt à déposer ou Déposé).
    public ReadOnlyBooleanProperty peutGenererArchivesProperty() {
        return peutGenererArchives.getReadOnlyProperty();
    }

    /// `true` si les archives de dépôt peuvent être supprimées : passage **déposé** et archives ZIP encore
    /// présentes dans `depot/` (nettoyage post-dépôt pour libérer l'espace).
    public ReadOnlyBooleanProperty peutSupprimerArchivesProperty() {
        return peutSupprimerArchives.getReadOnlyProperty();
    }

    /// `true` pendant la génération des archives (#251) : la vue affiche un état « en cours » et
    /// désactive le bouton (l'opération s'exécute hors fil JavaFX et peut être longue).
    public ReadOnlyBooleanProperty generationEnCoursProperty() {
        return generationEnCours.getReadOnlyProperty();
    }

    /// Récapitulatifs lisibles des archives ZIP de dépôt produites (#110), vide tant qu'aucune génération.
    public ObservableList<String> archives() {
        return archives;
    }

    /// Titre de la section archives, intégrant le **plafond configuré** (ex. « …(≤ 700 Mo) », #110).
    public ReadOnlyStringProperty titreArchivesProperty() {
        return titreArchives.getReadOnlyProperty();
    }

    /// Message d'état ou d'erreur (déposé, alertes, échec d'action), vide en fonctionnement nominal.
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }
}

package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.ControleCoherence;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.SuiviArchives;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.BooleanExpression;
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

    /// Espace disque estimé **suffisant** pour générer les archives (#…) : anticipé au chargement
    /// (estimation compression comprise vs espace disponible). `true` par défaut / si indéterminé (on ne
    /// bloque pas). Faux → bouton « Générer » désactivé + [#raisonEspaceInsuffisant] expliquée.
    private final ReadOnlyBooleanWrapper espaceDepotSuffisant =
            new ReadOnlyBooleanWrapper(this, "espaceDepotSuffisant", true);

    private final ReadOnlyStringWrapper raisonEspaceInsuffisant =
            new ReadOnlyStringWrapper(this, "raisonEspaceInsuffisant", "");

    /// Génération des archives en cours (#251) : posée pendant le travail hors fil JavaFX pour afficher
    /// un état « en cours » et désactiver le bouton (l'opération peut être longue sur une grosse nuit).
    private final ReadOnlyBooleanWrapper generationEnCours =
            new ReadOnlyBooleanWrapper(this, "generationEnCours", false);
    /// Lignes de la table de dépôt (#820) : une [LigneArchive] par ZIP, avec son état et sa barre de
    /// progression. Remplace l'ancienne liste de libellés : **source unique** pour l'affichage, les tests
    /// d'existence (bouton supprimer, ouvrir le dossier, rôles) et le suivi par archive (pré-remplie au plan,
    /// mise à jour au fil de la compression parallèle, réhydratée du disque à la réouverture).
    private final SuiviLignesArchives suiviLignes = new SuiviLignesArchives();

    /// Suppression des archives possible (#…) : **liaison vivante** sur les lignes — vraie dès qu'il existe
    /// des archives (régénérables), recalculée quand la liste change (génération, réhydratation, suppression).
    /// Un simple `set()` dans `appliquer` restait périmé après une génération en session, laissant le bouton
    /// inactif à tort.
    private final BooleanBinding peutSupprimerArchives = Bindings.isNotEmpty(suiviLignes.lignes());
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
            suiviLignes.reinitialiser();
            appliquer(service.consulterLot(idPassage));
            message.set("Archives de dépôt supprimées (" + Formats.octetsLisibles(liberes) + " libérés).");
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    /// Génère les **archives ZIP de dépôt** (#110) de façon **synchrone** (tests + CLI) : enchaîne
    /// [#marquerGenerationEnCours()], [#calculerArchivesDepot(Consumer, SuiviArchives)] et
    /// [#appliquerGeneration] / [#echecGeneration]. Ni la progression ni le suivi par archive ne sont
    /// observés ici (callbacks inertes) ; la vue préfère la variante suivie pour exécuter le calcul **hors
    /// fil JavaFX** sans figer l'IHM.
    ///
    /// @return `true` si au moins une archive a été générée
    public boolean genererArchives() {
        if (idPassage == null) {
            return false;
        }
        marquerGenerationEnCours();
        try {
            appliquerGeneration(calculerArchivesDepot(progression -> {}, SuiviArchives.inerte()));
            return true;
        } catch (RuntimeException echec) {
            echecGeneration(echec.getMessage());
            return false;
        }
    }

    /// Passe à l'état « génération en cours » (#251) et l'annonce : **à appeler sur le fil JavaFX**, juste
    /// avant de lancer [#calculerArchivesDepot(Consumer, SuiviArchives)] sur un fil d'arrière-plan. Amorce
    /// aussi le suivi de progression (#769 : barre + estimation de durée) en posant la référence temporelle
    /// de l'ETA, et vide la table (#820) que le plan re-remplira de lignes « en attente ».
    public void marquerGenerationEnCours() {
        generationEnCours.set(true);
        message.set("Génération des archives de dépôt en cours…");
        progression.demarrer("Préparation des archives…");
        suiviLignes.reinitialiser();
    }

    /// Calcule les archives ZIP de dépôt (appel service, potentiellement **long**). **Aucune** mutation de
    /// propriété observable ici : sûr à exécuter **hors fil JavaFX**. `progres` remonte l'avancement global
    /// (#769) et `suivi` le cycle de vie par archive (#820, animation de la table) ; tous deux DOIVENT
    /// relayer leurs mutations au fil JavaFX (`Platform.runLater`, cf. le controller). Le résultat est
    /// appliqué ensuite par [#appliquerGeneration] (sur le fil JavaFX). Sans passage ouvert, liste vide.
    public List<ArchiveDepot> calculerArchivesDepot(Consumer<Progression> progres, SuiviArchives suivi) {
        if (idPassage == null) {
            return List.of();
        }
        return service.genererArchivesDepot(idPassage, progres, suivi);
    }

    /// Applique le résultat d'une génération réussie (#251) : **à appeler sur le fil JavaFX**. Publie la
    /// liste, fait avancer le stepper de ② « Générer » vers ③ « Téléverser », et lève l'état « en cours ».
    public void appliquerGeneration(List<ArchiveDepot> produites) {
        // Harmonise la table à l'état final « terminée » : en génération synchrone (sans suivi) elle la
        // peuple ; en génération suivie (#820), les lignes étaient déjà passées « terminée » au fil des
        // événements, on repose simplement le même état final.
        suiviLignes.afficherTerminees(produites);
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
        // Réhydrate la table des archives depuis le DISQUE (#…) : à la réouverture d'un passage déjà généré,
        // on réaffiche les archives présentes dans depot/ (lignes « terminées » : réactive « Ouvrir le
        // dossier » / « Supprimer »), là où l'ancienne liste, seulement peuplée en session, restait vide.
        suiviLignes.afficherTerminees(service.archivesDepot(etat.cheminDossier()));
        peutPreparer.set(etat.statut() == StatutWorkflow.VERIFIE && !etat.aDesEchecs());
        peutDeposer.set(etat.statut() == StatutWorkflow.PRET_A_DEPOSER);
        depose.set(etat.statut() == StatutWorkflow.DEPOSE);
        // Les archives de dépôt (#110) se génèrent dès que le lot est prêt (séquences figées) : Prêt à
        // déposer ou déjà déposé.
        peutGenererArchives.set(
                etat.statut() == StatutWorkflow.PRET_A_DEPOSER || etat.statut() == StatutWorkflow.DEPOSE);
        // (peutSupprimerArchives est une liaison vivante sur les lignes : rien à poser ici.)
        majEspaceDisque(etat);
        majEtapes(etat.statut());
        message.set(FormatsLot.messageEtat(etat));
    }

    /// Anticipe l'espace disque (#…) **au chargement** : compare la taille estimée des archives (compression
    /// comprise, via le service) à l'espace disponible, pour désactiver « Générer » et l'expliquer AVANT le
    /// clic. Indéterminé (génération non pertinente, volume/chemin inconnu, disque illisible) → on ne bloque
    /// pas (`suffisant`).
    private void majEspaceDisque(EtatLot etat) {
        boolean generationPertinente =
                etat.statut() == StatutWorkflow.PRET_A_DEPOSER || etat.statut() == StatutWorkflow.DEPOSE;
        Long volume = etat.volumeSequencesOctets();
        long disponible = service.espaceDisqueDisponible(etat.cheminDossier());
        long requis = volume == null ? 0L : service.estimationTailleDepotOctets(volume);
        boolean insuffisant = generationPertinente && volume != null && disponible > 0 && disponible < requis;
        espaceDepotSuffisant.set(!insuffisant);
        raisonEspaceInsuffisant.set(insuffisant ? FormatsLot.messageEspaceInsuffisant(requis, disponible) : "");
    }

    /// Recompose le stepper du dépôt (#251) depuis [EtapesDepot], selon le statut et la génération
    /// d'archives. Appelé à chaque (re)chargement d'état et après une génération.
    private void majEtapes(StatutWorkflow statut) {
        etapes.setAll(EtapesDepot.calculer(statut, !suiviLignes.lignes().isEmpty()));
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
        espaceDepotSuffisant.set(true);
        raisonEspaceInsuffisant.set("");
        generationEnCours.set(false);
        suiviLignes.reinitialiser(); // repasse peutSupprimerArchives (liaison vivante) à false
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

    /// `true` si l'espace disque estimé suffit pour générer les archives (#…) ; faux → « Générer » désactivé
    /// et [#raisonEspaceInsuffisantProperty] explique pourquoi.
    public ReadOnlyBooleanProperty espaceDepotSuffisantProperty() {
        return espaceDepotSuffisant.getReadOnlyProperty();
    }

    /// Explication (non vide) quand l'espace disque est jugé insuffisant pour la génération, affichée en
    /// alerte près du bouton « Générer ». Vide si l'espace suffit ou est indéterminé.
    public ReadOnlyStringProperty raisonEspaceInsuffisantProperty() {
        return raisonEspaceInsuffisant.getReadOnlyProperty();
    }

    /// `true` dès qu'il existe des archives ZIP à supprimer (liaison vivante sur la liste des archives), pour
    /// libérer l'espace disque — les archives restant régénérables à l'identique.
    public BooleanExpression peutSupprimerArchivesProperty() {
        return peutSupprimerArchives;
    }

    /// `true` pendant la génération des archives (#251) : la vue affiche un état « en cours » et
    /// désactive le bouton (l'opération s'exécute hors fil JavaFX et peut être longue).
    public ReadOnlyBooleanProperty generationEnCoursProperty() {
        return generationEnCours.getReadOnlyProperty();
    }

    /// Suivi **par archive** de dépôt (#820) : ses [SuiviLignesArchives#lignes()] (une [LigneArchive] par
    /// ZIP, avec état + barre) alimentent la table ; le controller y relaie aussi le cycle de vie de la
    /// génération (via `Platform.runLater`). Vide tant qu'aucune archive n'existe (session ou disque).
    public SuiviLignesArchives suiviLignes() {
        return suiviLignes;
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

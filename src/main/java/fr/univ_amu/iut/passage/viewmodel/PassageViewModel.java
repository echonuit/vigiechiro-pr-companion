package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.ServiceArchivagePassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran pivot **M-Passage** : fiche d'identité d'un passage, **stepper** de statut
/// workflow et statistiques (volumes, durée enregistrée, nombre de séquences).
///
/// Ouvert sur un `idPassage` + un [ContexteSite] (carré/code/nom fournis par la navigation, pour
/// éviter une dépendance `passage → sites`). Le calcul passe par la projection
/// [ServicePassage#detailPassage(Long)]. VM agnostique de l'IHM (règle ArchUnit
/// `viewmodel_sans_javafx_ui`) : seuls `javafx.beans`/`javafx.collections`. Non-singleton.
public class PassageViewModel {

    private final ServicePassage service;
    private final ServicePurgeOriginaux purge;
    private final ServiceArchivagePassage archivage;
    private final ServiceReactivationPassage reactivation;

    private final ReadOnlyStringWrapper titreContexte = new ReadOnlyStringWrapper(this, "titreContexte", "");
    private final ReadOnlyStringWrapper plageHoraire = new ReadOnlyStringWrapper(this, "plageHoraire", "");
    private final ReadOnlyStringWrapper enregistreur = new ReadOnlyStringWrapper(this, "enregistreur", "");
    private final ReadOnlyObjectWrapper<StatutWorkflow> statut = new ReadOnlyObjectWrapper<>(this, "statut");
    private final ReadOnlyObjectWrapper<Verdict> verdict = new ReadOnlyObjectWrapper<>(this, "verdict");
    private final ReadOnlyStringWrapper volumeBruts = new ReadOnlyStringWrapper(this, "volumeBruts", "");
    private final ReadOnlyStringWrapper volumeTransformes = new ReadOnlyStringWrapper(this, "volumeTransformes", "");
    private final ReadOnlyStringWrapper dureeEnregistree = new ReadOnlyStringWrapper(this, "dureeEnregistree", "");
    private final ReadOnlyIntegerWrapper nombreSequences = new ReadOnlyIntegerWrapper(this, "nombreSequences", 0);
    private final ObservableList<EtapeWorkflow> etapes = FXCollections.observableArrayList();
    private final ReadOnlyBooleanWrapper verificationDisponible =
            new ReadOnlyBooleanWrapper(this, "verificationDisponible", false);
    private final ReadOnlyBooleanWrapper validationVerrouillee =
            new ReadOnlyBooleanWrapper(this, "validationVerrouillee", true);
    private final ReadOnlyBooleanWrapper depotDisponible = new ReadOnlyBooleanWrapper(this, "depotDisponible", false);
    private final ReadOnlyBooleanWrapper annulationDepotDisponible =
            new ReadOnlyBooleanWrapper(this, "annulationDepotDisponible", false);
    private final ReadOnlyBooleanWrapper suppressionPossible =
            new ReadOnlyBooleanWrapper(this, "suppressionPossible", false);
    private final ReadOnlyBooleanWrapper renommagePossible =
            new ReadOnlyBooleanWrapper(this, "renommagePossible", false);
    private final ReadOnlyBooleanWrapper purgeDisponible = new ReadOnlyBooleanWrapper(this, "purgeDisponible", false);
    private final ReadOnlyBooleanWrapper archivagePossible =
            new ReadOnlyBooleanWrapper(this, "archivagePossible", false);
    private final ReadOnlyStringWrapper motifBlocageArchivage =
            new ReadOnlyStringWrapper(this, "motifBlocageArchivage", "");
    private final ReadOnlyBooleanWrapper reactivationPossible =
            new ReadOnlyBooleanWrapper(this, "reactivationPossible", false);
    private final ReadOnlyStringWrapper motifBlocageReactivation =
            new ReadOnlyStringWrapper(this, "motifBlocageReactivation", "");
    private final ReadOnlyObjectWrapper<ActionRecommandee> actionRecommandee =
            new ReadOnlyObjectWrapper<>(this, "actionRecommandee", ActionRecommandee.AUCUNE);
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    /// Identifiant du passage affiché, mémorisé pour les actions (ex. suppression).
    private Long idPassage;

    /// Numéro de passage dans l'année (R3), pour le libellé du fil d'Ariane ; 0 tant qu'aucun passage
    /// n'est chargé.
    private int numeroPassage;

    public PassageViewModel(
            ServicePassage service,
            ServicePurgeOriginaux purge,
            ServiceArchivagePassage archivage,
            ServiceReactivationPassage reactivation) {
        this.service = Objects.requireNonNull(service, "service");
        this.purge = Objects.requireNonNull(purge, "purge");
        this.archivage = Objects.requireNonNull(archivage, "archivage");
        this.reactivation = Objects.requireNonNull(reactivation, "reactivation");
    }

    /// Ouvre l'écran sur le passage `idPassage` en **synchrone**, composition de [#charger] +
    /// [#appliquer] : conservée pour les enchaînements d'actions déjà sur le fil JavaFX (rechargement
    /// après suppression refusée, annulation du dépôt, purge). L'ouverture d'écran passe, elle, par le
    /// couple charger/appliquer sous le voile d'occupation (#1213). Une erreur (passage introuvable)
    /// est restituée dans [#messageProperty()] sans lever.
    public void ouvrirSur(Long idPassage, ContexteSite contexte) {
        try {
            appliquer(idPassage, charger(idPassage), contexte);
        } catch (RuntimeException echec) {
            signalerErreur(idPassage, echec);
        }
    }

    /// Lit la projection du passage, **hors du fil JavaFX** (lecture base + agrégats de la nuit) :
    /// aucune propriété observable n'est touchée ici. Le résultat s'applique via [#appliquer] ; un
    /// échec remonte à l'appelant (routé vers [#signalerErreur] par l'exécuteur).
    public DetailPassage charger(Long idPassage) {
        return service.detailPassage(idPassage);
    }

    /// Applique la projection aux propriétés observables, **sur le fil JavaFX**.
    public void appliquer(Long idPassage, DetailPassage detail, ContexteSite contexte) {
        this.idPassage = idPassage;
        appliquer(detail, contexte);
        message.set("");
    }

    /// Route un échec de chargement vers le message de l'écran (#795), **sur le fil JavaFX** : la
    /// fiche est réinitialisée pour ne pas exposer l'état d'un autre passage.
    public void signalerErreur(Long idPassage, Throwable erreur) {
        this.idPassage = idPassage;
        reinitialiser();
        message.set(erreur.getMessage());
    }

    /// Supprime le passage courant (action « Supprimer » de M-Passage). Délègue à
    /// [ServicePassage#supprimer] ; la [fr.univ_amu.iut.commun.model.RegleMetierException] d'un
    /// passage déposé remonte à la vue, qui l'affiche (même patron que la suppression d'un site).
    public void supprimer() {
        service.supprimer(idPassage);
    }

    /// Annule le dépôt du passage courant : le ramène de « Déposé » à « Prêt à déposer » sans supprimer
    /// les validations Tadarida déjà saisies. Délègue à [ServicePassage#annulerDepot] ; la
    /// [fr.univ_amu.iut.commun.model.RegleMetierException] d'un passage non déposé remonte à la vue, qui
    /// l'affiche. Le rechargement de l'affichage est à la charge de l'appelant (rejeu de [#ouvrirSur]).
    public void annulerDepot() {
        service.annulerDepot(idPassage);
    }

    /// Purge les **originaux** (`bruts/`) du passage courant pour récupérer l'espace disque : supprime les
    /// fichiers via [ServicePurgeOriginaux] (socle) puis marque les originaux purgés en base
    /// ([ServicePassage#marquerOriginauxPurges]). Les séquences transformées, la validation et le dépôt
    /// sont **conservés**. Le rechargement de l'affichage (volume bruts → 0) est à la charge de l'appelant
    /// (rejeu de [#ouvrirSur]).
    public void purgerOriginaux() {
        service.cheminSession(idPassage).ifPresent(purge::purgerSession);
        service.marquerOriginauxPurges(idPassage);
    }

    /// Espace récupérable par l'archivage du passage courant (annonce avant confirmation, #1300).
    public long volumeArchivable() {
        return archivage.volumeRecuperable(idPassage);
    }

    /// Séquences du passage courant encore sans empreinte (#1299) : annoncé avant la confirmation,
    /// leur identité sera capturée in extremis par l'archivage.
    public int sequencesSansEmpreinte() {
        return archivage.sequencesSansEmpreinte(idPassage);
    }

    /// Archive le passage courant (action « Archiver » de M-Passage, #1300) : purge l'audio, garde
    /// observations et validations. Délègue à [ServiceArchivagePassage#archiver] ; la
    /// [fr.univ_amu.iut.commun.model.RegleMetierException] d'un passage non déposé remonte à la vue,
    /// qui l'affiche. Le rechargement de l'affichage est à la charge de l'appelant.
    public ServiceArchivagePassage.BilanArchivage archiver() {
        return archivage.archiver(idPassage);
    }

    /// Réactive le passage courant depuis `dossierSource` (action « Réactiver » de M-Passage, #1302) :
    /// rebranche les séquences dont le fichier réimporté est **vérifié**, laisse les autres. Appelée
    /// **hors du fil JavaFX** (opération longue) ; le rechargement de l'affichage est à la charge de
    /// l'appelant. La [fr.univ_amu.iut.commun.model.RegleMetierException] (dossier introuvable) remonte
    /// à la vue, qui l'affiche.
    public RapportReactivation reactiver(Path dossierSource, Consumer<Progression> progres) {
        return reactivation.reactiver(idPassage, dossierSource, progres);
    }

    private void appliquer(DetailPassage detail, ContexteSite contexte) {
        // Identité pour la zone gauche de la barre de statut : format unifié « Carré X · Point · N° Z »
        // (socle #1020, harmonisation #1088), au lieu d'un format « / » propre à cet écran. L'année reste
        // visible via la date d'enregistrement (plage horaire).
        titreContexte.set(new ContextePassage(idPassage, detail.numeroPassage(), contexte).identiteStatut());
        numeroPassage = detail.numeroPassage();
        plageHoraire.set(detail.dateEnregistrement() + "  " + detail.heureDebut() + " → " + detail.heureFin());
        enregistreur.set("PR " + detail.idEnregistreur());
        statut.set(detail.statut());
        verdict.set(detail.verdict());
        volumeBruts.set(Formats.octetsLisibles(detail.volumeOriginauxOctets()));
        volumeTransformes.set(Formats.octetsLisibles(detail.volumeSequencesOctets()));
        dureeEnregistree.set(Formats.dureeLisible(detail.dureeEnregistreeSecondes()));
        nombreSequences.set(detail.nombreSequences());
        etapes.setAll(EtapesWorkflow.construire(detail.statut()));
        verificationDisponible.set(detail.statut().ordinal() >= StatutWorkflow.TRANSFORME.ordinal());
        validationVerrouillee.set(detail.statut() != StatutWorkflow.DEPOSE);
        // Accès à l'écran de dépôt (M-Lot) dès le passage vérifié ET **même une fois déposé** (#…) : on doit
        // pouvoir y revenir pour consulter les archives ou les supprimer, sans avoir à annuler le dépôt.
        depotDisponible.set(detail.statut().ordinal() >= StatutWorkflow.VERIFIE.ordinal());
        annulationDepotDisponible.set(detail.statut() == StatutWorkflow.DEPOSE);
        // Suppression bloquée sur un passage déposé (le service la refuse) : on grise le bouton en amont au
        // lieu de laisser l'utilisateur découvrir le refus après la confirmation. Il faut d'abord annuler
        // le dépôt.
        suppressionPossible.set(detail.statut() != StatutWorkflow.DEPOSE);
        // Renommage (rattachement) bloqué dès qu'un passage est déposé ou en cours de dépôt : son nom est
        // l'identité de ses fichiers côté serveur, le service refuse alors le renommage. Gating amont.
        renommagePossible.set(
                detail.statut() != StatutWorkflow.DEPOSE && detail.statut() != StatutWorkflow.DEPOT_EN_COURS);
        // Purge possible tant qu'il reste des originaux sur disque (volume > 0) ; après purge, il tombe à 0.
        purgeDisponible.set(detail.volumeOriginauxOctets() > 0);
        // Archivage (#1300) et réactivation (#1302) : gating en amont (#789), le motif alimente le
        // tooltip de l'enveloppe. Règles pures extraites dans GatingArchive.
        archivagePossible.set(GatingArchive.archivagePossible(detail));
        motifBlocageArchivage.set(GatingArchive.motifArchivage(detail));
        reactivationPossible.set(GatingArchive.reactivationPossible(detail));
        motifBlocageReactivation.set(GatingArchive.motifReactivation(detail));
        actionRecommandee.set(EtapesWorkflow.prochaineAction(detail.statut()));
    }

    private void reinitialiser() {
        titreContexte.set("");
        plageHoraire.set("");
        enregistreur.set("");
        statut.set(null);
        verdict.set(null);
        volumeBruts.set("");
        volumeTransformes.set("");
        dureeEnregistree.set("");
        nombreSequences.set(0);
        numeroPassage = 0;
        etapes.clear();
        verificationDisponible.set(false);
        validationVerrouillee.set(true);
        depotDisponible.set(false);
        annulationDepotDisponible.set(false);
        suppressionPossible.set(false);
        renommagePossible.set(false);
        purgeDisponible.set(false);
        archivagePossible.set(false);
        motifBlocageArchivage.set("");
        reactivationPossible.set(false);
        motifBlocageReactivation.set("");
        actionRecommandee.set(ActionRecommandee.AUCUNE);
    }

    /// Numéro de passage dans l'année (0 si aucun passage chargé), pour le libellé du fil d'Ariane.
    public int getNumeroPassage() {
        return numeroPassage;
    }

    /// Titre d'identité du passage (`Carré 640380 / A1 / N° 2 (2026)`).
    public ReadOnlyStringProperty titreContexteProperty() {
        return titreContexte.getReadOnlyProperty();
    }

    /// Plage horaire de la nuit (`date  début → fin`).
    public ReadOnlyStringProperty plageHoraireProperty() {
        return plageHoraire.getReadOnlyProperty();
    }

    /// Enregistreur (`PR <n° de série>`).
    public ReadOnlyStringProperty enregistreurProperty() {
        return enregistreur.getReadOnlyProperty();
    }

    /// Statut workflow courant du passage.
    public ReadOnlyObjectProperty<StatutWorkflow> statutProperty() {
        return statut.getReadOnlyProperty();
    }

    /// Verdict de vérification, ou `null` tant qu'aucun n'est posé.
    public ReadOnlyObjectProperty<Verdict> verdictProperty() {
        return verdict.getReadOnlyProperty();
    }

    /// Volume des enregistrements bruts, formaté (`Ko`/`Mo`/`Go`).
    public ReadOnlyStringProperty volumeBrutsProperty() {
        return volumeBruts.getReadOnlyProperty();
    }

    /// Volume des séquences transformées, formaté.
    public ReadOnlyStringProperty volumeTransformesProperty() {
        return volumeTransformes.getReadOnlyProperty();
    }

    /// Durée enregistrée cumulée, formatée (`Xh Ymin` ou `X min Y s`).
    public ReadOnlyStringProperty dureeEnregistreeProperty() {
        return dureeEnregistree.getReadOnlyProperty();
    }

    /// Nombre de séquences d'écoute de la session.
    public ReadOnlyIntegerProperty nombreSequencesProperty() {
        return nombreSequences.getReadOnlyProperty();
    }

    /// Étapes du stepper de statut (5 statuts, du plus ancien au dépôt), avec leur état.
    public ObservableList<EtapeWorkflow> etapes() {
        return etapes;
    }

    /// `true` si la vérification par échantillonnage est possible (passage au moins transformé).
    public ReadOnlyBooleanProperty verificationDisponibleProperty() {
        return verificationDisponible.getReadOnlyProperty();
    }

    /// `true` tant que la validation Tadarida est verrouillée (passage non encore déposé).
    public ReadOnlyBooleanProperty validationVerrouilleeProperty() {
        return validationVerrouillee.getReadOnlyProperty();
    }

    /// `true` quand la préparation/dépôt est pertinente (passage Vérifié ou Prêt à déposer).
    public ReadOnlyBooleanProperty depotDisponibleProperty() {
        return depotDisponible.getReadOnlyProperty();
    }

    /// `true` quand l'annulation du dépôt est pertinente (passage déjà **déposé**) : l'action ramène le
    /// passage à « Prêt à déposer » sans toucher aux validations Tadarida déjà saisies.
    public ReadOnlyBooleanProperty annulationDepotDisponibleProperty() {
        return annulationDepotDisponible.getReadOnlyProperty();
    }

    /// `true` quand le passage peut être supprimé (tout statut **sauf** Déposé). Un passage déposé doit
    /// d'abord voir son dépôt annulé ; le bouton « Supprimer » est grisé en conséquence, avec un tooltip
    /// d'explication (cf. [fr.univ_amu.iut.commun.view.IndicateurBlocage]).
    public ReadOnlyBooleanProperty suppressionPossibleProperty() {
        return suppressionPossible.getReadOnlyProperty();
    }

    /// `true` quand le passage peut être renommé (rattachement modifiable) : tout statut **sauf** Déposé
    /// ou Dépôt en cours. Après dépôt, le nom des fichiers est l'identité côté serveur ; le bouton
    /// « Modifier le passage » est grisé, avec un tooltip d'explication (cf.
    /// [fr.univ_amu.iut.commun.view.IndicateurBlocage]).
    public ReadOnlyBooleanProperty renommagePossibleProperty() {
        return renommagePossible.getReadOnlyProperty();
    }

    /// `true` quand des **originaux** sont encore stockés (volume bruts > 0) : la purge est alors proposée
    /// pour récupérer l'espace disque.
    public ReadOnlyBooleanProperty purgeDisponibleProperty() {
        return purgeDisponible.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty archivagePossibleProperty() {
        return archivagePossible.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty motifBlocageArchivageProperty() {
        return motifBlocageArchivage.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty reactivationPossibleProperty() {
        return reactivationPossible.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty motifBlocageReactivationProperty() {
        return motifBlocageReactivation.getReadOnlyProperty();
    }

    /// Prochaine action recommandée du workflow (carte mise en avant), dérivée du statut. Se déplace
    /// au fil de l'avancement : Vérifier → Préparer le dépôt → Sons & validation.
    public ReadOnlyObjectProperty<ActionRecommandee> actionRecommandeeProperty() {
        return actionRecommandee.getReadOnlyProperty();
    }

    /// Message d'erreur (passage introuvable), vide en fonctionnement nominal.
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }
}

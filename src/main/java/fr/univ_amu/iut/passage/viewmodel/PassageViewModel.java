package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.passage.model.ChoixRebranchement;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.RapportReactivation;
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
    private final ServiceReactivationPassage reactivation;
    private final PortailVigieChiro portail;

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
    /// Pourquoi chaque geste de la fiche est fermé, quand il l'est (#789) : quatre motifs qui se
    /// calculent au même moment et répondent à la même question.
    private final MotifsBlocagePassage motifs = new MotifsBlocagePassage();

    private final ReadOnlyBooleanWrapper depotDisponible = new ReadOnlyBooleanWrapper(this, "depotDisponible", false);
    private final ReadOnlyBooleanWrapper annulationDepotDisponible =
            new ReadOnlyBooleanWrapper(this, "annulationDepotDisponible", false);
    private final ReadOnlyBooleanWrapper suppressionPossible =
            new ReadOnlyBooleanWrapper(this, "suppressionPossible", false);
    private final ReadOnlyBooleanWrapper renommagePossible =
            new ReadOnlyBooleanWrapper(this, "renommagePossible", false);
    private final ReadOnlyBooleanWrapper reactivationPossible =
            new ReadOnlyBooleanWrapper(this, "reactivationPossible", false);
    /// #1514 : pourquoi la carte « Vérifier » est grisée quand elle l'est (nuit non transformée, ou
    /// déjà déposée donc verdict figé). Vide quand la vérification est disponible.
    /// Le bouton « Annuler le dépôt » a-t-il sa place sur cette fiche ? Vrai dès que la nuit est déposée
    /// - y compris quand le geste lui-même est refusé (#2771), pour que le refus soit **visible et
    /// expliqué** plutôt que muet.
    private final ReadOnlyBooleanWrapper annulationDepotPertinente =
            new ReadOnlyBooleanWrapper(this, "annulationDepotPertinente", false);

    /// Pourquoi la carte « Préparer le dépôt » est grisée ; vide quand elle ne l'est pas.

    /// Pourquoi « Annuler le dépôt » est grisé (#2771) ; vide quand il ne l'est pas. Gating en amont
    /// (#789) : on explique plutôt que de laisser découvrir le refus après confirmation.

    private final ReadOnlyObjectWrapper<ActionRecommandee> actionRecommandee =
            new ReadOnlyObjectWrapper<>(this, "actionRecommandee", ActionRecommandee.AUCUNE);
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// Identifiant du passage affiché, mémorisé pour les actions (ex. suppression).
    private Long idPassage;

    /// Numéro de passage dans l'année (R3), pour le libellé du fil d'Ariane ; 0 tant qu'aucun passage
    /// n'est chargé.
    private int numeroPassage;

    /// @param portail résolution **locale** du rattachement à une participation Vigie-Chiro (#1124, aucun
    ///     appel réseau) : c'est ce qui dit si une nuit sans séquence a une source d'où les récupérer
    ///     (#2555), et donc si « Réactiver ce passage » a un sens
    public PassageViewModel(
            ServicePassage service, ServiceReactivationPassage reactivation, PortailVigieChiro portail) {
        this.service = Objects.requireNonNull(service, "service");
        this.reactivation = Objects.requireNonNull(reactivation, "reactivation");
        this.portail = Objects.requireNonNull(portail, "portail");
    }

    /// Ouvre l'écran sur le passage `idPassage` en **synchrone**, composition de [#charger] +
    /// [#appliquer] : conservée pour les enchaînements d'actions déjà sur le fil JavaFX (rechargement
    /// après suppression refusée, annulation du dépôt, purge). L'ouverture d'écran passe, elle, par le
    /// couple charger/appliquer sous le voile d'occupation (#1213). Une erreur (passage introuvable)
    /// est restituée dans [#retourProperty()] sans lever.
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
        retour.set(RetourOperation.AUCUN);
    }

    /// Route un échec de chargement vers le bandeau de retour de l'écran (#795), **sur le fil
    /// JavaFX** : la fiche est réinitialisée pour ne pas exposer l'état d'un autre passage.
    public void signalerErreur(Long idPassage, Throwable erreur) {
        this.idPassage = idPassage;
        reinitialiser();
        retour.set(RetourOperation.erreur(erreur));
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

    /// Réactive le passage courant depuis `dossierSource` (action « Réactiver » de M-Passage, #1302) :
    /// rebranche les séquences dont le fichier réimporté est **vérifié**, laisse les autres. Appelée
    /// **hors du fil JavaFX** (opération longue) ; le rechargement de l'affichage est à la charge de
    /// l'appelant. La [fr.univ_amu.iut.commun.model.RegleMetierException] (dossier introuvable) remonte
    /// à la vue, qui l'affiche.
    public RapportReactivation reactiver(Path dossierSource, Consumer<Progression> progres) {
        return reactivation.reactiver(idPassage, dossierSource, progres);
    }

    /// Variante **suivie et annulable** (#1597) à un seul consommateur : le `jeton` interrompt la phase
    /// d'ancrage (le ré-import des `donnees` d'un passage reconstruit peut durer plusieurs dizaines de
    /// secondes). Les deux phases y reportent au même `progres`.
    public RapportReactivation reactiver(Path dossierSource, Consumer<Progression> progres, JetonAnnulation jeton) {
        return reactivation.reactiver(idPassage, dossierSource, progres, jeton);
    }

    /// Variante à **deux progressions** (#1780) : la modale de réactivation suit séparément la régénération
    /// des séquences (`progresRegeneration`) et l'acquisition de l'ancrage (`progresAncrage`), chacune sur sa
    /// barre. Appelée **hors du fil JavaFX** ; le `jeton` interrompt à la prochaine frontière / page.
    public RapportReactivation reactiver(
            Path dossierSource,
            Consumer<Progression> progresRegeneration,
            Consumer<Progression> progresAncrage,
            JetonAnnulation jeton) {
        return reactivation.reactiver(idPassage, dossierSource, progresRegeneration, progresAncrage, jeton);
    }

    /// Variante à **mode** (#2255) : copier les fichiers vérifiés dans l'espace de travail, ou laisser
    /// l'audio où il vit et faire suivre la base.
    public RapportReactivation reactiver(
            Path dossierSource,
            ChoixRebranchement choix,
            Consumer<Progression> progresRegeneration,
            Consumer<Progression> progresAncrage,
            JetonAnnulation jeton) {
        return reactivation.reactiver(idPassage, dossierSource, choix, progresRegeneration, progresAncrage, jeton);
    }

    /// Ce dossier appartient-il à l'utilisateur plutôt qu'à l'application (#2255) ? Sert à **proposer**
    /// le bon mode sans l'imposer.
    public boolean horsEspaceDeTravail(Path dossier) {
        return reactivation.horsEspaceDeTravail(dossier);
    }

    private void appliquer(DetailPassage detail, ContexteSite contexte) {
        // Identité pour la zone gauche de la barre de statut : format unifié « Carré X · Point · N° Z »
        // (socle #1020, harmonisation #1088), au lieu d'un format « / » propre à cet écran. L'année reste
        // visible via la date d'enregistrement (plage horaire).
        titreContexte.set(new ContextePassage(idPassage, detail.numeroPassage(), contexte).identiteStatut());
        numeroPassage = detail.numeroPassage();
        plageHoraire.set(detail.dateEnregistrement() + "  " + detail.heureDebut() + " -> " + detail.heureFin());
        enregistreur.set("PR " + detail.idEnregistreur());
        statut.set(detail.statut());
        verdict.set(detail.verdict());
        volumeBruts.set(Formats.octetsLisibles(detail.volumeOriginauxOctets()));
        volumeTransformes.set(Formats.octetsLisibles(detail.volumeSequencesOctets()));
        dureeEnregistree.set(Formats.dureeLisible(detail.dureeEnregistreeSecondes()));
        nombreSequences.set(detail.nombreSequences());
        etapes.setAll(EtapesWorkflow.construire(detail.statut()));
        // ⚠️ Comparaison par ordinal : elle ne vaut QUE pour les statuts de la file. « Récupéré » est
        // hors file et déclaré en dernier (ADR 2581), donc il passerait ce test sans rien avoir
        // parcouru - une nuit rapatriée n'a jamais été transformée ICI. Le dire, plutôt que de laisser
        // la position dans l'énumération répondre à notre place.
        boolean nuitTransformee = detail.statut() != StatutWorkflow.RECUPERE
                && detail.statut().ordinal() >= StatutWorkflow.TRANSFORME.ordinal();
        boolean nuitDeposee = detail.statut() == StatutWorkflow.DEPOSE;
        // Le statut porte la distinction depuis #2772 : l'écran la LIT dans le détail qu'il vient de
        // charger, au lieu de redemander au service - une requête sur trois tables à chaque ouverture
        // de fiche, pour une réponse qu'il tenait déjà.
        boolean nuitRecuperee = detail.statut() == StatutWorkflow.RECUPERE;
        // ⚠️ « Récupéré » et « Déposé » sont désormais EXCLUSIFS. Toute garde qui disait « sauf quand
        // c'est déposé » cessait donc de couvrir ces nuits, en silence, alors qu'elle les couvrait la
        // veille - le statut a changé sous elles. Chacune est reprise ci-dessous avec ce que la nuit
        // est vraiment : sur la plateforme, quel que soit celui de nous deux qui l'y a mise.
        boolean surLaPlateforme = nuitDeposee || nuitRecuperee;
        // #1514 : la vérification reste possible tant que la nuit n'est pas déposée (une nuit déposée a un
        // verdict figé, cf. ServiceQualification.enregistrerVerdict) : on grise donc la carte au dépôt.
        verificationDisponible.set(nuitTransformee && !surLaPlateforme);
        // Le verdict reste figé sur une nuit récupérée - elle EST sur la plateforme, et un verdict local
        // divergent la désynchroniserait tout autant. Mais le motif d'origine lui fait dire « cette nuit
        // est déposée » à quelqu'un qui ne l'a jamais déposée : il dit désormais d'où elle vient.
        String motifVerification = nuitRecuperee
                ? "Cette nuit vient de Vigie-Chiro, où elle est déjà déposée : son verdict s'y décide," + " pas ici."
                : nuitDeposee
                        ? "Verdict figé : cette nuit est déposée, son verdict ne change plus."
                        : nuitTransformee ? "" : "La vérification sera possible une fois la nuit transformée.";
        validationVerrouillee.set(!surLaPlateforme);
        // Accès à l'écran de dépôt (M-Lot) dès le passage vérifié ET **même une fois déposé** (#…) : on doit
        // pouvoir y revenir pour consulter les archives ou les supprimer, sans avoir à annuler le dépôt.
        // Une nuit récupérée n'a **rien à préparer** : elle est déjà sur la plateforme, et la préparation
        // la refuse (« déjà déposé »). Elle n'a pas non plus d'archives locales à consulter - elle n'a
        // jamais été déposée d'ici. La carte se ferme donc, avec son motif, plutôt que de conduire à un
        // refus découvert après coup (#789).
        //
        // ⚠️ La comparaison par ordinal ne vaut que pour les statuts de la file : « Récupéré », déclaré
        // en dernier, y répondrait « oui » par sa seule position (ADR 2581).
        depotDisponible.set(detail.statut() != StatutWorkflow.RECUPERE
                && detail.statut().ordinal() >= StatutWorkflow.VERIFIE.ordinal());
        String motifDepot = nuitRecuperee
                ? "Cette nuit vient de Vigie-Chiro, où elle est déjà déposée : il n'y a rien à y préparer"
                        + " ni à y téléverser."
                : "";
        // Sauf sur une nuit récupérée (#2771) : elle n'a pas de dépôt à annuler ici, et la transition
        // la rendrait « Prêt à déposer » - donc prête à faire un doublon sur la plateforme.
        // Deux questions distinctes. « Le bouton a-t-il sa place ici ? » - oui dès que la nuit est
        // déposée, sinon il disparaît (il n'a aucun sens avant le dépôt). « Le geste est-il possible ? »
        // - non sur une nuit récupérée, et là il reste VISIBLE mais désactivé, avec son motif : c'est
        // précisément la nuit où son absence surprendrait, puisque la pastille annonce « Déposé ».
        annulationDepotPertinente.set(surLaPlateforme);
        annulationDepotDisponible.set(nuitDeposee);
        String motifAnnulation = nuitRecuperee ? ServicePassage.MOTIF_DEPOT_NON_ANNULABLE : "";
        // Suppression bloquée sur un passage déposé (le service la refuse) : on grise le bouton en amont au
        // lieu de laisser l'utilisateur découvrir le refus après la confirmation. Il faut d'abord annuler
        // le dépôt.
        // Sauf une nuit récupérée (#2581) : nous ne l'avons pas déposée, nous l'avons reçue. La supprimer
        // enlève une copie locale, pas une donnée officielle - la participation reste sur la plateforme.
        // Sans cette exception, le seul recours était « Annuler le dépôt » puis « Supprimer », qui fait
        // affirmer un faux : la nuit EST déposée, et aucun geste local ne le change.
        suppressionPossible.set(!nuitDeposee);
        // Renommage (rattachement) bloqué dès qu'un passage est déposé ou en cours de dépôt : son nom est
        // l'identité de ses fichiers côté serveur, le service refuse alors le renommage. Gating amont.
        renommagePossible.set(!surLaPlateforme && detail.statut() != StatutWorkflow.DEPOT_EN_COURS);
        // Réactivation (#1302) : gating en amont (#789), le motif alimente le tooltip de
        // l'enveloppe. Règles pures extraites dans GatingReactivation.
        // Depuis #2555, une nuit SANS séquence peut aussi se réactiver : c'est une nuit rapatriée par la
        // synchro (ADR 0016), dont la réactivation ira d'abord chercher ses observations. La règle a donc
        // besoin de savoir d'où l'audio pourrait revenir - deux faits que le détail ne porte pas.
        GatingReactivation.ContexteReactivation contexteReactivation = new GatingReactivation.ContexteReactivation(
                portail.pageParticipation(idPassage).isPresent(), reactivation.hydratationDisponible());
        reactivationPossible.set(GatingReactivation.reactivationPossible(detail, contexteReactivation));
        // Les quatre motifs se posent ensemble : ils répondent à la même question, sur quatre gestes.
        motifs.appliquer(
                motifVerification,
                GatingReactivation.motifReactivation(detail, contexteReactivation),
                motifDepot,
                motifAnnulation);
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
        annulationDepotPertinente.set(false);
        validationVerrouillee.set(true);
        depotDisponible.set(false);
        annulationDepotDisponible.set(false);
        suppressionPossible.set(false);
        renommagePossible.set(false);
        reactivationPossible.set(false);
        motifs.effacer();
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

    /// Les motifs de blocage des gestes de la fiche (#789) : `motifs().depot()`, `motifs().verification()`…
    public MotifsBlocagePassage motifs() {
        return motifs;
    }

    /// `true` quand l'annulation du dépôt est pertinente (passage déjà **déposé**) : l'action ramène le
    /// passage à « Prêt à déposer » sans toucher aux validations Tadarida déjà saisies.
    public ReadOnlyBooleanProperty annulationDepotDisponibleProperty() {
        return annulationDepotDisponible.getReadOnlyProperty();
    }

    /// Le bouton « Annuler le dépôt » a-t-il sa place ici (#2771) ? Pilote sa **présence**, quand
    /// [#annulationDepotDisponibleProperty] pilote son **activation**.
    public ReadOnlyBooleanProperty annulationDepotPertinenteProperty() {
        return annulationDepotPertinente.getReadOnlyProperty();
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

    public ReadOnlyBooleanProperty reactivationPossibleProperty() {
        return reactivationPossible.getReadOnlyProperty();
    }

    /// Prochaine action recommandée du workflow (carte mise en avant), dérivée du statut. Se déplace
    /// au fil de l'avancement : Vérifier → Préparer le dépôt → Sons & validation.
    public ReadOnlyObjectProperty<ActionRecommandee> actionRecommandeeProperty() {
        return actionRecommandee.getReadOnlyProperty();
    }

    /// Compte rendu de l'ouverture de la fiche (passage introuvable), rendu par le bandeau partagé
    /// (ADR 0023). Vaut [RetourOperation#AUCUN] en fonctionnement nominal.
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Publie le refus d'une action **réversible** dans le bandeau de l'écran (ADR 0023) : rien n'a été
    /// détruit, donc rien ne justifie de bloquer l'utilisateur. Les refus d'actions irréversibles
    /// (suppression) restent modaux et ne passent pas par ici.
    public void signalerRefus(String motif) {
        retour.set(RetourOperation.erreur(motif));
    }

    /// Efface le retour (l'utilisateur a lu le bandeau et le ferme).
    public void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }
}

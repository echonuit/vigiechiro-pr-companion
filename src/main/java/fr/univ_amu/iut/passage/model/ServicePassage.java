package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Alerte;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.JournalMutations;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.ResultatVerification;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// Service métier central de la feature `passage` : lecture/détail d'un passage, création,
/// vérifications de protocole (R3/R4/R5), pilotage du workflow et pose du verdict. Calqué sur le
/// service de référence `ServiceSites` (cf. SERVICE-CONVENTIONS).
///
/// Les responsabilités voisines vivent dans leurs services dédiés (#1192) : les **conditions de la
/// nuit** (météo, matériel du micro) dans [ServiceConditionsPassage], le **rattachement rétroactif**
/// (re-préfixage disque + base) dans [ServiceRattachement]. La règle R5, partagée avec le
/// rattachement, vit dans [UniciteQuadruplet].
///
/// Principes repris du patron :
///
/// - **Pure Java, sans aucun import JavaFX** : la logique vit en `passage.model`, l'IHM viendra
/// par-dessus (contrôlé par `ArchitectureTest`).
/// - **Reçoit ses dépendances par constructeur** ([PassageDao], [MoteurWorkflowPassage],
/// [Horloge]), assemblées par `PassageModule` en production et instanciées à la main dans les
/// tests.
/// - **Distingue règles soft et dures** : R5 (unicité du quadruplet) et les transitions de
/// workflow interdites lèvent une [RegleMetierException] ; R3 (fenêtre saisonnière) et R4
/// (intervalle < 1 mois) renvoient un [ResultatVerification] d'alertes **non bloquantes**.
/// - **Dates via l'[Horloge] injectée** : aucune `LocalDate.now()` en dur (tests déterministes).
///
/// **Découplage inter-feature assumé.** Les règles R3/R4 ne concernent que les sites en mode
/// [Protocole#STANDARD] (`PointFixeStandard`). Le service **ne résout pas** le protocole en
/// remontant `passage → point → site` : cela créerait une dépendance `passage → sites` alors que
/// `sites → passage` existe déjà (`ServiceSites` lit `PassageDao`), donc un **cycle** que
/// `ArchitectureTest` refuse. Le [Protocole] est donc **passé en paramètre** par l'appelant (le
/// `viewmodel`, qui connaît le site courant) : exactement comme
/// `ServiceSites.rappelsProtocole(Protocole)`.
public class ServicePassage {

    /// Nom du paramètre `passage` (messages `requireNonNull`).
    private static final String PASSAGE = "passage";

    /// Nom du paramètre `idPassage` (messages `requireNonNull`).
    private static final String ID_PASSAGE = "idPassage";

    private final PassageDao passageDao;
    private final JournalMutations journal;
    private final MoteurWorkflowPassage moteur;
    private final Horloge horloge;
    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;

    /// Disponibilité **observée** de l'audio (#1298) : la fiche du passage la porte pour que l'IHM
    /// gate l'écoute et la réactivation (#1302) sans balayer le disque elle-même.
    private final ServiceDisponibiliteAudio disponibilite;
    private final UniciteQuadruplet unicite;

    /// Marquage opportuniste des passages (#2525) : un passage réalisé sur le carré d'un tiers est
    /// **exempté de R3 et R4** (ni contrainte de date, ni de fréquence). La table latérale
    /// `passage_opportuniste` porte ce fait hors du record [Passage].
    private final PassageOpportunisteDao opportunistes;

    public ServicePassage(
            PassageDao passageDao,
            MoteurWorkflowPassage moteur,
            Horloge horloge,
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            ServiceDisponibiliteAudio disponibilite,
            PassageOpportunisteDao opportunistes,
            JournalMutations journal) {
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.moteur = Objects.requireNonNull(moteur, "moteur");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.disponibilite = Objects.requireNonNull(disponibilite, "disponibilite");
        this.opportunistes = Objects.requireNonNull(opportunistes, "opportunistes");
        this.unicite = new UniciteQuadruplet(passageDao);
    }

    /// Disponibilité **ré-observée** de l'audio du passage : la fiche doit refléter le disque au
    /// moment où on l'ouvre (archivage #1300 ou réactivation #1302 entre-temps, disque rebranché),
    /// d'où l'invalidation du cache avant lecture. Balayage groupé : un accès disque par dossier.
    private DecompteAudio decompteAudio(Long idPassage) {
        disponibilite.invalider(idPassage);
        return disponibilite.decompte(idPassage);
    }

    /// Nombre total de passages (compteur du tableau de bord d'accueil).
    public long compterPassages() {
        return passageDao.compter();
    }

    /// Projection de lecture pour l'écran **M-Passage** : le passage `idPassage` et les agrégats de
    /// sa session (volumes, durée enregistrée, nombre de séquences). Sans jointure `sites` : le contexte
    /// site (carré, code point) est fourni à la vue par la navigation.
    ///
    /// @throws RegleMetierException si le passage est introuvable
    public DetailPassage detailPassage(Long idPassage) {
        Passage passage = charger(idPassage);
        Optional<SessionDEnregistrement> session = sessionDao.trouverParPassage(idPassage);
        List<SequenceDEcoute> sequences =
                session.map(s -> sequenceDao.findBySession(s.id())).orElseGet(List::of);
        double dureeEnregistree = sequences.stream()
                .map(SequenceDEcoute::dureeSecondes)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        return new DetailPassage(
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.idEnregistreur(),
                passage.statutWorkflow(),
                passage.verdictVerification(),
                passage.deposeLe(),
                session.map(SessionDEnregistrement::volumeOriginauxOctets).orElse(0L),
                session.map(SessionDEnregistrement::volumeSequencesOctets).orElse(0L),
                sequences.size(),
                dureeEnregistree,
                MeteoPassage.lire(passage.donneesMeteo()),
                decompteAudio(idPassage));
    }

    /// Dossier de session (`root_path`) d'un passage, s'il en a une : sert à **localiser** ses fichiers
    /// sur disque. [Optional#empty()] si le passage n'a pas de session.
    public Optional<Path> cheminSession(Long idPassage) {
        Objects.requireNonNull(idPassage, ID_PASSAGE);
        return sessionDao.trouverParPassage(idPassage).map(s -> Path.of(s.cheminRacine()));
    }

    /// Crée un passage à l'état initial [StatutWorkflow#IMPORTE], sans verdict.
    ///
    /// - R5 (dur) : refuse si le quadruplet `(point, année, n° de passage)` existe déjà :
    /// pré-vérifié via [UniciteQuadruplet] (filet : contrainte `UNIQUE` du schéma).
    /// - Année : déduite de la date d'enregistrement. Si `dateEnregistrement` est `null`, on prend
    /// la date du jour de l'[Horloge] (déterministe en test).
    ///
    /// @param idPoint point d'écoute rattaché (FK `listening_point.id`)
    /// @param idEnregistreur n° de série de l'enregistreur (FK `recorder.serial_number`)
    /// @param numeroPassage n° de passage dans l'année (typiquement 1 ou 2)
    /// @param dateEnregistrement date du soir d'enregistrement, ou `null` pour « aujourd'hui »
    /// @return le passage inséré, avec son `id` auto-généré
    /// @throws RegleMetierException si le quadruplet existe déjà (R5)
    public Passage creerPassage(
            Long idPoint,
            String idEnregistreur,
            int numeroPassage,
            LocalDate dateEnregistrement,
            String heureDebut,
            String heureFin,
            String parametresAcquisition,
            String commentaire,
            String donneesMeteo) {
        Objects.requireNonNull(idPoint, "idPoint");
        LocalDate date = dateEnregistrement != null ? dateEnregistrement : horloge.aujourdhui();
        int annee = date.getYear();
        unicite.exiger(idPoint, annee, numeroPassage); // R5
        Passage aCreer = new Passage(
                null,
                numeroPassage,
                annee,
                date.toString(),
                heureDebut,
                heureFin,
                parametresAcquisition,
                StatutWorkflow.IMPORTE,
                null,
                commentaire,
                donneesMeteo,
                null,
                idPoint,
                idEnregistreur,
                null);
        Passage cree = passageDao.insert(aCreer);
        journal.mutationStructurelleValidee();
        return cree;
    }

    /// Vérifications de protocole non bloquantes (R3 + R4) à présenter à l'utilisateur après saisie
    /// d'un passage. Accumule les alertes des deux règles dans un seul [ResultatVerification]
    /// (patron d'accumulation immuable et fluente, cf. SERVICE-CONVENTIONS §2.3).
    ///
    /// Sur un site [Protocole#RECHERCHE], les deux règles sont muettes : le résultat est conforme.
    public ResultatVerification verifierProtocole(Passage passage, Protocole protocole) {
        ResultatVerification resultat = verifierFenetreSaisonniere(passage, protocole);
        for (Alerte alerte : verifierIntervalleEntrePassages(passage, protocole).alertes()) {
            resultat = resultat.avec(alerte);
        }
        return resultat;
    }

    /// Un passage **opportuniste** (#2525, réalisé sur le carré d'un tiers) est hors protocole Point
    /// Fixe : R3 et R4 le laissent muet. Un passage non encore persisté (`id` nul) ne peut pas être
    /// marqué : il est traité comme normal.
    private boolean estOpportuniste(Passage passage) {
        return passage.id() != null && opportunistes.estOpportuniste(passage.id());
    }

    /// (Dé)marque le passage `idPassage` comme participation opportuniste (#2525). Façade du service sur
    /// [PassageOpportunisteDao#definir] : le concept vit avec les règles R3/R4 qu'il neutralise. Point
    /// d'entrée des surfaces de saisie (import, modale « Modifier le passage »).
    public void marquerOpportuniste(Long idPassage, boolean opportuniste) {
        Objects.requireNonNull(idPassage, ID_PASSAGE);
        opportunistes.definir(idPassage, opportuniste);
    }

    /// Le passage `idPassage` est-il marqué opportuniste (#2525) ? Lecture pour les surfaces de saisie
    /// (case de la modale « Modifier le passage »).
    public boolean estOpportuniste(Long idPassage) {
        Objects.requireNonNull(idPassage, ID_PASSAGE);
        return opportunistes.estOpportuniste(idPassage);
    }

    /// R3 (soft, `PointFixeStandard` uniquement) : le passage 1 est attendu entre le 15 juin et le
    /// 31 juillet, le passage 2 entre le 15 août et le 30 septembre. Hors fenêtre → alerte non
    /// bloquante. Sur [Protocole#RECHERCHE], ou pour un n° de passage sans fenêtre définie (autre
    /// que 1 ou 2), la règle est muette.
    public ResultatVerification verifierFenetreSaisonniere(Passage passage, Protocole protocole) {
        Objects.requireNonNull(passage, PASSAGE);
        if (estOpportuniste(passage)) {
            return ResultatVerification.ok(); // participation opportuniste : hors fenêtre R3
        }
        if (protocole != Protocole.STANDARD || passage.dateEnregistrement() == null) {
            return ResultatVerification.ok();
        }
        Optional<FenetreSaisonniere> fenetre = FenetreSaisonniere.pour(passage.numeroPassage(), passage.annee());
        if (fenetre.isEmpty()) {
            return ResultatVerification.ok();
        }
        LocalDate date = LocalDate.parse(passage.dateEnregistrement());
        if (fenetre.get().contient(date)) {
            return ResultatVerification.ok();
        }
        return ResultatVerification.de(Alerte.soft("Le passage n°"
                + passage.numeroPassage()
                + " du "
                + date
                + " est hors de la fenêtre attendue ["
                + fenetre.get().debut()
                + " -> "
                + fenetre.get().fin()
                + "] pour un site PointFixeStandard. Alerte non bloquante."));
    }

    /// R4 (soft, `PointFixeStandard` uniquement) : l'intervalle conseillé entre les deux passages
    /// d'un même point dans la même année est d'au moins 1 mois. Si un autre passage du même point
    /// (même année, n° différent) est à moins d'un mois, une alerte non bloquante est émise.
    ///
    /// Granularité : la règle est évaluée **par point d'écoute** (et non par site). C'est la maille
    /// atteignable depuis la feature `passage` sans dépendre de `sites` (cf. la note de découplage
    /// de cette classe) ; un passage appartenant à exactement un point, comparer ses frères de point
    /// est une lecture fidèle de la règle. Sur [Protocole#RECHERCHE], muette.
    public ResultatVerification verifierIntervalleEntrePassages(Passage passage, Protocole protocole) {
        Objects.requireNonNull(passage, PASSAGE);
        if (estOpportuniste(passage)) {
            return ResultatVerification.ok(); // participation opportuniste : hors intervalle R4
        }
        if (protocole != Protocole.STANDARD || passage.dateEnregistrement() == null) {
            return ResultatVerification.ok();
        }
        LocalDate dateCourante = LocalDate.parse(passage.dateEnregistrement());
        // Les passages opportunistes du point ne sont pas des passages protocolaires : on ne les
        // compare pas (un traitement groupé évite une requête par voisin).
        Set<Long> idsOpportunistes = opportunistes.tousLesIds();
        ResultatVerification resultat = ResultatVerification.ok();
        for (Passage autre : passageDao.findByPoint(passage.idPoint())) {
            if (estLeMemePassage(autre, passage)
                    || autre.numeroPassage() == passage.numeroPassage()
                    || autre.annee() != passage.annee()
                    || autre.dateEnregistrement() == null
                    || (autre.id() != null && idsOpportunistes.contains(autre.id()))) {
                continue;
            }
            LocalDate dateAutre = LocalDate.parse(autre.dateEnregistrement());
            if (intervalleInferieurAUnMois(dateCourante, dateAutre)) {
                resultat = resultat.avec(Alerte.soft("Moins d'un mois entre le passage n°"
                        + passage.numeroPassage()
                        + " ("
                        + dateCourante
                        + ") et le passage n°"
                        + autre.numeroPassage()
                        + " ("
                        + dateAutre
                        + ") sur ce point. Intervalle conseillé : au moins 1 mois. Alerte non bloquante."));
            }
        }
        return resultat;
    }

    /// Fait avancer un passage à l'étape suivante du workflow (cf. [MoteurWorkflowPassage]).
    ///
    /// @throws RegleMetierException si le passage est déjà au statut terminal
    /// ([StatutWorkflow#DEPOSE])
    public Passage avancerStatut(Passage passage) {
        Objects.requireNonNull(passage, PASSAGE);
        StatutWorkflow suivant = moteur.suivant(passage.statutWorkflow())
                .orElseThrow(() -> new RegleMetierException("Le passage est déjà au statut terminal « "
                        + passage.statutWorkflow().libelle()
                        + " » : aucune transition possible."));
        return changerStatut(passage, suivant);
    }

    /// Applique une transition de workflow explicite après l'avoir validée.
    ///
    /// Le passage à [StatutWorkflow#DEPOSE] horodate automatiquement `deposeLe` via l'[Horloge]
    /// (`maintenant()`, déterministe en test).
    ///
    /// @return le passage mis à jour (persisté)
    /// @throws RegleMetierException si la transition n'est pas le passage à l'étape suivante
    public Passage changerStatut(Passage passage, StatutWorkflow nouveauStatut) {
        Objects.requireNonNull(passage, PASSAGE);
        Objects.requireNonNull(nouveauStatut, "nouveauStatut");
        moteur.exigerTransitionAutorisee(passage.statutWorkflow(), nouveauStatut);
        String deposeLe =
                nouveauStatut == StatutWorkflow.DEPOSE ? horloge.maintenant().toString() : passage.deposeLe();
        Passage misAJour = new Passage(
                passage.id(),
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.parametresAcquisition(),
                nouveauStatut,
                passage.verdictVerification(),
                passage.commentaire(),
                passage.donneesMeteo(),
                deposeLe,
                passage.idPoint(),
                passage.idEnregistreur(),
                passage.idCampagne());
        passageDao.update(misAJour);
        return misAJour;
    }

    /// Pose (ou met à jour) le verdict de vérification d'un passage (R13 : verdict `Non vérifié` /
    /// `OK` / `Utilisable` / `Inexploitable`, saisi par l'utilisateur après écoute).
    ///
    /// Invariant dur : un passage déjà [StatutWorkflow#DEPOSE] ne peut plus être re-jugé (son
    /// verdict est figé une fois déposé sur Vigie-Chiro).
    ///
    /// @return le passage mis à jour (persisté)
    /// @throws RegleMetierException si le passage est déjà déposé
    public Passage poserVerdict(Passage passage, Verdict verdict) {
        Objects.requireNonNull(passage, PASSAGE);
        Objects.requireNonNull(verdict, "verdict");
        // « Récupéré » compte ici au même titre que « Déposé » (#2773) : la nuit est sur Vigie-Chiro,
        // un verdict local divergent la désynchroniserait exactement pareil. Sans cette ligne, poser le
        // statut au lot 1 aurait DÉGELÉ un verdict que la version précédente tenait fermé.
        if (verdictFige(passage.statutWorkflow())) {
            throw new RegleMetierException(
                    "Verdict figé : un passage déposé ne peut plus changer de verdict de vérification.");
        }
        Passage misAJour = new Passage(
                passage.id(),
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.parametresAcquisition(),
                passage.statutWorkflow(),
                verdict,
                passage.commentaire(),
                passage.donneesMeteo(),
                passage.deposeLe(),
                passage.idPoint(),
                passage.idEnregistreur(),
                passage.idCampagne());
        passageDao.update(misAJour);
        return misAJour;
    }

    /// Supprime définitivement un passage. Par cascade DB (`ON DELETE CASCADE`), sa session, ses
    /// originaux, séquences, sélection et relevés capteur/climat disparaissent aussi : un seul
    /// `DELETE` sur la table `passage` suffit. Les fichiers du workspace (bruts, transformés) ne
    /// sont pas touchés, comme pour [fr.univ_amu.iut.sites.model.ServiceSites#supprimerSite] : seule
    /// la base est nettoyée.
    ///
    /// **Refuse** un passage déposé : une nuit déposée est une donnée officielle transmise à
    /// Vigie-Chiro, on ne la détruit pas depuis l'IHM.
    ///
    /// **Sauf une nuit récupérée** (#2581) : celle-là, nous ne l'avons pas déposée, nous l'avons reçue.
    /// La supprimer ne détruit aucune donnée officielle - la participation reste sur la plateforme, et une
    /// prochaine synchronisation la rapatriera. C'est une copie locale qu'on enlève, pas un dépôt qu'on
    /// annule. Le détour existant (« Annuler le dépôt » puis « Supprimer ») demandait à l'utilisateur
    /// d'affirmer quelque chose de faux pour obtenir le droit de nettoyer sa base.
    ///
    /// @throws RegleMetierException si le passage est introuvable, ou déposé sans être une nuit récupérée
    public void supprimer(Long idPassage) {
        Passage passage = charger(idPassage);
        // Le statut porte désormais la distinction (#2772) : « Déposé » veut dire « déposé par nous ».
        if (passage.statutWorkflow() == StatutWorkflow.DEPOSE) {
            throw new RegleMetierException("Suppression refusée : un passage déposé ne peut pas être supprimé.");
        }
        passageDao.delete(idPassage);
        journal.mutationStructurelleValidee();
    }

    /// Pourquoi une nuit récupérée n'annule pas son dépôt. Nommé ici pour que le service et l'écran
    /// disent **la même chose** : un motif affiché qui diverge du refus est un motif qui ment une fois
    /// sur deux.
    public static final String MOTIF_DEPOT_NON_ANNULABLE =
            "Cette nuit vient de Vigie-Chiro, où elle est déposée : il n'y a pas de dépôt à annuler ici."
                    + " Pour la retirer de cette machine, utilisez « Supprimer » - la participation reste"
                    + " sur la plateforme, et une prochaine synchronisation la rapatriera.";

    /// Cette nuit vient-elle de la plateforme sans que rien n'y ait été fait ici (#2581) ?
    ///
    /// Une nuit **récupérée** porte le statut « Déposé », et c'est vrai : la participation existe sur
    /// Vigie-Chiro. Mais les gardes de ce statut protègent une nuit **que nous avons déposée**, et elles
    /// ne disent pas toutes quelque chose de juste sur celle-ci.
    ///
    /// Depuis #2772, l'état est **porté par le passage** : la question se lit sur lui, elle ne se
    /// redemande plus à la base. Le prédicat observé (`NuitRecupereeDao`) reste ce qui a **fondé** ce
    /// statut, et ce que la migration V37 rejoue - mais entretenir deux chemins vers la même vérité,
    /// c'est se donner deux réponses possibles. Voir l'ADR 2581.
    /// Les statuts pour lesquels le verdict ne se change plus : la nuit vit sur Vigie-Chiro, qu'elle y
    /// soit allée par nos soins ou qu'elle en vienne. Partagé avec `ServiceQualification`, qui applique
    /// la même règle sur son propre chemin de saisie.
    public static boolean verdictFige(StatutWorkflow statut) {
        return statut == StatutWorkflow.DEPOSE || statut == StatutWorkflow.RECUPERE;
    }

    public boolean estNuitRecuperee(Long idPassage) {
        return passageDao
                .findById(idPassage)
                .map(p -> p.statutWorkflow() == StatutWorkflow.RECUPERE)
                .orElse(false);
    }

    /// **Annule le dépôt** d'un passage : le repasse de [StatutWorkflow#DEPOSE] à
    /// [StatutWorkflow#PRET_A_DEPOSER] et efface son horodatage `deposited_at`, pour permettre de le
    /// **corriger** (compléter/rectifier des validations, ré-importer le CSV Tadarida) puis re-déposer.
    ///
    /// Les observations et leurs **validations sont conservées** : seul le statut change (aucune donnée
    /// n'est détruite). C'est la **seule transition arrière** admise du workflow : le
    /// [MoteurWorkflowPassage] étant strictement linéaire, ce retour délibéré depuis « Déposé » est géré
    /// ici directement, hors moteur.
    ///
    /// @throws RegleMetierException si le passage est introuvable ou n'est **pas** déposé
    public Passage annulerDepot(Long idPassage) {
        Passage passage = charger(idPassage);
        // D'ABORD le refus spécifique. Depuis #2772 une nuit récupérée n'est plus « Déposé », donc le
        // refus générique ci-dessous l'attraperait - en lui répondant « le passage n'est pas déposé »,
        // ce qui est vrai localement et trompeur : il l'est sur la plateforme, et le message ne dirait
        // plus quoi faire.
        if (passage.statutWorkflow() == StatutWorkflow.RECUPERE) {
            throw new RegleMetierException(MOTIF_DEPOT_NON_ANNULABLE);
        }
        if (passage.statutWorkflow() != StatutWorkflow.DEPOSE) {
            throw new RegleMetierException(
                    "Annulation du dépôt impossible : le passage n'est pas déposé (statut actuel : « "
                            + passage.statutWorkflow().libelle()
                            + " »).");
        }
        // Une nuit récupérée n'a pas de dépôt à annuler ici (#2771) : c'est la plateforme qui l'a, et
        // aucun geste local ne le change. Pire, la transition la ramènerait en « Prêt à déposer », d'où
        // le geste suivant - parfaitement naturel depuis cet état - fabriquerait une SECONDE
        // participation. Depuis #2760, « Supprimer » suffit à qui veut nettoyer sa base.
        Passage misAJour = new Passage(
                passage.id(),
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.parametresAcquisition(),
                StatutWorkflow.PRET_A_DEPOSER,
                passage.verdictVerification(),
                passage.commentaire(),
                passage.donneesMeteo(),
                null, // deposited_at effacé : le passage n'est plus déposé
                passage.idPoint(),
                passage.idEnregistreur(),
                passage.idCampagne());
        passageDao.update(misAJour);
        return misAJour;
    }

    private Passage charger(Long idPassage) {
        Objects.requireNonNull(idPassage, ID_PASSAGE);
        return passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException("Passage introuvable : " + idPassage));
    }

    private static boolean estLeMemePassage(Passage a, Passage b) {
        return a.id() != null && a.id().equals(b.id());
    }

    /// Vrai si les deux dates sont distantes de strictement moins d'un mois calendaire (R4).
    private static boolean intervalleInferieurAUnMois(LocalDate a, LocalDate b) {
        LocalDate plusTot = a.isAfter(b) ? b : a;
        LocalDate plusTard = a.isAfter(b) ? a : b;
        return plusTot.plusMonths(1).isAfter(plusTard);
    }
}

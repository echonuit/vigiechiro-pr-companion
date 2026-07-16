package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Service métier de la feature `qualification` : pilote la
/// **vérification d'enregistrement par échantillonnage** (parcours P3), de l'ouverture de la
/// vue jusqu'au verdict.
///
/// Calqué sur le service de référence `ServiceSites` (cf. SERVICE-CONVENTIONS) :
///
/// - reçoit ses dépendances par constructeur (DAO + moteurs + [UniteDeTravail]), assemblées
///   par `QualificationModule` ; reste un objet Java ordinaire, sans annotation d'injection.
///   L'[fr.univ_amu.iut.commun.model.Horloge] du patron de référence n'est pas injectée ici :
///   la feature `qualification` n'écrit aucune colonne de date (le verdict ne s'horodate pas
///   dans le schéma ; `deposited_at` relève de la feature `lot`). Elle sera ajoutée le jour où
///   une colonne `verified_at` apparaîtra ;
/// - n'écrit aucun SQL : il orchestre les DAO et délègue les règles pures aux moteurs
///   ([GenerateurSelection], [PreCheckNuit]) ;
/// - **aucun import JavaFX** (logique métier pure, testable en JUnit) ;
/// - règles dures (intégrité, refus) ⇒ [RegleMetierException] ; saisie mal formée ⇒
///   [IllegalArgumentException] ; règles soft ⇒
///   [fr.univ_amu.iut.commun.model.ResultatVerification] (ici via [PreCheckNuit]).
///
/// Dépendances inter-features assumées et acycliques : `qualification → passage` (séquences,
/// sessions, originaux, passage) et `qualification → sites` (carré du site et code du point,
/// pour vérifier le préfixe R6). Aucune feature ne dépend de `qualification` (graphe sans
/// cycle, contrôlé par `ArchitectureTest`).
///
/// **Atomicité (R12).** Constituer une sélection écrit dans deux tables (`listening_selection`
/// + N × `selection_sequence`) : l'opération est enveloppée dans une [UniteDeTravail] via les
/// surcharges « connection-aware » de [SelectionDao] (tout ou rien). Le drapeau dénormalisé
/// `listening_sequence.in_selection` n'est volontairement pas mis à jour ici : la table de
/// jonction est la source de vérité, et maintenir cette dénormalisation supposerait une
/// méthode transactionnelle dans `SequenceDao` (feature `passage`, hors périmètre de
/// modification).
public class ServiceQualification {

    /// Horodatage de l'enregistreur dans le nom de fichier (R7) : `_AAAAMMJJ_HHMMSS`.
    private static final Pattern HORODATAGE = Pattern.compile("_(\\d{8})_(\\d{6})");

    /// Format d'affichage des heures dans les explications du pré-check (#1506) : `HH:mm`.
    private static final DateTimeFormatter HEURE_COURTE = DateTimeFormatter.ofPattern("HH:mm");

    /// Message d'erreur métier émis quand l'`id` de passage ne correspond à aucune ligne.
    private static final String PASSAGE_INTROUVABLE = "Passage introuvable : ";

    private final SelectionDao selectionDao;
    private final SequenceDao sequenceDao;
    private final SessionDao sessionDao;
    private final EnregistrementOriginalDao originalDao;
    private final PassageDao passageDao;
    private final PointDao pointDao;
    private final SiteDao siteDao;
    private final GenerateurSelection generateur;
    private final PreCheckNuit preCheck;
    private final UniteDeTravail uniteDeTravail;

    public ServiceQualification(
            SelectionDao selectionDao,
            SequenceDao sequenceDao,
            SessionDao sessionDao,
            EnregistrementOriginalDao originalDao,
            PassageDao passageDao,
            PointDao pointDao,
            SiteDao siteDao,
            GenerateurSelection generateur,
            PreCheckNuit preCheck,
            UniteDeTravail uniteDeTravail) {
        this.selectionDao = Objects.requireNonNull(selectionDao, "selectionDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.originalDao = Objects.requireNonNull(originalDao, "originalDao");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.generateur = Objects.requireNonNull(generateur, "generateur");
        this.preCheck = Objects.requireNonNull(preCheck, "preCheck");
        this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
    }

    // ===========================================================================
    // R12 — Constitution de la sélection d'écoute
    // ===========================================================================

    /// Constitue la sélection d'écoute **à l'ouverture de la vue** (R12) : méthode
    /// [MethodeSelection#REPARTITION_TEMPORELLE] par défaut, taille
    /// [GenerateurSelection#TAILLE_DEFAUT]. Idempotent : si une sélection existe déjà pour ce
    /// passage (relation 0:1), elle est renvoyée telle quelle plutôt que reconstituée.
    ///
    /// @return la sélection (existante ou nouvellement créée)
    /// @throws RegleMetierException si le passage est introuvable, sans session, ou sans séquence
    public SelectionDEcoute ouvrirVerification(Long idPassage) {
        Optional<SelectionDEcoute> existante = selectionDao.findByPassage(idPassage);
        return existante.orElseGet(() ->
                creerSelection(idPassage, MethodeSelection.REPARTITION_TEMPORELLE, GenerateurSelection.TAILLE_DEFAUT));
    }

    /// (Re)constitue la sélection d'écoute d'un passage avec une méthode et une taille choisies
    /// (P3 : l'utilisateur peut changer de méthode ou augmenter la taille). Une éventuelle
    /// sélection existante est remplacée atomiquement.
    ///
    /// La taille persistée (`listening_selection.size`) est le nombre **réel** de séquences
    /// retenues (≤ `taille` si la nuit en compte moins).
    ///
    /// @throws RegleMetierException si le passage est introuvable, sans session, ou sans séquence
    /// @throws IllegalArgumentException si `taille < 1`
    public SelectionDEcoute creerSelection(Long idPassage, MethodeSelection methode, int taille) {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(methode, "methode");
        passageDao.findById(idPassage).orElseThrow(() -> new RegleMetierException(PASSAGE_INTROUVABLE + idPassage));
        SessionDEnregistrement session = sessionDao
                .trouverParPassage(idPassage)
                .orElseThrow(() ->
                        new RegleMetierException("Aucune session d'enregistrement pour le passage " + idPassage + "."));
        List<SequenceDEcoute> nuit = sequenceDao.findBySession(session.id());
        if (nuit.isEmpty()) {
            throw new RegleMetierException(
                    "Aucune séquence d'écoute à échantillonner pour ce passage (transformation requise).");
        }
        List<SequenceDEcoute> choisies = generateur.selectionner(nuit, methode, taille);
        Optional<SelectionDEcoute> existante = selectionDao.findByPassage(idPassage);

        uniteDeTravail.executer(connexion -> {
            if (existante.isPresent()) {
                selectionDao.supprimerDansTransaction(connexion, existante.get().id());
            }
            long idSelection = selectionDao.insererDansTransaction(connexion, methode, choisies.size(), idPassage);
            int position = 0;
            for (SequenceDEcoute sequence : choisies) {
                selectionDao.attacherDansTransaction(connexion, idSelection, sequence.id(), position++, false);
            }
        });
        return selectionDao
                .findByPassage(idPassage)
                .orElseThrow(() -> new RegleMetierException("Sélection non persistée pour le passage " + idPassage));
    }

    /// Séquences de la sélection, ordonnées par position d'affichage (relecture).
    public List<SequenceSelectionnee> sequencesDeLaSelection(Long idSelection) {
        return selectionDao.listerSequences(idSelection);
    }

    /// Marque une séquence de la sélection comme **écoutée** (flag `listened` de la jonction).
    /// Sans effet si le couple (sélection, séquence) n'est pas rattaché.
    public void marquerSequenceEcoutee(Long idSelection, Long idSequence) {
        selectionDao.marquerEcoutee(idSelection, idSequence);
    }

    // ===========================================================================
    // Projections de lecture pour l'IHM (bandeau d'en-tête + liste de la sélection)
    // ===========================================================================

    /// Charge le contexte d'en-tête du passage à vérifier (identité du site/point, plage horaire,
    /// volumétrie de la nuit, statut, verdict) pour le bandeau de M-Qualification. Lecture seule.
    ///
    /// @throws RegleMetierException si le passage est introuvable
    public ContexteVerification chargerContexte(Long idPassage) {
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException(PASSAGE_INTROUVABLE + idPassage));
        String numeroCarre = "?";
        String codePoint = "?";
        String nomSite = "";
        Optional<PointDEcoute> point = pointDao.findById(passage.idPoint());
        if (point.isPresent()) {
            codePoint = point.get().code();
            Optional<Site> site = siteDao.findById(point.get().idSite());
            numeroCarre = site.map(Site::numeroCarre).orElse("?");
            nomSite = site.map(Site::nomConvivial).orElse("");
        }
        List<SequenceDEcoute> sequences = sessionDao
                .trouverParPassage(idPassage)
                .map(session -> sequenceDao.findBySession(session.id()))
                .orElseGet(List::of);
        double dureeEnregistree = sequences.stream()
                .map(SequenceDEcoute::dureeSecondes)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        return new ContexteVerification(
                numeroCarre,
                codePoint,
                nomSite,
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                sequences.size(),
                dureeEnregistree,
                passage.statutWorkflow(),
                passage.verdictVerification());
    }

    /// Détaille la sélection : joint chaque rattachement (position + flag écouté) à sa séquence
    /// d'écoute ([SequenceDEcoute]), ordonné par position. Lecture seule.
    public List<SequenceEnSelection> detaillerSelection(Long idSelection) {
        List<SequenceEnSelection> lignes = new ArrayList<>();
        for (SequenceSelectionnee rattachement : selectionDao.listerSequences(idSelection)) {
            sequenceDao
                    .findById(rattachement.idSequence())
                    .ifPresent(sequence -> lignes.add(new SequenceEnSelection(
                            sequence, rattachement.position(), rattachement.ecoutee(), rattachement.verdict())));
        }
        return lignes;
    }

    /// Détaille la sélection d'écoute **d'un passage** (jointure de lecture par `idPassage`) : liste vide
    /// si le passage n'a pas de sélection. Convenience de lecture pour la CLI (#1512). Lecture seule.
    public List<SequenceEnSelection> detaillerSelectionParPassage(Long idPassage) {
        return selectionDao
                .findByPassage(idPassage)
                .map(selection -> detaillerSelection(selection.id()))
                .orElseGet(List::of);
    }

    /// Enregistre le **verdict par fichier** d'une séquence de la sélection d'un passage (#1524,
    /// lot 5). Lève si le passage n'a pas de sélection. N'écrit **que** `selection_sequence.verdict` :
    /// il ne rafraîchit pas le cache dénormalisé `passage.verification_verdict`. Depuis le lot 6a, l'IHM
    /// dérive le verdict du passage de ces verdicts par fichier ([#verdictDerivePassage]) et le persiste
    /// à l'enregistrement ([#enregistrerVerdict]). La **propagation automatique** du cache (M-Passage,
    /// listes, dépôt) au fil des verdicts par fichier relève du lot 6b (#1551).
    ///
    /// @throws RegleMetierException si le passage n'a pas de sélection d'écoute
    public void enregistrerVerdictFichier(Long idPassage, Long idSequence, VerdictFichier verdict) {
        Objects.requireNonNull(verdict, "verdict");
        SelectionDEcoute selection = selectionDao
                .findByPassage(idPassage)
                .orElseThrow(
                        () -> new RegleMetierException("Aucune sélection d'écoute pour le passage " + idPassage + "."));
        selectionDao.marquerVerdict(selection.id(), idSequence, verdict);
    }

    /// **Verdict final proposé** pour un passage, dérivé des verdicts par fichier de sa sélection
    /// ([AgregationVerdict], #1524). Lecture seule : ne persiste rien. Depuis le lot 6a, ce dérivé
    /// **pré-remplit** le verdict global dans l'IHM (surchargeable), et devient donc, à l'enregistrement,
    /// la valeur persistée par [#enregistrerVerdict]. Persister le cache `passage.verification_verdict`
    /// automatiquement (sans passer par l'enregistrement) est un chantier du lot 6b (#1551). Renvoie
    /// [Verdict#A_VERIFIER] si le passage n'a pas de sélection ou si aucune séquence n'est jugée.
    public Verdict verdictDerivePassage(Long idPassage) {
        return selectionDao
                .findByPassage(idPassage)
                .map(selection -> AgregationVerdict.deriver(selectionDao.listerSequences(selection.id()).stream()
                        .map(SequenceSelectionnee::verdict)
                        .toList()))
                .orElse(Verdict.A_VERIFIER);
    }

    // ===========================================================================
    // R13 — Verdict global et transition de statut
    // ===========================================================================

    /// Enregistre le **verdict global** de vérification d'un passage et le fait transiter vers
    /// le statut [StatutWorkflow#VERIFIE] (P3, étape 3).
    ///
    /// **R13 : aucun seuil d'écoute obligatoire.** Le verdict est accepté quel que soit le
    /// nombre de séquences réellement écoutées (l'utilisateur reste responsable) : la méthode ne
    /// consulte donc jamais l'état d'écoute de la sélection. Un verdict [Verdict#A_JETER] est
    /// simplement mémorisé tel quel — le refus d'inclusion dans un lot (R14) sera appliqué en
    /// aval par la feature `lot`.
    ///
    /// @param verdict `OK`, `Utilisable` ou `Inexploitable` (le sentinelle `Non vérifié` est refusé : il
    ///     dénote l'absence de verdict)
    /// @param commentaire commentaire libre facultatif (`null` ⇒ commentaire existant conservé)
    /// @return le passage mis à jour
    /// @throws IllegalArgumentException si `verdict` est `null` ou [Verdict#A_VERIFIER]
    /// @throws RegleMetierException si le passage est introuvable, ou déjà **déposé** (verdict figé :
    ///     une nuit déposée sur Vigie-Chiro ne peut plus changer de verdict, #1514)
    public Passage enregistrerVerdict(Long idPassage, Verdict verdict, String commentaire) {
        if (verdict == null || verdict == Verdict.A_VERIFIER) {
            throw new IllegalArgumentException("Un verdict explicite est requis (OK, Utilisable ou Inexploitable).");
        }
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException(PASSAGE_INTROUVABLE + idPassage));
        // #1514 : un passage déposé est figé (même règle que ServicePassage.poserVerdict et la CLI).
        // Sans cette garde, l'IHM ferait régresser le passage de « Déposé » à « Vérifié », désynchronisé
        // de la plateforme (deposeLe conservé, participation orpheline) : corruption de données.
        if (passage.statutWorkflow() == StatutWorkflow.DEPOSE) {
            throw new RegleMetierException(
                    "Verdict figé : un passage déposé ne peut plus changer de verdict de vérification.");
        }
        String commentaireFinal = commentaire != null ? commentaire : passage.commentaire();
        Passage verifie = new Passage(
                passage.id(),
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.parametresAcquisition(),
                StatutWorkflow.VERIFIE,
                verdict,
                commentaireFinal,
                passage.donneesMeteo(),
                passage.deposeLe(),
                passage.idPoint(),
                passage.idEnregistreur());
        passageDao.update(verifie);
        return verifie;
    }

    /// `true` si le passage porte le verdict [Verdict#A_JETER]. Point d'extension pour R14 (un
    /// passage « Inexploitable » ne peut pas rejoindre un lot prêt à déposer) : la feature `lot`
    /// s'appuiera sur cette information.
    ///
    /// @throws RegleMetierException si le passage est introuvable
    public boolean estAJeter(Long idPassage) {
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException(PASSAGE_INTROUVABLE + idPassage));
        return passage.verdictVerification() == Verdict.A_JETER;
    }

    // ===========================================================================
    // P3 étape 1 — Pré-check synthétique (3 feux)
    // ===========================================================================

    /// Évalue les trois feux du pré-check d'une nuit (P3) : couverture horaire, nombre de
    /// fichiers, cohérence du renommage. Rassemble les mesures depuis les DAO puis délègue la
    /// décision au moteur pur [PreCheckNuit].
    ///
    /// **Couverture horaire — limite connue.** La fenêtre théorique « coucher de soleil - 30
    /// min → lever de soleil + 30 min » (R3) suppose un calcul astronomique (position du soleil
    /// selon GPS + date) non disponible dans cette couche. À défaut, on utilise comme fenêtre de
    /// référence la plage déclarée du passage (`start_time` → `end_time`, à cheval sur minuit le
    /// cas échéant) et on la compare à la plage observée (premier → dernier horodatage
    /// d'original). C'est un **point d'intégration** : brancher un vrai calcul d'éphémérides
    /// quand il sera disponible.
    ///
    /// @throws RegleMetierException si le passage est introuvable
    public PreCheckNuit.Diagnostic precheck(Long idPassage) {
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException(PASSAGE_INTROUVABLE + idPassage));
        List<EnregistrementOriginal> originaux = sessionDao
                .trouverParPassage(idPassage)
                .map(session -> originalDao.findBySession(session.id()))
                .orElseGet(List::of);

        int nombreFichiers = originaux.size();
        int fichiersMalNommes = compterFichiersMalNommes(passage, originaux);
        Couverture couverture = mesurerCouverture(passage, originaux);
        PreCheckNuit.Mesures mesures = new PreCheckNuit.Mesures(
                nombreFichiers,
                fichiersMalNommes,
                couverture.ecartMinutes(),
                couverture.moitieManquante(),
                couverture.plageObservee(),
                couverture.plageAttendue());
        return preCheck.evaluer(mesures);
    }

    /// Résultat de la mesure de couverture horaire : l'écart et le drapeau de moitié manquante
    /// (consommés par le moteur), plus les deux plages **déjà formatées** (`HH:mm à HH:mm`) qui
    /// alimentent l'explication du feu (#1506). Les plages sont `null` si non mesurables.
    private record Couverture(long ecartMinutes, boolean moitieManquante, String plageObservee, String plageAttendue) {}

    // --- Helpers de mesure (privés) -------------------------------------------

    /// Compte les originaux dont le nom ne commence pas par le préfixe R6 attendu.
    private int compterFichiersMalNommes(Passage passage, List<EnregistrementOriginal> originaux) {
        String prefixeAttendu = prefixeAttendu(passage);
        if (prefixeAttendu == null) {
            return 0; // site/point introuvable : on ne peut pas statuer, on ne pénalise pas.
        }
        int malNommes = 0;
        for (EnregistrementOriginal original : originaux) {
            String nom = original.nomFichier();
            if (nom == null || !nom.startsWith(prefixeAttendu)) {
                malNommes++;
            }
        }
        return malNommes;
    }

    /// Préfixe R6 attendu pour ce passage, ou `null` si le point/site est introuvable.
    private String prefixeAttendu(Passage passage) {
        PointDEcoute point = pointDao.findById(passage.idPoint()).orElse(null);
        if (point == null) {
            return null;
        }
        Site site = siteDao.findById(point.idSite()).orElse(null);
        if (site == null) {
            return null;
        }
        return new Prefixe(site.numeroCarre(), passage.annee(), passage.numeroPassage(), point.code()).prefixeFichier();
    }

    /// Mesure le déficit de couverture horaire et formate les plages pour l'explication (#1506).
    private Couverture mesurerCouverture(Passage passage, List<EnregistrementOriginal> originaux) {
        List<LocalDateTime> horodatages = originaux.stream()
                .map(original -> horodatageDe(original.nomFichier()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted()
                .toList();
        Optional<LocalDateTime[]> fenetre = fenetreAttendue(passage);
        if (horodatages.isEmpty() || fenetre.isEmpty()) {
            return new Couverture(0L, false, null, null); // pas de quoi statuer : couverture neutre (verte).
        }
        LocalDateTime debutObserve = horodatages.get(0);
        LocalDateTime finObservee = horodatages.get(horodatages.size() - 1);
        LocalDateTime debutAttendu = fenetre.get()[0];
        LocalDateTime finAttendue = fenetre.get()[1];

        long manqueDebut =
                Math.max(0, Duration.between(debutAttendu, debutObserve).toMinutes());
        long manqueFin = Math.max(0, Duration.between(finObservee, finAttendue).toMinutes());
        long ecart = Math.max(manqueDebut, manqueFin);
        long dureeFenetre =
                Math.max(1, Duration.between(debutAttendu, finAttendue).toMinutes());
        boolean moitieManquante = ecart * 2 >= dureeFenetre;
        return new Couverture(
                ecart, moitieManquante, plage(debutObserve, finObservee), plage(debutAttendu, finAttendue));
    }

    /// Formate une plage `HH:mm à HH:mm` (l'heure seule, la date étant implicite) pour l'affichage
    /// de la couverture (#1506).
    private static String plage(LocalDateTime debut, LocalDateTime fin) {
        return debut.format(HEURE_COURTE) + " à " + fin.format(HEURE_COURTE);
    }

    /// Fenêtre théorique de la nuit déduite de `start_time`/`end_time` du passage.
    private static Optional<LocalDateTime[]> fenetreAttendue(Passage passage) {
        try {
            LocalDate date = LocalDate.parse(passage.dateEnregistrement());
            LocalTime heureDebut = LocalTime.parse(passage.heureDebut());
            LocalTime heureFin = LocalTime.parse(passage.heureFin());
            LocalDateTime debut = LocalDateTime.of(date, heureDebut);
            LocalDateTime fin = LocalDateTime.of(date, heureFin);
            if (!fin.isAfter(debut)) {
                fin = fin.plusDays(1); // nuit à cheval sur minuit
            }
            return Optional.of(new LocalDateTime[] {debut, fin});
        } catch (RuntimeException invalide) {
            return Optional.empty();
        }
    }

    /// Extrait l'horodatage `_AAAAMMJJ_HHMMSS` d'un nom de fichier (R7), si présent.
    private static Optional<LocalDateTime> horodatageDe(String nomFichier) {
        if (nomFichier == null) {
            return Optional.empty();
        }
        Matcher matcher = HORODATAGE.matcher(nomFichier);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String date = matcher.group(1);
        String heure = matcher.group(2);
        try {
            LocalDate jour = LocalDate.of(
                    Integer.parseInt(date.substring(0, 4)),
                    Integer.parseInt(date.substring(4, 6)),
                    Integer.parseInt(date.substring(6, 8)));
            LocalTime moment = LocalTime.of(
                    Integer.parseInt(heure.substring(0, 2)),
                    Integer.parseInt(heure.substring(2, 4)),
                    Integer.parseInt(heure.substring(4, 6)));
            return Optional.of(LocalDateTime.of(jour, moment));
        } catch (RuntimeException invalide) {
            return Optional.empty();
        }
    }
}

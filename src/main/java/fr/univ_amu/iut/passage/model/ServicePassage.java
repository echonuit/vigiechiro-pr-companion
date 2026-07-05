package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Alerte;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.ResultatVerification;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.RattachementDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Service métier transverse de la feature `passage` : création d'un passage, vérifications de
/// protocole (R3/R4), pilotage du workflow et pose du verdict. Calqué sur le service de référence
/// `ServiceSites` (cf. SERVICE-CONVENTIONS).
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
/// `viewmodel`, qui connaît le site courant) — exactement comme
/// `ServiceSites.rappelsProtocole(Protocole)`.
public class ServicePassage {

    /// Nom du paramètre `passage` (messages `requireNonNull`).
    private static final String PASSAGE = "passage";

    /// Nom du paramètre `idPassage` (messages `requireNonNull`).
    private static final String ID_PASSAGE = "idPassage";

    /// Préfixe du message d'erreur « passage introuvable ».
    private static final String PASSAGE_INTROUVABLE = "Passage introuvable : ";

    private final PassageDao passageDao;
    private final MoteurWorkflowPassage moteur;
    private final Horloge horloge;
    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final ReprefixeurSession reprefixeur;
    private final UniteDeTravail uniteDeTravail;
    private final RattachementDao rattachementDao;
    private final MaterielMicroDao materielDao;

    public ServicePassage(
            PassageDao passageDao,
            MoteurWorkflowPassage moteur,
            Horloge horloge,
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            ReprefixeurSession reprefixeur,
            UniteDeTravail uniteDeTravail,
            RattachementDao rattachementDao,
            MaterielMicroDao materielDao) {
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.moteur = Objects.requireNonNull(moteur, "moteur");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.reprefixeur = Objects.requireNonNull(reprefixeur, "reprefixeur");
        this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
        this.rattachementDao = Objects.requireNonNull(rattachementDao, "rattachementDao");
        this.materielDao = Objects.requireNonNull(materielDao, "materielDao");
    }

    /// Nombre total de passages (compteur du tableau de bord d'accueil).
    public long compterPassages() {
        return passageDao.compter();
    }

    /// Projection de lecture pour l'écran **M-Passage** : le passage `idPassage` et les agrégats de
    /// sa session (volumes, durée audible, nombre de séquences). Sans jointure `sites` : le contexte
    /// site (carré, code point) est fourni à la vue par la navigation.
    ///
    /// @throws RegleMetierException si le passage est introuvable
    public DetailPassage detailPassage(Long idPassage) {
        Objects.requireNonNull(idPassage, ID_PASSAGE);
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException(PASSAGE_INTROUVABLE + idPassage));
        Optional<SessionDEnregistrement> session = sessionDao.trouverParPassage(idPassage);
        List<SequenceDEcoute> sequences =
                session.map(s -> sequenceDao.findBySession(s.id())).orElseGet(List::of);
        double dureeAudible = sequences.stream()
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
                dureeAudible,
                MeteoPassage.lire(passage.donneesMeteo()));
    }

    /// Renseigne (ou efface, avec `null`) la **température en début de nuit** (°C) d'un passage (#106) :
    /// donnée **optionnelle**, jamais bloquante. Stockée dans `passage.weather_data` via [MeteoPassage].
    ///
    /// @param idPassage passage cible
    /// @param temperatureDebutNuit température en °C, ou `null` pour effacer
    /// @return le passage mis à jour
    public Passage definirTemperatureDebutNuit(Long idPassage, Double temperatureDebutNuit) {
        Objects.requireNonNull(idPassage, ID_PASSAGE);
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException(PASSAGE_INTROUVABLE + idPassage));
        Passage modifie = avecDonneesMeteo(passage, MeteoPassage.definir(passage.donneesMeteo(), temperatureDebutNuit));
        passageDao.update(modifie);
        return modifie;
    }

    /// Renseigne le **relevé météo complet** d'un passage (température début/fin, vent, couverture
    /// nuageuse ; #106 étendu) : données **optionnelles** stockées dans `passage.weather_data` via
    /// [MeteoPassage]. Chaque grandeur `null` du relevé efface sa clé ; les autres clés sont préservées.
    ///
    /// @param idPassage passage cible
    /// @param releve relevé météo (grandeurs optionnelles ; `null` par grandeur = effacer)
    /// @return le passage mis à jour
    public Passage definirMeteo(Long idPassage, MeteoReleve releve) {
        Objects.requireNonNull(idPassage, ID_PASSAGE);
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException(PASSAGE_INTROUVABLE + idPassage));
        Passage modifie = avecDonneesMeteo(passage, MeteoPassage.definirReleve(passage.donneesMeteo(), releve));
        passageDao.update(modifie);
        return modifie;
    }

    /// Copie `passage` en ne changeant que ses données météo sérialisées (colonne `weather_data`),
    /// factorisée par les deux points d'entrée météo ci-dessus.
    private static Passage avecDonneesMeteo(Passage passage, String donneesMeteo) {
        return new Passage(
                passage.id(),
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.parametresAcquisition(),
                passage.statutWorkflow(),
                passage.verdictVerification(),
                passage.commentaire(),
                donneesMeteo,
                passage.deposeLe(),
                passage.idPoint(),
                passage.idEnregistreur());
    }

    /// Matériel du micro déployé pour le passage `idPassage` (position sol/canopée, hauteur de fixation,
    /// type ; métadonnées de dépôt VigieChiro). Renvoie un [MaterielMicro#vide] si rien n'a été saisi —
    /// jamais `null`. Le n° de série du détecteur n'est pas ici (il vit sur l'enregistreur du passage).
    public MaterielMicro materiel(Long idPassage) {
        Objects.requireNonNull(idPassage, ID_PASSAGE);
        return materielDao.pour(idPassage);
    }

    /// Enregistre le **matériel du micro** d'un passage (ou **efface** la ligne si le relevé est vide),
    /// dans la table `passage_equipment`. Le passage lui-même n'est pas modifié. Le `materiel` porte son
    /// `idPassage`.
    ///
    /// @param materiel matériel saisi (grandeurs optionnelles), rattaché à son passage
    public void definirMateriel(MaterielMicro materiel) {
        Objects.requireNonNull(materiel, "materiel");
        materielDao.definir(materiel);
    }

    /// Crée un passage à l'état initial [StatutWorkflow#IMPORTE], sans verdict.
    ///
    /// - R5 (dur) : refuse si le quadruplet `(point, année, n° de passage)` existe déjà —
    /// pré-vérifié via [PassageDao#trouverParPointAnneePassage] (filet : contrainte `UNIQUE` du
    /// schéma).
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
        exigerQuadrupletUnique(idPoint, annee, numeroPassage); // R5
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
                idEnregistreur);
        return passageDao.insert(aCreer);
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

    /// R3 (soft, `PointFixeStandard` uniquement) : le passage 1 est attendu entre le 15 juin et le
    /// 31 juillet, le passage 2 entre le 15 août et le 30 septembre. Hors fenêtre → alerte non
    /// bloquante. Sur [Protocole#RECHERCHE], ou pour un n° de passage sans fenêtre définie (autre
    /// que 1 ou 2), la règle est muette.
    public ResultatVerification verifierFenetreSaisonniere(Passage passage, Protocole protocole) {
        Objects.requireNonNull(passage, PASSAGE);
        if (protocole != Protocole.STANDARD || passage.dateEnregistrement() == null) {
            return ResultatVerification.ok();
        }
        Optional<Fenetre> fenetre = fenetrePour(passage.numeroPassage(), passage.annee());
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
                + " → "
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
        if (protocole != Protocole.STANDARD || passage.dateEnregistrement() == null) {
            return ResultatVerification.ok();
        }
        LocalDate dateCourante = LocalDate.parse(passage.dateEnregistrement());
        ResultatVerification resultat = ResultatVerification.ok();
        for (Passage autre : passageDao.findByPoint(passage.idPoint())) {
            if (estLeMemePassage(autre, passage)
                    || autre.numeroPassage() == passage.numeroPassage()
                    || autre.annee() != passage.annee()
                    || autre.dateEnregistrement() == null) {
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
                        + ") sur ce point. Intervalle conseillé ≥ 1 mois. Alerte non bloquante."));
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
                passage.idEnregistreur());
        passageDao.update(misAJour);
        return misAJour;
    }

    /// Pose (ou met à jour) le verdict de vérification d'un passage (R13 : verdict `À vérifier` /
    /// `OK` / `Douteux` / `À jeter`, saisi par l'utilisateur après écoute).
    ///
    /// Invariant dur : un passage déjà [StatutWorkflow#DEPOSE] ne peut plus être re-jugé (son
    /// verdict est figé une fois déposé sur Vigie-Chiro).
    ///
    /// @return le passage mis à jour (persisté)
    /// @throws RegleMetierException si le passage est déjà déposé
    public Passage poserVerdict(Passage passage, Verdict verdict) {
        Objects.requireNonNull(passage, PASSAGE);
        Objects.requireNonNull(verdict, "verdict");
        if (passage.statutWorkflow() == StatutWorkflow.DEPOSE) {
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
                passage.idEnregistreur());
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
    /// @throws RegleMetierException si le passage est introuvable ou déjà déposé
    public void supprimer(Long idPassage) {
        Objects.requireNonNull(idPassage, ID_PASSAGE);
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException(PASSAGE_INTROUVABLE + idPassage));
        if (passage.statutWorkflow() == StatutWorkflow.DEPOSE) {
            throw new RegleMetierException("Suppression refusée : un passage déposé ne peut pas être supprimé.");
        }
        passageDao.delete(idPassage);
    }

    /// **Annule le dépôt** d'un passage : le repasse de [StatutWorkflow#DEPOSE] à
    /// [StatutWorkflow#PRET_A_DEPOSER] et efface son horodatage `deposited_at`, pour permettre de le
    /// **corriger** (compléter/rectifier des validations, ré-importer le CSV Tadarida) puis re-déposer.
    ///
    /// Les observations et leurs **validations sont conservées** : seul le statut change (aucune donnée
    /// n'est détruite). C'est la **seule transition arrière** admise du workflow — le
    /// [MoteurWorkflowPassage] étant strictement linéaire, ce retour délibéré depuis « Déposé » est géré
    /// ici directement, hors moteur.
    ///
    /// @throws RegleMetierException si le passage est introuvable ou n'est **pas** déposé
    public Passage annulerDepot(Long idPassage) {
        Objects.requireNonNull(idPassage, ID_PASSAGE);
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException(PASSAGE_INTROUVABLE + idPassage));
        if (passage.statutWorkflow() != StatutWorkflow.DEPOSE) {
            throw new RegleMetierException(
                    "Annulation du dépôt impossible : le passage n'est pas déposé (statut actuel : « "
                            + passage.statutWorkflow().libelle()
                            + " »).");
        }
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
                passage.idEnregistreur());
        passageDao.update(misAJour);
        return misAJour;
    }

    /// Modifie rétroactivement le rattachement d'un passage (E2.S8) : nouvelle année et/ou n° de
    /// passage, **même site/point**. Le préfixe `Car<carré>-<année>-Pass<n>-<point>` change : tous
    /// les fichiers de la nuit (dossier, originaux, séquences) sont re-renommés.
    ///
    /// Ordre (atomicité best-effort base/disque) : (1) contrôle **R5** du nouveau quadruplet ;
    /// (2) re-préfixage **disque** ([ReprefixeurSession], rollback interne) ; (3) transaction
    /// **base** ([UniteDeTravail]) du quadruplet et des chemins (session, originaux, séquences,
    /// journal, relevé — [RattachementDao]). Si la transaction échoue, le disque est **remis dans
    /// son état initial** (compensation) avant que l'erreur ne soit propagée.
    ///
    /// Le carré et le code point (inchangés) sont fournis par l'appelant via `nouveau` (le `model` ne
    /// dépend pas de `sites`) ; l'ancien préfixe est reconstruit depuis l'année/n° courants.
    ///
    /// @param nouveau préfixe cible (même carré/point, nouvelle année et/ou n° de passage)
    /// @throws RegleMetierException si le passage est introuvable ou si le nouveau quadruplet existe
    public void modifierRattachement(Long idPassage, Prefixe nouveau) {
        Objects.requireNonNull(idPassage, ID_PASSAGE);
        Objects.requireNonNull(nouveau, "nouveau");
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException(PASSAGE_INTROUVABLE + idPassage));
        Prefixe ancien = new Prefixe(nouveau.carre(), passage.annee(), passage.numeroPassage(), nouveau.codePoint());
        if (ancien.equals(nouveau)) {
            return; // ni l'année ni le n° de passage n'ont changé : rien à faire
        }
        exigerQuadrupletUnique(passage.idPoint(), nouveau.annee(), nouveau.numeroPassage());

        Optional<SessionDEnregistrement> session = sessionDao.trouverParPassage(idPassage);
        Long idSession = session.map(SessionDEnregistrement::id).orElse(null);
        Path ancienneRacine = session.map(s -> Path.of(s.cheminRacine())).orElse(null);
        // Une session en base implique un dossier sur disque : on le re-préfixe ([ReprefixeurSession]
        // échoue, avant toute écriture base, si le dossier est absent ou la cible occupée). Seul un
        // passage sans session du tout (jamais importé) saute l'étape disque.
        Path nouvelleRacine = ancienneRacine == null ? null : reprefixeur.reprefixer(ancienneRacine, ancien, nouveau);

        try {
            uniteDeTravail.executer(cx -> {
                rattachementDao.majQuadruplet(cx, idPassage, nouveau.annee(), nouveau.numeroPassage());
                if (idSession != null) {
                    rattachementDao.reprefixerChemins(
                            cx,
                            idPassage,
                            idSession,
                            ancienneRacine,
                            nouvelleRacine,
                            ancien.prefixeFichier(),
                            nouveau.prefixeFichier());
                }
            });
        } catch (RuntimeException echec) {
            if (nouvelleRacine != null) {
                compenser(nouvelleRacine, nouveau, ancien, echec);
            }
            throw echec;
        }
    }

    /// Remet le dossier de session dans son état initial après un échec de la transaction base ; une
    /// erreur de compensation est rattachée à l'erreur d'origine plutôt que de la masquer.
    private void compenser(Path nouvelleRacine, Prefixe nouveau, Prefixe ancien, RuntimeException origine) {
        try {
            reprefixeur.reprefixer(nouvelleRacine, nouveau, ancien);
        } catch (RuntimeException echecCompensation) {
            origine.addSuppressed(echecCompensation);
        }
    }

    private void exigerQuadrupletUnique(Long idPoint, int annee, int numeroPassage) {
        if (passageDao
                .trouverParPointAnneePassage(idPoint, annee, numeroPassage)
                .isPresent()) {
            throw new RegleMetierException("Un passage n°"
                    + numeroPassage
                    + " existe déjà pour ce point en "
                    + annee
                    + " (le quadruplet point/année/n° de passage doit être unique).");
        }
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

    private static Optional<Fenetre> fenetrePour(int numeroPassage, int annee) {
        return switch (numeroPassage) {
            case 1 -> Optional.of(new Fenetre(LocalDate.of(annee, 6, 15), LocalDate.of(annee, 7, 31)));
            case 2 -> Optional.of(new Fenetre(LocalDate.of(annee, 8, 15), LocalDate.of(annee, 9, 30)));
            default -> Optional.empty();
        };
    }

    /// Fenêtre saisonnière fermée [debut, fin] pour la vérification R3.
    private record Fenetre(LocalDate debut, LocalDate fin) {
        boolean contient(LocalDate date) {
            return !date.isBefore(debut) && !date.isAfter(fin);
        }
    }
}

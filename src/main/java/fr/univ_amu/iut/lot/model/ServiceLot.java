package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.ResultatVerification;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Service métier de la feature `lot` : prépare et trace le dépôt d'un passage sur
/// Vigie-Chiro (parcours P4, épopée E4). Suit le patron du service de référence
/// `ServiceSites` : pure Java testable, dépendances reçues par constructeur, distinction
/// règles soft / règles dures.
///
/// Deux étapes du parcours P4 :
///
/// - [#preparerLot(Long)] (E4.S1 + E4.S2) : contrôle R14 + cohérence
///   ([VerificationCoherence]), assemble le récapitulatif ([Lot]) et fait passer le passage
///   à [StatutWorkflow#PRET_A_DEPOSER]. Refuse (exception) tout passage « À jeter » (R14)
///   ou incohérent.
/// - [#marquerDepose(Long)] (E4.S3) : une fois le téléversement manuel effectué, marque le
///   passage [StatutWorkflow#DEPOSE] et horodate `deposited_at` via l'[Horloge] injectée
///   (déterministe en test).
///
/// Les transitions de statut sont déléguées au [MoteurWorkflowPassage] (engin pur de la
/// feature `passage`) : on ne réécrit pas la progression linéaire et on garantit qu'un dépôt
/// suit bien une préparation (pas de saut d'étape ni de retour arrière).
///
/// **Aucun accès réseau** : l'application ne dialogue jamais avec le portail Vigie-Chiro,
/// le dépôt est manuel (le service se contente de préparer le dossier et de tracer la date
/// déclarée).
public class ServiceLot {

    private final PassageDao passageDao;
    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final VerificationCoherence verification;
    private final MoteurWorkflowPassage moteurWorkflow;
    private final Horloge horloge;

    /// Compacteur des archives de dépôt (#110). **Reçu par constructeur** pour que son plafond soit un
    /// réglage applicatif (alimenté par [fr.univ_amu.iut.lot.di.LotModule] depuis une propriété système),
    /// pas une valeur codée en dur dans le chemin métier.
    private final CompacteurDepot compacteur;

    public ServiceLot(
            PassageDao passageDao,
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            VerificationCoherence verification,
            MoteurWorkflowPassage moteurWorkflow,
            Horloge horloge,
            CompacteurDepot compacteur) {
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.verification = Objects.requireNonNull(verification, "verification");
        this.moteurWorkflow = Objects.requireNonNull(moteurWorkflow, "moteurWorkflow");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.compacteur = Objects.requireNonNull(compacteur, "compacteur");
    }

    /// Plafond de taille (octets) appliqué à chaque archive de dépôt (#110), pour l'affichage du réglage.
    public long plafondArchiveOctets() {
        return compacteur.tailleMaxOctets();
    }

    /// Consulte l'état de dépôt d'un passage **sans le transitionner** (lecture pour l'IHM M-Lot) :
    /// statut courant, dossier de session à téléverser (R22), nombre et volume des séquences, et les
    /// alertes de cohérence bloquantes (R14) qui empêcheraient la préparation. N'écrit rien.
    ///
    /// @param idPassage passage consulté
    /// @return l'état de dépôt courant
    /// @throws RegleMetierException si le passage est introuvable
    public EtatLot consulterLot(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        Passage passage = chargerPassage(idPassage);
        ResultatVerification coherence = verification.verifier(passage);
        Optional<SessionDEnregistrement> session = sessionDao.trouverParPassage(idPassage);
        String chemin = session.map(SessionDEnregistrement::cheminRacine).orElse(null);
        int nombre = session.map(s -> sequenceDao.findBySession(s.id()).size()).orElse(0);
        Long volume = session.map(SessionDEnregistrement::volumeSequencesOctets).orElse(null);
        return new EtatLot(
                passage.statutWorkflow(), chemin, nombre, volume, coherence.alertesBloquantes(), passage.deposeLe());
    }

    /// Prépare le lot à déposer d'un passage (E4.S1 + E4.S2).
    ///
    /// - R14 (dur, bloquant) : un passage au verdict [Verdict#A_JETER] est refusé d'emblée.
    /// - Cohérence (dur) : si au moins un contrôle de [VerificationCoherence] est bloquant
    ///   (transformation incomplète, préfixe non conforme, journal absent…), la préparation
    ///   est refusée. Les alertes *soft* (relevé absent, R20) ne bloquent pas.
    /// - Transition : le passage passe à [StatutWorkflow#PRET_A_DEPOSER].
    ///
    /// @return le récapitulatif du lot (séquences, volume, chemin du dossier prêt)
    /// @throws RegleMetierException si le passage est introuvable, « À jeter » (R14) ou incohérent
    public Lot preparerLot(Long idPassage) {
        Passage passage = chargerPassage(idPassage);
        exigerNonAJeter(passage); // R14

        ResultatVerification coherence = verification.verifier(passage);
        if (coherence.estBloquant()) {
            throw new RegleMetierException("Préparation du lot impossible : "
                    + String.join(
                            " ",
                            coherence.alertesBloquantes().stream()
                                    .map(a -> a.message())
                                    .toList()));
        }

        SessionDEnregistrement session = sessionDao
                .trouverParPassage(idPassage)
                .orElseThrow(() -> new RegleMetierException(
                        "Session d'enregistrement introuvable pour le passage " + idPassage + "."));
        List<SequenceDEcoute> sequences = sequenceDao.findBySession(session.id());

        // Transition de statut déléguée au moteur de workflow du passage (Vérifié → Prêt à déposer).
        moteurWorkflow.exigerTransitionAutorisee(passage.statutWorkflow(), StatutWorkflow.PRET_A_DEPOSER);
        passageDao.update(avecStatutEtDepot(passage, StatutWorkflow.PRET_A_DEPOSER, passage.deposeLe()));

        return new Lot(idPassage, session.cheminRacine(), sequences, session.volumeSequencesOctets());
    }

    /// Marque un passage comme déposé après téléversement manuel (E4.S3) : statut
    /// [StatutWorkflow#DEPOSE] et horodatage `deposited_at` lu de l'[Horloge].
    ///
    /// @return le passage mis à jour (statut déposé, date posée)
    /// @throws RegleMetierException si le passage est introuvable ou « À jeter » (R14)
    public Passage marquerDepose(Long idPassage) {
        Passage passage = chargerPassage(idPassage);
        exigerNonAJeter(passage); // R14 : un « À jeter » ne peut jamais être déposé.

        // Transition de statut déléguée au moteur de workflow (Prêt à déposer → Déposé) : impose donc
        // qu'un lot ait d'abord été préparé.
        moteurWorkflow.exigerTransitionAutorisee(passage.statutWorkflow(), StatutWorkflow.DEPOSE);
        Passage depose = avecStatutEtDepot(
                passage, StatutWorkflow.DEPOSE, horloge.maintenant().toString());
        passageDao.update(depose);
        return depose;
    }

    /// Génère les **archives ZIP de dépôt** (#110) du passage : les séquences d'écoute (R8) sont
    /// scindées en archives `<préfixe>-N.zip` ≤ 700 Mo ([CompacteurDepot]), écrites dans un sous-dossier
    /// `depot/` de la session. Le préfixe est le nom du dossier de session (R22 : dossier = préfixe R6).
    /// Ne change pas le statut (la transition Prêt à déposer → Déposé reste portée par
    /// [#marquerDepose(Long)], une fois le téléversement manuel effectué).
    ///
    /// @return les archives produites, par numéro croissant
    /// @throws RegleMetierException si le passage/session est introuvable ou si aucune séquence n'est à déposer
    public List<ArchiveDepot> genererArchivesDepot(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        Passage passage = chargerPassage(idPassage);
        // Le lot doit avoir été **préparé** (preparerLot a déjà validé R14 + cohérence et posé le statut).
        // On n'archive donc que des passages Prêt à déposer ou déjà Déposé : l'API ne court-circuite pas
        // ces contrôles, même si l'IHM masque déjà le bouton avant cet état.
        if (passage.statutWorkflow() != StatutWorkflow.PRET_A_DEPOSER
                && passage.statutWorkflow() != StatutWorkflow.DEPOSE) {
            throw new RegleMetierException("Les archives de dépôt ne peuvent être générées qu'une fois le lot"
                    + " préparé (statut « Prêt à déposer ») : préparez d'abord le lot.");
        }
        SessionDEnregistrement session = sessionDao
                .trouverParPassage(idPassage)
                .orElseThrow(() -> new RegleMetierException(
                        "Session d'enregistrement introuvable pour le passage " + idPassage + "."));
        List<SequenceDEcoute> sequences = sequenceDao.findBySession(session.id());
        if (sequences.isEmpty()) {
            throw new RegleMetierException(
                    "Aucune séquence à déposer pour le passage " + idPassage + " : préparez d'abord le lot.");
        }
        Path racineSession = Path.of(session.cheminRacine());
        String prefixe = racineSession.getFileName().toString(); // R22 : nom du dossier = préfixe R6
        // Le chemin d'une séquence est absolu en production ; on tolère un chemin relatif (résolu contre
        // la racine de session) pour rester robuste aux données héritées.
        List<Path> fichiers = sequences.stream()
                .map(s -> Path.of(s.cheminFichier()))
                .map(p -> p.isAbsolute() ? p : racineSession.resolve(p))
                .toList();
        return compacteur.compacter(fichiers, prefixe, racineSession.resolve("depot"));
    }

    private Passage chargerPassage(Long idPassage) {
        return passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException("Passage introuvable : " + idPassage));
    }

    private static void exigerNonAJeter(Passage passage) {
        if (passage.verdictVerification() == Verdict.A_JETER) {
            throw new RegleMetierException("R14 : le passage n°"
                    + passage.numeroPassage()
                    + " ("
                    + passage.annee()
                    + ") porte le verdict « À jeter » et ne peut pas rejoindre un lot prêt à déposer.");
        }
    }

    /// Reconstruit le passage (record immuable) avec un nouveau statut et une date de dépôt.
    private static Passage avecStatutEtDepot(Passage passage, StatutWorkflow statut, String deposeLe) {
        return new Passage(
                passage.id(),
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.parametresAcquisition(),
                statut,
                passage.verdictVerification(),
                passage.commentaire(),
                passage.donneesMeteo(),
                deposeLe,
                passage.idPoint(),
                passage.idEnregistreur());
    }
}

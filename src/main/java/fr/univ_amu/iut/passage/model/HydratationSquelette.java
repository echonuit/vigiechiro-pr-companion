package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.ExecutionParallele;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.JournalMutations;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/// **Amène une nuit rapatriée au niveau « contenu »** (#2555) : lui crée ses séquences et rapatrie ses
/// observations, **en place**, sans toucher à son identité.
///
/// La synchro « mes sites » rapatrie l'historique en **squelettes** (ADR 0016) : point, date, identité de
/// l'enregistreur, mais aucune séquence. Une nuit en reste au niveau « identité » (#1814) tant que rien
/// ne l'hydrate, et « Réactiver ce passage » y est grisé faute de séquences à confronter au dossier
/// désigné. Trois coutures composées, rien de dupliqué : [PlateformeReconstruction],
/// [CreationPassageArchive#hydraterSequences] et [ImportObservations].
///
/// **En place, pas remplacé**, là où la reconstruction supprime le squelette pour le recréer entier
/// ([ServiceReconstructionPassages#reconstruire]). Deux raisons l'interdisent : un écran est ouvert sur
/// cet `idPassage`, et un squelette porte peut-être des saisies manuelles que la plateforme ignore,
/// n° de série (#1828), météo (#1688), heures de nuit, qu'un delete puis recreate écraserait en silence.
///
/// Le repli sur la pagination `donnees` est acceptable sur **une** nuit désignée, pas sur un balayage de
/// compte où il ferait resurgir le coût qui avait écarté « tout rapatrier à la synchro ». D'où [Source].
/// **Hors du fil JavaFX.**
public final class HydratationSquelette {

    private static final Logger LOG = Logger.getLogger(HydratationSquelette.class.getName());

    /// Nombre de CSV téléchargés **de front** lors d'un balayage de compte (#2557). Borne d'**entrée /
    /// sortie**, pas de calcul : chaque tâche est un GET qui passe son temps à attendre le réseau. Même
    /// valeur que les appels de détail de la synchro (`PlateformeReconstruction`), et pour la même raison :
    /// rester poli avec la plateforme.
    private static final int TELECHARGEMENTS_DE_FRONT = 8;

    /// Où en est une hydratation quand elle écrit. Ces deux repères ne sont **pas** des mesures : ils
    /// situent l'écriture après le téléchargement (0.10, posé par [PlateformeReconstruction]) et avant la
    /// fin. Un appelant pour qui l'hydratation n'est qu'une sous-étape les aplatit ([#libelleSeul]).
    private static final double FRACTION_SEQUENCES = 0.55;

    private static final double FRACTION_IMPORT = 0.85;

    private final PlateformeReconstruction plateforme;

    /// Fan-out borné des téléchargements d'un balayage. Il ne couvre **que** la phase réseau : les écritures
    /// restent en série, SQLite étant mono-écrivain.
    private final ExecutionParallele telechargements = new ExecutionParallele(TELECHARGEMENTS_DE_FRONT);

    private final CreationPassageArchive structure;
    private final LienVigieChiroDao liens;
    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final EnregistrementOriginalDao originalDao;

    /// Port d'import des observations (#1264), **optionnel** : la fonctionnalité « Import Vigie-Chiro » est
    /// désactivable (#1057). Absent, il n'y a pas d'hydratation possible - et [Source] décide si cela se
    /// dit ou se tait.
    private final Optional<ImportObservations> importObservations;

    public HydratationSquelette(
            SourceDeDonnees source,
            ClientVigieChiro client,
            Workspace workspace,
            Horloge horloge,
            Optional<ImportObservations> importObservations,
            JournalMutations journal) {
        Objects.requireNonNull(source, "source");
        this.plateforme = new PlateformeReconstruction(Objects.requireNonNull(client, "client"));
        this.structure = new CreationPassageArchive(source, workspace, horloge, journal);
        this.liens = new LienVigieChiroDao(source);
        this.sessionDao = new SessionDao(source);
        this.sequenceDao = new SequenceDao(source);
        this.originalDao = new EnregistrementOriginalDao(source);
        this.importObservations = Objects.requireNonNull(importObservations, "importObservations");
    }

    /// D'où tirer les observations, et ce que vaut une absence.
    public enum Source {

        /// **Le CSV seul** (#1565, deux GET). Pour la synchro, qui balaie tout un compte : une nuit dont le
        /// CSV n'est pas exposé (analyse non terminée, pièce jointe non montée) est **laissée telle
        /// quelle**, sans erreur, et sera hydratée plus tard. Un empêchement n'est pas un échec ici : c'est
        /// une nuit qui n'est pas encore prête.
        CSV_SEULEMENT,

        /// Le CSV, **puis la pagination `donnees`** en repli. Pour la réactivation, qui traite **une** nuit
        /// que l'utilisateur vient de désigner : le coût du repli est justifié par le geste, et un
        /// empêchement doit se **dire**, avec sa raison.
        COMPLETE
    }

    /// Ce qu'une hydratation a produit.
    ///
    /// @param sequences nombre de lignes de séquences créées
    /// @param observations nombre d'observations rapatriées
    public record BilanHydratation(int sequences, int observations) {}

    /// Hydrate `idPassage` **si c'est un squelette**, et rend ce qui a été rapatrié.
    ///
    /// Rend [Optional#empty()] quand il n'y avait **rien à faire** ou que la source n'a rien donné sans que
    /// ce soit un échec : nuit déjà hydratée (idempotence), nuit sans session, et - en
    /// [Source#CSV_SEULEMENT] seulement - nuit non rattachée, CSV absent, dossier de session renommé, ou
    /// fonctionnalité d'import éteinte. En [Source#COMPLETE], ces mêmes empêchements lèvent une
    /// [RegleMetierException] qui **dit quoi faire**.
    ///
    /// @param progres notifié aux étapes réseau et base
    /// @param jeton consulté aux frontières d'étape
    /// @throws RegleMetierException en [Source#COMPLETE], si l'hydratation est empêchée (avec la raison)
    public Optional<BilanHydratation> hydraterSiSquelette(
            Long idPassage, Source source, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(jeton, "jeton");

        Optional<NuitAHydrater> nuit = reconnaitre(idPassage, source);
        if (nuit.isEmpty()) {
            return Optional.empty();
        }
        // Pas de leverSiAnnule ici : l'étape réseau consulte le jeton en PREMIER
        // ([PlateformeReconstruction#observationsCsv]), et doubler le contrôle laissait croire à une
        // garantie propre à cet appelant. Le mutant qui le supprimait survivait à toute la suite (#2554,
        // passe 6) - un contrôle qu'on peut retirer sans rien casser ne contrôle rien.
        Optional<ObservationsAReconstruire> observations =
                telecharger(nuit.orElseThrow().idParticipation(), source, progres, jeton);
        if (observations.isEmpty()) {
            return Optional.empty(); // CSV_SEULEMENT : pas encore de CSV, la nuit attend son tour
        }
        return Optional.of(ecrire(nuit.orElseThrow(), observations.orElseThrow(), progres));
    }

    /// **Amène au niveau « contenu » toutes les nuits encore en squelette** parmi `idsPassage` (#2557).
    ///
    /// En trois temps, comme la synchro ([ServiceReconstructionPassages#synchroniserStructure]).
    /// **Lectures** : ne retenir que les nuits réellement en squelette, et parmi elles celles qu'on sait
    /// traiter, rattachées et au préfixe lisible. **Réseau, parallélisé** : le CSV de chacune, borne
    /// d'entrée/sortie, best-effort, une nuit dont le CSV n'est pas exposé n'écartant pas les autres.
    /// **Écritures, en série** : SQLite est mono-écrivain, et le parallélisme ne vaut que sur le temps réseau.
    ///
    /// **Best-effort par nuit** : une nuit qui échoue est comptée « restée incomplète » et rendue à son état
    /// de squelette (compensation d'[#ecrire]), donc reprenable telle quelle au prochain tour.
    ///
    /// @param idsPassage les nuits locales rattachées à une participation, squelettes ou non
    /// @return combien ont été complétées, et combien restent en squelette
    public BilanCompletion completerLesSquelettes(
            List<Long> idsPassage, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(idsPassage, "idsPassage");
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(jeton, "jeton");

        // Compté AVANT tout traitement : c'est le dénominateur honnête du compte rendu. Une nuit qu'on ne
        // sait pas traiter (non rattachée, dossier renommé) reste une nuit incomplète, et la taire ferait
        // passer un balayage à moitié fait pour un balayage réussi.
        List<Long> squelettes = idsPassage.stream().filter(this::estSquelette).toList();
        List<NuitAHydrater> candidats = squelettes.stream()
                .map(idPassage -> reconnaitre(idPassage, Source.CSV_SEULEMENT))
                .flatMap(Optional::stream)
                .toList();
        if (candidats.isEmpty()) {
            return new BilanCompletion(0, squelettes.size(), 0);
        }

        List<IssueTelechargement> sources =
                telechargements.cartographier(candidats, "Nuits", this::telecharger, progres, jeton);

        int completees = 0;
        int echecs = 0;
        for (int index = 0; index < candidats.size(); index++) {
            jeton.leverSiAnnule();
            IssueTelechargement source = sources.get(index);
            if (source.echouee()) {
                echecs++;
                continue; // on N'A PAS PU lire : ce n'est pas une nuit qui attend, c'est une lecture ratée
            }
            if (source.observations().isEmpty()) {
                continue; // pas encore de CSV : la nuit attend son analyse, sans que ce soit une erreur
            }
            try {
                ecrire(candidats.get(index), source.observations().orElseThrow(), libelleSeul(progres));
                completees++;
            } catch (RuntimeException echecNuit) {
                // Best-effort : la nuit est rendue à son état de squelette par la compensation d'ecrire, le
                // balayage continue, et elle sera reprise au prochain tour (la synchro est idempotente).
                // Mais poursuivre n'est pas oublier : sans trace, « il me reste 12 nuits vides » serait
                // indiagnosticable (ADR 0008).
                consigner(candidats.get(index).idPassage(), echecNuit);
                echecs++;
            }
        }
        return new BilanCompletion(completees, squelettes.size() - completees - echecs, echecs);
    }

    /// Ce qu'un balayage a produit (#2557), **ventilé par cause** (#2554 passe 1).
    ///
    /// Les trois nombres couvrent **exactement** les nuits en squelette au début du balayage : c'est ce qui
    /// interdit un reliquat silencieux, et ce que [fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre]
    /// exige d'une ventilation.
    ///
    /// La distinction entre les deux dernières n'est pas cosmétique. Le premier compte rendu annonçait
    /// **« en attente d'analyse Vigie-Chiro »** pour toute nuit non complétée, y compris celles dont le CSV
    /// n'avait pas pu être **lu** : on affirmait une cause qu'on n'avait pas constatée, et on orientait vers
    /// l'attente là où il fallait réessayer.
    ///
    /// @param completees nuits amenées au niveau « contenu »
    /// @param enAttenteDAnalyse nuits dont la plateforme n'expose pas encore le CSV : rien à faire qu'attendre
    /// @param nonLues nuits dont la lecture ou l'écriture a échoué (réseau, refus serveur, écriture
    ///     interrompue) : à reprendre, pas à attendre
    public record BilanCompletion(int completees, int enAttenteDAnalyse, int nonLues) {}

    /// L'issue du téléchargement d'**une** nuit : ses observations, ou l'absence, ou l'échec - trois états
    /// que le seul `Optional` confondait en deux.
    private record IssueTelechargement(Optional<ObservationsAReconstruire> observations, boolean echouee) {

        static IssueTelechargement lue(Optional<ObservationsAReconstruire> observations) {
            return new IssueTelechargement(observations, false);
        }

        static IssueTelechargement echec() {
            return new IssueTelechargement(Optional.empty(), true);
        }
    }

    /// Ce qu'il faut savoir d'une nuit avant de l'hydrater, résolu **une seule fois** : porté de la phase
    /// des lectures à celle des écritures sans les recalculer.
    private record NuitAHydrater(
            Long idPassage, SessionDEnregistrement session, Prefixe prefixe, String idParticipation) {}

    /// Reconnaît une nuit **hydratable**, ou renonce en disant pourquoi (selon la [Source]).
    private Optional<NuitAHydrater> reconnaitre(Long idPassage, Source source) {
        Optional<SessionDEnregistrement> session = sessionDao.trouverParPassage(idPassage);
        if (session.isEmpty() || !sansSequence(session.orElseThrow())) {
            return Optional.empty(); // rien à hydrater : idempotent, et sans objet sur une nuit importée
        }
        SessionDEnregistrement squelette = session.orElseThrow();

        Optional<String> idParticipation = liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage));
        if (idParticipation.isEmpty()) {
            return renoncer(
                    source,
                    "Cette nuit n'a aucune séquence en local et n'est rattachée à aucune participation"
                            + " Vigie-Chiro : il n'existe aucune source d'où récupérer la liste de ses"
                            + " fichiers. Rattachez-la depuis « Sons & validation », puis recommencez.");
        }
        if (importObservations.isEmpty()) {
            return renoncer(
                    source,
                    "Récupérer les observations de cette nuit est impossible : la fonctionnalité"
                            + " « Import Vigie-Chiro » est désactivée.",
                    new Besoin.Fonctionnalite("Import Vigie-Chiro"));
        }
        Optional<Prefixe> prefixe = squelette.prefixe();
        if (prefixe.isEmpty()) {
            return renoncer(
                    source,
                    "Le dossier de cette nuit a été renommé à la main : son préfixe (Car…-2026-Pass1-A1)"
                            + " n'est plus lisible, et les séquences ne peuvent pas être recréées sous le bon"
                            + " nom. Corrigez le nom du dossier, puis recommencez.");
        }
        return Optional.of(
                new NuitAHydrater(idPassage, squelette, prefixe.orElseThrow(), idParticipation.orElseThrow()));
    }

    /// Cette nuit est-elle un **squelette** (#1710) : une session **sans aucune séquence** ?
    private boolean estSquelette(Long idPassage) {
        return sessionDao
                .trouverParPassage(idPassage)
                .filter(this::sansSequence)
                .isPresent();
    }

    private boolean sansSequence(SessionDEnregistrement session) {
        return sequenceDao.findBySession(session.id()).isEmpty();
    }

    /// Un empêchement : **passé sous silence** en [Source#CSV_SEULEMENT] (best-effort d'un balayage, la
    /// nuit sera reprise plus tard), **dit avec sa raison** en [Source#COMPLETE] (l'utilisateur attend une
    /// réponse sur la nuit qu'il vient de désigner).
    private static <T> Optional<T> renoncer(Source source, String raison) {
        if (source == Source.COMPLETE) {
            throw new RegleMetierException(raison);
        }
        return Optional.empty();
    }

    /// Même renoncement, mais le refus porte ce qui **manque** (#2635) : la surface y ajoutera le geste.
    private static <T> Optional<T> renoncer(Source source, String raison, Besoin besoin) {
        if (source == Source.COMPLETE) {
            throw new RegleMetierException(raison, besoin);
        }
        return Optional.empty();
    }

    private Optional<ObservationsAReconstruire> telecharger(
            String idParticipation, Source source, Consumer<Progression> suivi, JetonAnnulation jeton) {
        ImportObservations importateur = importObservations.orElseThrow();
        return source == Source.CSV_SEULEMENT
                ? plateforme.observationsCsv(idParticipation, importateur, suivi, jeton)
                : Optional.of(plateforme.observations(idParticipation, importateur, suivi, jeton));
    }

    /// Le CSV d'**une** nuit du balayage, sans rien dire de son avancement propre : c'est le **lot** qui
    /// rythme (« Nuits k/N »), pas chaque nuit. Un échec réseau isolé n'écarte pas les autres.
    private IssueTelechargement telecharger(NuitAHydrater nuit) {
        try {
            return IssueTelechargement.lue(
                    telecharger(nuit.idParticipation(), Source.CSV_SEULEMENT, progres -> {}, JetonAnnulation.neutre()));
        } catch (RuntimeException indisponible) {
            consigner(nuit.idPassage(), indisponible);
            return IssueTelechargement.echec();
        }
    }

    /// L'étape qui **écrit** : les lignes de séquences (une transaction), puis les observations qui s'y
    /// rattachent par nom.
    ///
    /// Toute interruption après la première écriture est **compensée** en supprimant les originaux que
    /// cette hydratation vient de poser ; la cascade `ON DELETE CASCADE` emporte les séquences avec eux, et
    /// la nuit **redevient exactement un squelette**. On ne peut pas défaire en supprimant le passage,
    /// comme le fait la reconstruction : ici le passage doit survivre. Mais l'état de repli est déjà un
    /// état légal du modèle, il n'y a donc rien à inventer.
    private BilanHydratation ecrire(
            NuitAHydrater nuit, ObservationsAReconstruire observations, Consumer<Progression> progres) {
        SessionDEnregistrement session = nuit.session();
        Set<Long> avant = originauxDe(session);
        progres.accept(new Progression("Création des séquences…", FRACTION_SEQUENCES));
        int sequences = structure.hydraterSequences(session.id(), nuit.prefixe(), observations.nomsFichiers());
        try {
            progres.accept(new Progression("Import des observations…", FRACTION_IMPORT));
            observations.importer(nuit.idPassage());
        } catch (RuntimeException interruption) {
            annulerHydratationPartielle(session, avant, interruption);
            throw interruption;
        }
        return new BilanHydratation(sequences, observations.nbObservations());
    }

    /// Retire les originaux **posés par cette hydratation** (et, en cascade, leurs séquences). On compare à
    /// l'inventaire d'**avant** plutôt que de tout supprimer : un squelette n'a en principe aucun original,
    /// mais compenser en s'appuyant sur cette supposition détruirait des données le jour où elle serait
    /// fausse. Best-effort : un échec de compensation est **attaché** à la cause d'origine plutôt que de la
    /// masquer (observabilité, #1523).
    private void annulerHydratationPartielle(SessionDEnregistrement session, Set<Long> avant, RuntimeException cause) {
        try {
            for (Long idOriginal : originauxDe(session)) {
                if (!avant.contains(idOriginal)) {
                    originalDao.delete(idOriginal);
                }
            }
        } catch (RuntimeException echecCompensation) {
            cause.addSuppressed(echecCompensation);
        }
    }

    private Set<Long> originauxDe(SessionDEnregistrement session) {
        List<EnregistrementOriginal> originaux = originalDao.findBySession(session.id());
        Set<Long> ids = new HashSet<>();
        originaux.forEach(original -> ids.add(original.id()));
        return ids;
    }

    /// Consigne l'échec d'**une** nuit d'un balayage, au niveau qui correspond à sa **nature** (ADR 0008).
    ///
    /// Un balayage best-effort continue, mais continuer n'est pas oublier : sans trace, un utilisateur qui
    /// signale « il me reste douze nuits vides » ne laisse rien à quoi se raccrocher. La distinction de
    /// niveau importe autant que la trace elle-même - une analyse non terminée sur la plateforme est une
    /// issue **normale** ; si elle partait en SEVERE, elle noierait les vrais bugs qu'on cherche ici.
    private static void consigner(Long idPassage, RuntimeException echec) {
        if (echec instanceof RegleMetierException) {
            LOG.fine(() -> "Nuit " + idPassage + " laissée incomplète : " + echec.getMessage());
        } else {
            LOG.log(Level.WARNING, echec, () -> "Échec inattendu en complétant la nuit " + idPassage + ".");
        }
    }

    /// Relaie le **libellé** d'une progression en gardant la fraction à zéro.
    ///
    /// À l'appelant de décider, parce que lui seul sait **à qui appartient la barre** (#2554, passe 7) :
    ///
    /// - l'hydratation est une **sous-étape** (phase 0 d'une réactivation, une nuit parmi N d'un balayage) :
    ///   la barre appartient à l'opération englobante, et
    ///   [fr.univ_amu.iut.commun.viewmodel.ProgressionOperation] la garde **monotone** (#814). Laisser
    ///   passer les fractions d'ici l'épinglerait au plus haut atteint, et la suite resterait **invisible**
    ///   jusqu'à l'avoir dépassé. La barre reste donc vide pendant que le libellé avance ;
    /// - l'hydratation est **toute l'opération** (« Compléter cette nuit ») : la barre est la sienne, et
    ///   l'aplatir la figerait sans raison.
    ///
    /// Ce choix vivait ici, donc s'imposait aux deux : router la modale de complétion vers l'hydratation
    /// lui aurait donné une barre morte.
    public static Consumer<Progression> libelleSeul(Consumer<Progression> progres) {
        return point -> progres.accept(new Progression(point.libelle(), 0.0));
    }
}

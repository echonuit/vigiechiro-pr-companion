package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro.Phase;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.PointParLocalite;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/// **Reconstruit un passage jamais importé sur cette machine** (#1305, EPIC #1297), à partir de ce que
/// la plateforme VigieChiro en sait.
///
/// Les passages sans audio local forment **deux populations**, et les issues A à G n'en traitaient qu'une :
///
/// 1. **archivé par purge** : on l'a eu, on l'a supprimé (#1300, #1302) — le passage existe en base ;
/// 2. **jamais local** : la participation existe **sur la plateforme** (déposée depuis un autre poste,
///    avant l'application, ou après une réinstallation) mais **rien** n'est en base ici.
///
/// C'est la seconde qui réalise vraiment la promesse du chantier : « voir l'historique de ses passages
/// même sans avoir conservé les données d'origine ». [#reconstruire] recrée un passage local **en état
/// archivé** : ses lignes de séquences existent (sans fichier), ses observations sont rapatriées, il se
/// consulte comme tout passage archivé (#1301) et se réactive si l'utilisateur retrouve ses fichiers
/// (#1302).
///
/// **Pourquoi recréer les séquences.** L'import d'observations rattache chaque ligne à la séquence de
/// **même nom** et **ignore** celles qu'il ne trouve pas : sans lignes de séquences, un passage
/// reconstruit n'aurait aucune observation. On les crée donc à partir des `titre` des données
/// distantes — ce sont exactement les noms de fichiers attendus.
///
/// **Ce qui manque est dit, pas deviné** ([RapportReconstruction#lacunesConnues]) : ni journal du
/// capteur, ni relevé climatique, ni séquences non identifiées (le serveur ne les connaît pas), ni
/// empreintes (elles se posent à l'import local, qui n'a jamais eu lieu). Une réactivation s'appuiera
/// donc sur la **cascade** (#1309) : noms, durées, et surtout les **cris eux-mêmes**.
///
/// Les DAO sont construits depuis la [SourceDeDonnees] (fins adaptateurs sans état), comme
/// `ServiceAuditCoherence` : le constructeur reste court et le service testable sur une base jetable.
public class ServiceReconstructionPassages implements RapprochementVigieChiro {

    private final PassageDao passageDao;
    private final LienVigieChiroDao liens;

    /// Pour reconnaître un **squelette** (#1710) : un passage rattaché mais dont la session archivée n'a
    /// **aucune séquence** est une nuit rapatriée par la synchro (#1707) pas encore hydratée. Reconstruire
    /// une telle nuit la **remplace** au lieu de la refuser.
    private final SessionDao sessionDao;

    private final SequenceDao sequenceDao;

    /// Toutes les lectures distantes de la reconstruction (participations, détail, source des observations
    /// CSV #1565 / donnees), extraites dans un collaborateur dédié (plafond God Class).
    private final PlateformeReconstruction plateforme;

    private final PointParLocalite pointParLocalite;

    /// Port de l'import des observations (#1264), **optionnel** comme partout ailleurs : la feature
    /// « Import VigieChiro » est désactivable (#1057), et un module ne peut pas exiger en dur ce qu'une
    /// autre feature fournit : l'injecteur ne se construirait plus. Absent, [#reconstruire] le **dit**.
    private final Optional<ImportObservations> importObservations;

    /// Noyau de **structure** (#1662, EPIC B) : crée le squelette local du passage archivé (passage +
    /// session archivée + séquences + enregistreur/météo/micro). La reconstruction le **compose** avec
    /// l'import des observations ; la synchro « mes sites » le réutilisera pour rapatrier la structure.
    private final CreationPassageArchive creationStructure;

    public ServiceReconstructionPassages(
            SourceDeDonnees source,
            ClientVigieChiro client,
            PointParLocalite pointParLocalite,
            Optional<ImportObservations> importObservations,
            Workspace workspace,
            Horloge horloge) {
        Objects.requireNonNull(source, "source");
        this.passageDao = new PassageDao(source);
        this.liens = new LienVigieChiroDao(source);
        this.sessionDao = new SessionDao(source);
        this.sequenceDao = new SequenceDao(source);
        this.plateforme = new PlateformeReconstruction(client);
        this.pointParLocalite = Objects.requireNonNull(pointParLocalite, "pointParLocalite");
        this.importObservations = Objects.requireNonNull(importObservations, "importObservations");
        this.creationStructure = new CreationPassageArchive(source, workspace, horloge);
    }

    /// Participations de la plateforme **à reconstruire ici** : celles qui n'ont aucun passage local, **ou**
    /// dont le passage local n'est qu'un **squelette** rapatrié par la synchro (#1707) — point + date, sans
    /// séquences — qu'il reste à **hydrater** (#1710). Chacune dit si son point est déjà connu localement.
    ///
    /// Depuis #1707, la synchro consomme en squelettes les orphelines « jamais vues » ; sans les inclure
    /// ici, la liste de reconstruction serait vide juste après une synchro, et les nuits rapatriées
    /// resteraient inhydratables.
    ///
    /// @throws RegleMetierException hors connexion, ou si la plateforme est injoignable (avec la cause)
    public List<ParticipationOrpheline> orphelines() {
        Map<String, Long> passageParParticipation = passagesParParticipation();
        return plateforme.participations().stream()
                .filter(participation -> aReconstruire(passageParParticipation.get(participation.id())))
                .map(this::enOrpheline)
                .toList();
    }

    /// Une participation est **à reconstruire** si elle n'a pas de passage local (`idPassageLie == null`) ou
    /// si ce passage est un **squelette** (rattaché, sans séquence) qu'il reste à hydrater (#1710).
    private boolean aReconstruire(Long idPassageLie) {
        return idPassageLie == null || estSquelette(idPassageLie);
    }

    /// Inverse de [LienVigieChiroDao#tous] pour l'entité passage : identifiant de participation distante →
    /// passage local rattaché.
    private Map<String, Long> passagesParParticipation() {
        Map<String, Long> parParticipation = new HashMap<>();
        liens.tous(LienVigieChiro.ENTITE_PASSAGE)
                .forEach(
                        (idPassage, idParticipation) -> parParticipation.put(idParticipation, Long.valueOf(idPassage)));
        return parParticipation;
    }

    /// **Rapprocheur de structure** (#1707, EPIC #1662) : à la synchronisation « mes sites », rapatrie sous
    /// forme de **squelette** (point + date + n°, sans observations) chaque participation de la plateforme
    /// dont le point est déjà local mais qui n'a **pas encore** de passage ici. Ainsi la synchro ne ramène
    /// plus seulement les sites, mais aussi l'**historique des nuits** ; l'utilisateur les hydrate ensuite à
    /// la demande (reconstruction/réactivation, #1710).
    ///
    /// Contrat **best-effort** du port : ne lève jamais. Hors connexion ou plateforme injoignable, silence
    /// légitime (le rapprocheur des sites porte déjà le souci dans le même geste). Renvoie un rapport
    /// seulement s'il y a du neuf à annoncer.
    ///
    /// **Ordre.** Ne crée un squelette que pour les points **déjà** locaux. Ce rapprocheur est donc en
    /// [Phase#DEPENDANTE] : la synchro le rejoue **après** le rapprocheur des sites (#1776), si bien qu'un
    /// site tout juste synchronisé voit ses passages **dès ce tour**. La synchro reste idempotente :
    /// relancée, elle ne recrée pas ce qui est déjà rattaché.
    @Override
    public Optional<RapportSynchro> synchroniser(ClientVigieChiro client) {
        try {
            int crees = synchroniserStructure();
            return crees == 0 ? Optional.empty() : Optional.of(new RapportSynchro("passage(s) rapatrié(s)", crees));
        } catch (RuntimeException echecBestEffort) {
            return Optional.empty();
        }
    }

    /// [Phase#DEPENDANTE] : les passages se rapatrient sur des points d'écoute **déjà locaux**, créés par le
    /// rapprocheur des sites ([Phase#STRUCTURE]) - la synchro rejoue donc ce rapprocheur après lui (#1776).
    @Override
    public Phase phase() {
        return Phase.DEPENDANTE;
    }

    /// Crée un passage archivé pour chaque participation **sans passage local**, dont le point est déjà
    /// local et la nuit datable ; les autres sont **ignorées** (déjà rapatriées, ou pas encore situables).
    ///
    /// Depuis #1814, la synchro remonte l'**identité** de la nuit (enregistreur, météo, micro, dateFin) et
    /// pas seulement sa structure : elle paie un **appel de détail par nuit nouvelle**. En trois temps :
    ///
    /// 1. **candidats** (lectures) : les nuits nouvelles, point + date résolus ;
    /// 2. **détails** ([PlateformeReconstruction#detailsBestEffort]) : le détail de chaque nuit, best-effort
    ///    (indisponible → vide, la nuit n'est pas écartée pour autant) ;
    /// 3. **création** (écritures) : dans l'ordre, pour que [#premierNumeroLibre] s'enchaîne (numéros
    ///    successifs pour un même point/année), via [CreationPassageArchive#creerNuitRapatriee] : avec
    ///    identité si le détail est là, repli sur le squelette nu (INCONNU) sinon.
    ///
    /// @return le nombre de passages créés
    int synchroniserStructure() {
        Map<String, Long> passageParParticipation = passagesParParticipation();
        List<NuitARapatrier> candidats = new ArrayList<>();
        for (ParticipationVigieChiro participation : plateforme.participations()) {
            // Une nuit qui a déjà un passage local (squelette OU hydraté) n'est pas rapatriée une seconde
            // fois : c'est ce qui rend la synchro idempotente (#1707). On n'itère donc pas [#orphelines],
            // qui inclut désormais les squelettes à hydrater (#1710) — les recréer ferait des doublons.
            if (passageParParticipation.containsKey(participation.id())) {
                continue;
            }
            ParticipationOrpheline orpheline = enOrpheline(participation);
            Optional<Long> idPoint = pointParLocalite.pour(orpheline.numeroCarre(), orpheline.codePoint());
            Optional<LocalDateTime> debut = ParticipationOrpheline.horodatage(orpheline.dateDebut());
            if (idPoint.isEmpty() || debut.isEmpty()) {
                continue;
            }
            candidats.add(new NuitARapatrier(orpheline, idPoint.get(), debut.get()));
        }
        if (candidats.isEmpty()) {
            return 0;
        }
        List<Optional<ParticipationDetail>> details = plateforme.detailsBestEffort(candidats.stream()
                .map(candidat -> candidat.orpheline().idParticipation())
                .toList());
        int crees = 0;
        for (int i = 0; i < candidats.size(); i++) {
            NuitARapatrier candidat = candidats.get(i);
            Optional<ParticipationDetail> detail = details.get(i);
            int annee = candidat.debut().getYear();
            int numeroPassage = creationStructure.premierNumeroLibre(candidat.idPoint(), annee);
            Prefixe prefixe = new Prefixe(
                    candidat.orpheline().numeroCarre(),
                    annee,
                    numeroPassage,
                    candidat.orpheline().codePoint());
            LocalDateTime fin = detail.flatMap(connu -> ParticipationOrpheline.horodatage(connu.dateFin()))
                    .orElse(candidat.debut());
            Long idPassage = creationStructure
                    .creerNuitRapatriee(candidat.idPoint(), numeroPassage, candidat.debut(), fin, prefixe, detail)
                    .idPassage();
            liens.upsert(new LienVigieChiro(
                    LienVigieChiro.ENTITE_PASSAGE,
                    String.valueOf(idPassage),
                    candidat.orpheline().idParticipation()));
            crees++;
        }
        return crees;
    }

    /// Une nuit nouvelle à rapatrier : l'orpheline distante et sa résolution locale (point + début), portées
    /// de la phase « candidats » (lectures) à la phase « création » (écritures) sans les recalculer.
    private record NuitARapatrier(ParticipationOrpheline orpheline, Long idPoint, LocalDateTime debut) {}

    /// Reconstruit localement la participation `idParticipation` en **passage archivé** : passage, session
    /// (marquée archivée), lignes de séquences sans fichier, puis rapatriement des observations.
    ///
    /// Variante **non suivie** : sans progression ni annulation (jeton neutre). Sert la CLI et les appels
    /// qui n'offrent pas de barre. L'IHM passe par la variante suivie depuis une orpheline.
    ///
    /// @throws RegleMetierException si la participation est déjà rattachée à un passage **déjà reconstruit**
    ///     (un squelette rapatrié, lui, est remplacé), si son point n'existe pas ici, si sa nuit est
    ///     indatable, ou si ses observations ne sont pas récupérables (le message dit laquelle de ces raisons)
    public RapportReconstruction reconstruire(String idParticipation) {
        return reconstruire(idParticipation, progression -> {}, JetonAnnulation.neutre());
    }

    /// Variante **suivie et annulable** par identifiant (chemin CLI, qui ne connaît que l'`_id`) : le
    /// carré et la localité, absents du détail par id, sont retrouvés via le résumé du compte - une
    /// lecture de la **liste entière** des participations. L'IHM, qui tient déjà la nuit choisie, évite ce
    /// coût par la variante depuis une orpheline.
    public RapportReconstruction reconstruire(
            String idParticipation, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(idParticipation, "idParticipation");
        ParticipationVigieChiro resume = plateforme.resume(idParticipation);
        return reconstruire(idParticipation, ParticipationOrpheline.carreDe(resume), resume.point(), progres, jeton);
    }

    /// Variante **suivie et annulable** depuis une [ParticipationOrpheline] déjà en main (chemin IHM) :
    /// carré et localité en sont tirés directement, **sans re-télécharger toute la liste** des
    /// participations pour retrouver une nuit qu'on a déjà sélectionnée (#1522).
    public RapportReconstruction reconstruire(
            ParticipationOrpheline orpheline, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(orpheline, "orpheline");
        return reconstruire(
                orpheline.idParticipation(), orpheline.numeroCarre(), orpheline.codePoint(), progres, jeton);
    }

    /// **Import groupé** (#1708) : reconstruit **toutes** les nuits de `aTraiter`, l'une après l'autre. Le
    /// geste - boucle, best-effort par nuit, accumulation d'un bilan - vit **ici**, au service, et les deux
    /// surfaces (IHM et CLI) l'appellent en ne gardant que leur **rendu** (barres de progression / lignes) via
    /// les rappels. C'est l'harmonisation de la passe 7 : un seul endroit porte la **politique best-effort**.
    ///
    /// - `progresGlobal` : émis **avant** chaque nuit (« Nuit X / N »), pour la barre du **lot** (IHM) ;
    /// - `progresParNuit` : émis **pendant** chaque nuit, pour la barre de la **nuit courante** (IHM) ;
    /// - `issueParNuit` : émis **après** chaque nuit, son issue (reconstruite/ignorée), pour la **ligne** CLI ;
    /// - `jeton` : consulté entre chaque nuit et à l'intérieur.
    ///
    /// **Best-effort par nuit** : une nuit qui échoue pour une raison métier est **comptée « ignorée » et
    /// sautée**, le lot continue. Une **annulation** ([OperationAnnuleeException]) arrête tout le lot (geste
    /// délibéré). Réutilise [#reconstruire] par nuit : aucune logique d'import dupliquée.
    public BilanReconstructionGroupe reconstruireTout(
            List<ParticipationOrpheline> aTraiter,
            Consumer<Progression> progresGlobal,
            Consumer<Progression> progresParNuit,
            Consumer<IssueNuit> issueParNuit,
            JetonAnnulation jeton) {
        Objects.requireNonNull(aTraiter, "aTraiter");
        int total = aTraiter.size();
        int reussies = 0;
        int ignorees = 0;
        long sequences = 0;
        long observations = 0;
        for (int index = 0; index < total; index++) {
            jeton.leverSiAnnule();
            ParticipationOrpheline nuit = aTraiter.get(index);
            progresGlobal.accept(new Progression(
                    "Nuit " + (index + 1) + " / " + total + "…", total == 0 ? 1.0 : (double) index / total));
            try {
                RapportReconstruction rapport = reconstruire(nuit, progresParNuit, jeton);
                reussies++;
                sequences += rapport.sequencesRecreees();
                observations += rapport.observationsImportees();
                issueParNuit.accept(new IssueNuit.Reconstruite(nuit, rapport));
            } catch (OperationAnnuleeException annulation) {
                throw annulation; // geste délibéré : arrête tout le lot
            } catch (RegleMetierException echecNuit) {
                ignorees++; // best-effort : cette nuit est sautée, le lot continue
                issueParNuit.accept(new IssueNuit.Ignoree(nuit, echecNuit.getMessage()));
            }
        }
        progresGlobal.accept(new Progression("Terminé.", 1.0));
        return new BilanReconstructionGroupe(reussies, ignorees, sequences, observations);
    }

    /// Bilan d'un import groupé (#1708) : combien de nuits **reconstruites**, combien **ignorées**
    /// (best-effort : point d'écoute inconnu ici, analyse non terminée), et les totaux de séquences et
    /// d'observations rapatriées.
    public record BilanReconstructionGroupe(int reussies, int ignorees, long sequences, long observations) {}

    /// Issue d'**une** nuit dans un import groupé (#1708) : **reconstruite** (avec son rapport) ou
    /// **ignorée** (best-effort, avec la cause). Permet à chaque surface son rendu - une ligne en CLI, un
    /// compteur de barre globale en IHM - sans que le service ne connaisse ni l'une ni l'autre.
    public sealed interface IssueNuit permits IssueNuit.Reconstruite, IssueNuit.Ignoree {

        ParticipationOrpheline nuit();

        record Reconstruite(ParticipationOrpheline nuit, RapportReconstruction rapport) implements IssueNuit {}

        record Ignoree(ParticipationOrpheline nuit, String cause) implements IssueNuit {}
    }

    /// Cœur de la reconstruction, une fois carré et localité connus (quelle que soit leur origine). Émet
    /// des **points de progression** aux étapes lourdes et consulte le **jeton d'annulation** entre elles
    /// (#1252). Dès la première écriture, toute interruption (annulation ou échec) est **compensée** :
    /// aucun passage partiel ne subsiste.
    private RapportReconstruction reconstruire(
            String idParticipation,
            String carre,
            String localite,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(jeton, "jeton");
        remplacerSiSquelette(idParticipation);

        // Vérifié AVANT toute écriture : un passage reconstruit sans ses observations ne serait qu'une
        // coquille, et mieux vaut ne rien créer que créer à moitié.
        ImportObservations importateur = importObservations.orElseThrow(() -> new RegleMetierException(
                "La reconstruction rapatrie les observations depuis VigieChiro, et la fonctionnalité"
                        + " « Import VigieChiro » est désactivée : réactivez-la (menu ☰ > Fonctionnalités)"
                        + " puis recommencez."));

        progres.accept(new Progression("Lecture de la participation…", 0.05));
        jeton.leverSiAnnule();
        ParticipationDetail detail = plateforme.detail(idParticipation);
        Long idPoint = pointParLocalite
                .pour(carre, localite)
                .orElseThrow(() -> new RegleMetierException(
                        "Le point d'écoute de cette participation (carré " + carre + ", localité " + localite
                                + ") n'existe pas localement. Créez d'abord le site et le point, puis"
                                + " recommencez."));
        LocalDateTime debut = ParticipationOrpheline.horodatage(detail.dateDebut())
                .orElseThrow(() -> new RegleMetierException(
                        "La participation ne porte pas de date de début exploitable : impossible de dater la"
                                + " nuit."));
        LocalDateTime fin = ParticipationOrpheline.horodatage(detail.dateFin()).orElse(debut);

        // Où trouver les observations : le CSV téléchargé d'un coup (#1565) si la plateforme l'expose,
        // sinon la pagination donnees (repli, l'ancien chemin). Dans les deux cas on récupère les NOMS des
        // fichiers analysés (pour recréer les séquences, et plus tard reconnaître les fichiers réimportés
        // #1302) et de quoi importer. C'est l'étape réseau : progression et annulation y sont honorées.
        ObservationsAReconstruire observations = plateforme.observations(idParticipation, importateur, progres, jeton);

        // Le préfixe R6 RÉEL de la nuit (carré, année, n° de passage, code du point) : c'est celui que
        // l'audit recalcule depuis le passage (`ServiceAuditCoherence#prefixeAttendu`), et il doit être le
        // même, sans quoi le passage reconstruit serait signalé PREFIXE_NON_CONFORME à vie (#1050).
        int numeroPassage = creationStructure.premierNumeroLibre(idPoint, debut.getYear());
        Prefixe prefixe = new Prefixe(carre, debut.getYear(), numeroPassage, localite);

        // À partir d'ici on ÉCRIT. La vraie transaction unique est hors de portée (l'import ouvre la
        // sienne sur une base SQLite mono-écrivain) ; à la place, toute interruption défait ce qui a été
        // créé - le schéma en ON DELETE CASCADE rend cette compensation sûre (#1522).
        Long idPassage = null;
        try {
            jeton.leverSiAnnule();
            // Structure locale (passage archivé + session + séquences + enregistreur/météo/micro) : le
            // noyau réutilisable émet ses propres points de progression (« Création du passage… » puis
            // « Création des séquences… ») aux mêmes fractions qu'avant.
            CreationPassageArchive.PassageArchive structure = creationStructure.creer(
                    idPoint, numeroPassage, debut, fin, prefixe, detail, observations.nomsFichiers(), progres);
            idPassage = structure.idPassage();
            liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), idParticipation));

            // L'import rattache chaque ligne à la séquence de même nom - celles qu'on vient de recréer
            // (mécanisme du port socle, EPIC #1259). Le geste concret (CSV ou donnees) est déjà choisi.
            progres.accept(new Progression("Import des observations…", 0.96));
            jeton.leverSiAnnule();
            observations.importer(idPassage);
            progres.accept(new Progression("Terminé.", 1.0));
            return new RapportReconstruction(
                    idPassage,
                    structure.nbSequences(),
                    observations.nbObservations(),
                    RapportReconstruction.lacunesConnues());
        } catch (RuntimeException interruption) {
            if (idPassage != null) {
                annulerReconstructionPartielle(idPassage, interruption);
            }
            throw interruption;
        }
    }

    /// Défait une reconstruction interrompue : retire le lien VigieChiro (posé ou non) puis supprime le
    /// passage, dont la suppression **cascade** sur session, séquences et observations (`ON DELETE
    /// CASCADE`). Best-effort : un échec de compensation est **attaché** à la cause d'origine plutôt que de
    /// la masquer, pour qu'aucune trace ne se perde (observabilité, #1523).
    private void annulerReconstructionPartielle(Long idPassage, RuntimeException cause) {
        try {
            liens.supprimer(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage));
            passageDao.delete(idPassage);
        } catch (RuntimeException echecCompensation) {
            cause.addSuppressed(echecCompensation);
        }
    }

    // --- Lectures distantes et helpers -------------------------------------------------------------

    /// Si la participation est déjà rattachée à un passage local, deux cas (#1710) :
    ///
    /// - **squelette** (rapatrié par la synchro #1707, sans séquence) : on le **retire** pour le reconstruire
    ///   complet par le même geste qu'une nuit jamais vue (sa session vide part en cascade ; le lien sera
    ///   reposé par la reconstruction). Retiré **avant** les lectures réseau, pour que le numéro de passage
    ///   libéré soit réutilisé ; si un aléa réseau interrompt ensuite, la synchro suivante recrée le squelette
    ///   (idempotente, #1707) — rien n'est perdu durablement.
    /// - **déjà hydraté** (avec séquences) : il n'y a rien à reconstruire, on refuse.
    private void remplacerSiSquelette(String idParticipation) {
        Optional<Long> idPassageLie = passageRattache(idParticipation);
        if (idPassageLie.isEmpty()) {
            return; // vraie orpheline : aucun passage à remplacer
        }
        Long idPassage = idPassageLie.get();
        if (!estSquelette(idPassage)) {
            throw new RegleMetierException(
                    "Cette participation est déjà rattachée à un passage local déjà reconstruit : il n'y a rien"
                            + " à reconstruire.");
        }
        liens.supprimer(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage));
        passageDao.delete(idPassage);
    }

    /// Passage local rattaché à cette participation, s'il en existe un.
    private Optional<Long> passageRattache(String idParticipation) {
        return liens.tous(LienVigieChiro.ENTITE_PASSAGE).entrySet().stream()
                .filter(entree -> entree.getValue().equals(idParticipation))
                .map(entree -> Long.valueOf(entree.getKey()))
                .findFirst();
    }

    /// Un passage est un **squelette** (#1710) s'il porte une session **sans aucune séquence** : une nuit
    /// rapatriée par la synchro (#1707), point + date, jamais hydratée. Un passage reconstruit ou importé, lui,
    /// a des séquences.
    private boolean estSquelette(Long idPassage) {
        return sessionDao
                .trouverParPassage(idPassage)
                .map(session -> sequenceDao.findBySession(session.id()).isEmpty())
                .orElse(false);
    }

    /// L'orpheline correspondant à une participation distante, avec la résolution de son point local.
    private ParticipationOrpheline enOrpheline(ParticipationVigieChiro participation) {
        return ParticipationOrpheline.depuis(
                participation, pointLocal(participation).isPresent());
    }

    private Optional<Long> pointLocal(ParticipationVigieChiro participation) {
        return pointParLocalite.pour(ParticipationOrpheline.carreDe(participation), participation.point());
    }
}

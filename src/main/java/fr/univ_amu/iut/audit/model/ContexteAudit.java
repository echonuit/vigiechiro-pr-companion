package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/// Ce que l'audit lit **une fois pour toutes les nuits** plutôt qu'une fois par nuit (#4286).
///
/// ## Pourquoi ces quatre tables, et pas les six
///
/// Auditer un passage demandait **six requêtes** : sa session, ses originaux, ses séquences, son
/// journal, son relevé climatique, ses résultats Tadarida. Sur trois cents nuits - ce qu'un
/// coordinateur départemental accumule en deux saisons - cela faisait dix-huit cents requêtes, et
/// **900 ms** pour un audit complet.
///
/// Quatre de ces tables portent **une ligne par session ou par passage** : trois cents lignes pour trois
/// cents nuits. Les lire en entier tient en mémoire sans y penser, et le contexte les sert ensuite.
///
/// **Les originaux et les séquences n'y sont pas**, et c'est délibéré : ce sont les tables de
/// volume - une nuit porte des milliers de séquences. Les charger toutes d'un bloc échangerait un
/// défaut de lenteur contre un défaut de mémoire, ce qui n'est pas un progrès. Elles restent lues
/// session par session.
///
/// ## Deux façons de le construire
///
/// [#deTout] lit les quatre tables entières : c'est le chemin de l'audit complet, qui les parcourra
/// toutes de toute façon. [#dUnSeulPassage] ne lit rien d'avance et délègue au DAO : l'audit ciblé
/// d'une nuit (#1347) n'a aucune raison de charger la base pour une ligne.
final class ContexteAudit {

    private final Function<Long, Optional<SessionDEnregistrement>> parPassage;
    private final Function<Long, Optional<JournalDuCapteur>> parSessionJournal;
    private final Function<Long, Optional<ReleveClimatique>> parSessionReleve;
    private final Function<Long, Optional<ResultatsIdentification>> parPassageResultats;
    private final Function<Long, Optional<PointDEcoute>> parIdPoint;
    private final Function<Long, Optional<Site>> parIdSite;
    private final Function<Long, List<DepotUnite>> parPassageDepots;

    private ContexteAudit(
            Function<Long, Optional<SessionDEnregistrement>> parPassage,
            Function<Long, Optional<JournalDuCapteur>> parSessionJournal,
            Function<Long, Optional<ReleveClimatique>> parSessionReleve,
            Function<Long, Optional<ResultatsIdentification>> parPassageResultats,
            Function<Long, Optional<PointDEcoute>> parIdPoint,
            Function<Long, Optional<Site>> parIdSite,
            Function<Long, List<DepotUnite>> parPassageDepots) {
        this.parPassage = parPassage;
        this.parSessionJournal = parSessionJournal;
        this.parSessionReleve = parSessionReleve;
        this.parPassageResultats = parPassageResultats;
        this.parIdPoint = parIdPoint;
        this.parIdSite = parIdSite;
        this.parPassageDepots = parPassageDepots;
    }

    /// Contexte de l'audit **complet** : les quatre tables lues en entier, quatre requêtes en tout.
    static ContexteAudit deTout(
            SessionDao sessions,
            JournalDuCapteurDao journaux,
            ReleveClimatiqueDao releves,
            ResultatsIdentificationDao resultats,
            PointDao points,
            SiteDao sites,
            DepotUniteDao depots) {
        Map<Long, SessionDEnregistrement> parPassage = indexer(sessions, SessionDEnregistrement::idPassage);
        Map<Long, JournalDuCapteur> journauxParSession = indexer(journaux, JournalDuCapteur::idSession);
        Map<Long, ReleveClimatique> relevesParSession = indexer(releves, ReleveClimatique::idSession);
        Map<Long, ResultatsIdentification> resultatsParPassage = indexer(resultats, ResultatsIdentification::idPassage);
        Map<Long, PointDEcoute> pointsParId = indexer(points, PointDEcoute::id);
        Map<Long, Site> sitesParId = indexer(sites, Site::id);
        Map<Long, List<DepotUnite>> depotsParPassage = new HashMap<>();
        for (DepotUnite unite : depots.findAll()) {
            depotsParPassage
                    .computeIfAbsent(unite.passageId(), cle -> new ArrayList<>())
                    .add(unite);
        }
        return new ContexteAudit(
                id -> Optional.ofNullable(parPassage.get(id)),
                id -> Optional.ofNullable(journauxParSession.get(id)),
                id -> Optional.ofNullable(relevesParSession.get(id)),
                id -> Optional.ofNullable(resultatsParPassage.get(id)),
                id -> Optional.ofNullable(pointsParId.get(id)),
                id -> Optional.ofNullable(sitesParId.get(id)),
                id -> depotsParPassage.getOrDefault(id, List.of()));
    }

    /// Contexte de l'audit **ciblé** : rien n'est lu d'avance, chaque question va au DAO. La session
    /// n'y figure pas - l'appelant la tient déjà.
    static ContexteAudit dUnSeulPassage(
            JournalDuCapteurDao journaux,
            ReleveClimatiqueDao releves,
            ResultatsIdentificationDao resultats,
            PointDao points,
            SiteDao sites,
            DepotUniteDao depots) {
        return new ContexteAudit(
                id -> Optional.empty(),
                journaux::trouverParSession,
                releves::trouverParSession,
                resultats::findByPassage,
                points::findById,
                sites::findById,
                depots::parPassage);
    }

    Optional<SessionDEnregistrement> session(Long idPassage) {
        return parPassage.apply(idPassage);
    }

    Optional<JournalDuCapteur> journal(Long idSession) {
        return parSessionJournal.apply(idSession);
    }

    Optional<ReleveClimatique> releve(Long idSession) {
        return parSessionReleve.apply(idSession);
    }

    Optional<ResultatsIdentification> resultats(Long idPassage) {
        return parPassageResultats.apply(idPassage);
    }

    Optional<PointDEcoute> point(Long idPoint) {
        return parIdPoint.apply(idPoint);
    }

    Optional<Site> site(Long idSite) {
        return parIdSite.apply(idSite);
    }

    List<DepotUnite> depots(Long idPassage) {
        return parPassageDepots.apply(idPassage);
    }

    private static <T> Map<Long, T> indexer(
            fr.univ_amu.iut.commun.persistence.DaoGenerique<T, Long> dao, Function<T, Long> cle) {
        Map<Long, T> index = new HashMap<>();
        for (T ligne : dao.findAll()) {
            index.put(cle.apply(ligne), ligne);
        }
        return index;
    }
}

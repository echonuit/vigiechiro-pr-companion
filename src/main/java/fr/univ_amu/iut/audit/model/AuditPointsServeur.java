package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.DistanceGeo;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Audit **en ligne des points d'écoute** (#1178) : confronte chaque point local à sa localité serveur
/// (via le lien `vigiechiro_link` `ENTITE_SITE` du site) et signale les divergences (point local inconnu
/// du serveur, position différente). Lecture seule ; dégrade en `INFO` si le serveur est injoignable.
///
/// ## Les deux sens, et pourquoi ils ne se valent pas (#1455)
///
/// Le **sens local → serveur** signale tout écart : un point d'ici que la plateforme ne connaît pas est
/// un [CategorieConstat#POINT_DIVERGENT], toujours.
///
/// Le **sens serveur → local** ne peut pas être aussi bavard. Une localité que la plateforme connaît et
/// qu'on n'a pas ici est **créée en silence** au prochain rapprochement - et c'est le comportement
/// **voulu** : c'est exactement ce qui rend possible la restauration depuis une base vierge (#1050) et le
/// reset guidé (#1419). Le signaler à chaque fois noierait l'audit sous le bruit d'un premier
/// rapprochement.
///
/// Reste la question qui compte : **quand ce silence masque-t-il quelque chose ?** Réponse : quand la
/// localité **porte des nuits** qu'on n'a pas ici. Le point créé sans bruit dissimule alors du **travail
/// qui existe ailleurs** - et ça, il faut le dire ([CategorieConstat#POINT_SERVEUR_IGNORE]).
public final class AuditPointsServeur {

    /// Nombre de nuits citées nommément dans le constat : au-delà, le compte parle mieux que la liste.
    private static final int NUITS_CITEES = 3;

    private final ClientVigieChiro client;
    private final SiteDao siteDao;
    private final PointDao pointDao;
    private final LienVigieChiroDao liens;
    private final String idUtilisateur;

    /// Reconstruction (#1305) : elle sait déjà dire quelles participations de la plateforme n'ont **aucun**
    /// équivalent ici, et si leur localité existe localement. L'audit s'appuie dessus plutôt que de
    /// recalculer la même chose. `Optional` : la feature « reconstruire » est désactivable, et l'audit
    /// doit alors se taire sur ce sens, pas échouer.
    private final Optional<ServiceReconstructionPassages> reconstruction;

    public AuditPointsServeur(
            ClientVigieChiro client,
            SiteDao siteDao,
            PointDao pointDao,
            LienVigieChiroDao liens,
            String idUtilisateur,
            Optional<ServiceReconstructionPassages> reconstruction) {
        this.client = Objects.requireNonNull(client, "client");
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        this.reconstruction = Objects.requireNonNull(reconstruction, "reconstruction");
    }

    List<ConstatAudit> auditer() {
        // Depuis #1284, le constat dit la vraie cause : « non connecté », « injoignable », « refusé »
        // et « aucun site distant » ne se confondent plus dans un même message.
        return switch (client.mesSites()) {
            case ReponseApi.Succes<List<SiteVigieChiro>>(List<SiteVigieChiro> distants) -> auditerLesDeuxSens(distants);
            case ReponseApi.NonConnecte<List<SiteVigieChiro>> nonConnecte ->
                List.of(constatIndisponible("non connecté à Vigie-Chiro (aucun jeton)."));
            case ReponseApi.Injoignable<List<SiteVigieChiro>>(String cause) ->
                List.of(constatIndisponible("Vigie-Chiro injoignable (" + cause + ")."));
            case ReponseApi.Refuse<List<SiteVigieChiro>>(int statut, String corps) ->
                List.of(constatIndisponible("Vigie-Chiro a refusé la lecture (HTTP " + statut + ")."));
        };
    }

    private List<ConstatAudit> auditerLesDeuxSens(List<SiteVigieChiro> distants) {
        List<ConstatAudit> constats = new ArrayList<>(confronterTous(distants));
        constats.addAll(localitesQuiCachentDuTravail());
        return List.copyOf(constats);
    }

    /// Sens **serveur → local** : les localités que la plateforme connaît, qu'on n'a pas ici, et qui
    /// **portent des nuits absentes d'ici**. Une localité inconnue qui ne porte **rien** ne dit rien : sa
    /// création silencieuse est le comportement voulu.
    private List<ConstatAudit> localitesQuiCachentDuTravail() {
        if (reconstruction.isEmpty()) {
            return List.of(); // feature « reconstruire » désactivée : ce sens n'est pas auditable
        }
        List<ParticipationOrpheline> orphelines;
        try {
            orphelines = reconstruction.get().orphelines();
        } catch (RegleMetierException indisponible) {
            // Le service lève quand la plateforme ne répond plus entre deux appels : on dégrade en INFO,
            // comme le reste de l'audit en ligne : jamais d'échec dur.
            return List.of(constatIndisponible(indisponible.getMessage()));
        }
        Map<String, List<ParticipationOrpheline>> parLocalite = orphelines.stream()
                .filter(orpheline -> !orpheline.pointLocalConnu())
                .filter(orpheline -> orpheline.codePoint() != null)
                .collect(Collectors.groupingBy(AuditPointsServeur::cibleDe, LinkedHashMap::new, Collectors.toList()));

        return parLocalite.entrySet().stream()
                .map(localite -> new ConstatAudit(
                        Severite.AVERTISSEMENT,
                        CategorieConstat.POINT_SERVEUR_IGNORE,
                        null,
                        localite.getKey(),
                        detailDe(localite.getValue())))
                .toList();
    }

    private static String cibleDe(ParticipationOrpheline orpheline) {
        String carre = orpheline.numeroCarre() != null ? orpheline.numeroCarre() : "?";
        return carre + " / " + orpheline.codePoint();
    }

    /// Le constat nomme les nuits : c'est le **travail** qu'on ne verrait pas, pas la localité, qui rend
    /// l'écart intéressant.
    private static String detailDe(List<ParticipationOrpheline> nuits) {
        List<String> dates = nuits.stream()
                .map(ParticipationOrpheline::dateDebut)
                .filter(Objects::nonNull)
                .map(date -> date.length() >= 10 ? date.substring(0, 10) : date)
                .sorted()
                .toList();
        String citees = dates.stream().limit(NUITS_CITEES).collect(Collectors.joining(", "));
        String reste = dates.size() > NUITS_CITEES ? ", et " + (dates.size() - NUITS_CITEES) + " autre(s)" : "";
        String enumeration = dates.isEmpty() ? "" : " (" + citees + reste + ")";
        return "Vigie-Chiro connaît cette localité, que vous n'avez pas ici, et elle porte " + nuits.size()
                + " nuit(s) absente(s) d'ici" + enumeration
                + ". Le prochain rapprochement créerait le point sans rien dire : reconstruisez ces nuits, "
                + "ou vérifiez que vous travaillez bien sur le bon poste.";
    }

    private static ConstatAudit constatIndisponible(String precision) {
        return new ConstatAudit(
                Severite.INFO,
                CategorieConstat.SERVEUR_INJOIGNABLE,
                null,
                "-",
                "Points serveur indisponibles : " + precision);
    }

    /// Confrontation de chaque point local lié à sa localité serveur, sur des sites effectivement lus.
    private List<ConstatAudit> confronterTous(List<SiteVigieChiro> distants) {
        if (distants.isEmpty()) {
            return List.of(constatIndisponible("aucun site distant (ce compte ne participe à aucun site)."));
        }
        Map<String, SiteVigieChiro> parObjectid =
                distants.stream().collect(Collectors.toMap(SiteVigieChiro::id, Function.identity(), (a, b) -> a));
        List<ConstatAudit> constats = new ArrayList<>();
        for (Site local : siteDao.findByUtilisateur(idUtilisateur)) {
            Optional<String> objectid = liens.objectidPour(LienVigieChiro.ENTITE_SITE, String.valueOf(local.id()));
            if (objectid.isEmpty()) {
                continue; // site non lié au serveur : hors périmètre de l'audit en ligne
            }
            SiteVigieChiro distant = parObjectid.get(objectid.get());
            if (distant == null) {
                continue; // lien périmé : le site distant n'est plus dans mesSites
            }
            Map<String, PointVigieChiro> pointsServeur = distant.points().stream()
                    .collect(Collectors.toMap(PointVigieChiro::code, Function.identity(), (a, b) -> a));
            for (PointDEcoute point : pointDao.findBySite(local.id())) {
                confronter(constats, local, point, pointsServeur.get(point.code()));
            }
        }
        return constats;
    }

    private void confronter(List<ConstatAudit> constats, Site site, PointDEcoute local, PointVigieChiro distant) {
        String cible = site.numeroCarre() + " / " + local.code();
        if (distant == null) {
            constats.add(new ConstatAudit(
                    Severite.AVERTISSEMENT,
                    CategorieConstat.POINT_DIVERGENT,
                    null,
                    cible,
                    "Point local inconnu du serveur (créé localement, ou supprimé côté serveur)."));
        } else if (positionDiffere(local, distant)) {
            constats.add(new ConstatAudit(
                    Severite.AVERTISSEMENT,
                    CategorieConstat.POINT_DIVERGENT,
                    null,
                    cible,
                    "Position différente du serveur (local " + local.latitude() + "," + local.longitude()
                            + " vs serveur " + distant.latitude() + "," + distant.longitude() + ")."));
        }
    }

    /// Les deux positions diffèrent-elles assez pour être **deux endroits** (#3750) ?
    ///
    /// ⚠️ La comparaison se faisait **axe par axe, en degrés** (`1e-4`). Un degré n'est pas une distance :
    /// la tolérance en longitude valait ~11 m à l'équateur mais ~7,8 m à 45° N, et l'audit se resserrait
    /// donc en montant vers le nord sans que personne ne l'ait voulu. Elle passe désormais par
    /// [DistanceGeo#memeEndroit], en mètres, avec **le seuil que la publication d'un point utilise
    /// aussi** : les deux répondaient à la même question et pouvaient se contredire sous les yeux de
    /// l'utilisateur.
    private static boolean positionDiffere(PointDEcoute local, PointVigieChiro distant) {
        if (local.latitude() == null || local.longitude() == null) {
            return false; // pas de position locale à comparer
        }
        return !DistanceGeo.memeEndroit(local.latitude(), local.longitude(), distant.latitude(), distant.longitude());
    }
}

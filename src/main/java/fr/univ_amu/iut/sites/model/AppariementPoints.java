package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.LocalitesDuSite;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.DistanceGeo;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// Apparie les points **saisis avant connexion** avec les localités que la plateforme porte déjà au même
/// endroit sous un autre nom (#3750).
///
/// ## Ce que cette classe fait, et ce qu'elle ne fait pas
///
/// Elle **dit**, elle n'écrit rien. Aucune fusion n'est appliquée ici : le rapprochement d'un point local
/// et d'une localité distante demande un choix explicite de l'utilisateur, et
/// [RapprochementSites#completerLesPoints] a délibérément laissé ce choix hors de la synchro
/// automatique.
///
/// ## Pourquoi le nom du point local doit changer, et jamais l'inverse
///
/// Sur la plateforme, une participation **nomme** sa localité (`'point'` est une chaîne au schéma des
/// participations). Fusionner revient donc à **adopter le nom distant** pour le point local. Déplacer ou
/// renommer la localité en ligne emporterait toutes les nuits qui s'y rattachent, y compris celles
/// d'autres observateurs - ce que la publication d'un point refuse déjà
/// ([PublicationPoint.Resultat.AilleursSurLaPlateforme]).
///
/// **Bloquant** (réseau) : à appeler hors du fil JavaFX.
public class AppariementPoints {

    private final ClientVigieChiro client;
    private final ServiceSites serviceSites;
    private final LienVigieChiroDao liens;
    private final PassageDao passages;

    public AppariementPoints(
            ClientVigieChiro client, ServiceSites serviceSites, LienVigieChiroDao liens, PassageDao passages) {
        this.client = Objects.requireNonNull(client, "client");
        this.serviceSites = Objects.requireNonNull(serviceSites, "serviceSites");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.passages = Objects.requireNonNull(passages, "passages");
    }

    /// Ce qu'on peut dire d'un point local face aux localités distantes.
    public sealed interface Verdict {

        /// Une localité distante, et une seule, occupe le même endroit sous un autre nom : la fusion a un
        /// sens, et c'est à l'utilisateur de la demander.
        record Fusionnable(PointVigieChiro distante, double ecartMetres) implements Verdict {}

        /// Aucune localité distante au même endroit : ce point est **nouveau** pour la plateforme. Sa
        /// suite naturelle est la publication (#3724), pas la fusion.
        record AucunCandidat() implements Verdict {}

        /// Plusieurs localités distantes dans le rayon. On **ne choisit pas** à la place de
        /// l'utilisateur : deux points de protocole ne devraient pas être si proches, et le cas mérite
        /// d'être regardé avant d'être résolu.
        record PlusieursCandidats(List<PointVigieChiro> candidates) implements Verdict {}

        /// Le point porte des nuits **déjà sur la plateforme** : son nom ne peut plus changer.
        ///
        /// Ce n'est pas une précaution, c'est une impossibilité. `RequetesVigieChiro` retire `point`
        /// du corps des mises à jour de participation, avec sa raison : *« la localité identifie la
        /// participation, elle ne se modifie pas depuis l'app »*. Renommer le point local le
        /// désolidariserait de ses propres participations, **sans aucun moyen de le réparer ensuite**.
        record Scelle(int nuitsSurLaPlateforme) implements Verdict {}

        /// Le point n'a pas de coordonnées : il n'y a rien à comparer. Distinct de [AucunCandidat], qui
        /// affirme qu'on a cherché et n'a rien trouvé.
        record SansPosition() implements Verdict {}
    }

    /// Un point local et ce qu'on en dit.
    public record Appariement(PointDEcoute local, Verdict verdict) {}

    /// Confronte les points locaux du site aux localités distantes.
    ///
    /// Ne sont examinés que les points **dont le code est inconnu de la plateforme** : un point dont le
    /// code correspond déjà à une localité est apparié, il n'y a rien à décider. Symétriquement, seules
    /// les localités **dont le nom est inconnu ici** peuvent être candidates.
    public ReponseApi<List<Appariement>> apparier(long idSite) {
        Optional<String> objectid = liens.objectidPour(LienVigieChiro.ENTITE_SITE, String.valueOf(idSite));
        if (objectid.isEmpty()) {
            return ReponseApi.succes(List.of());
        }
        return client.localitesDuSite(objectid.get()).transformer(localites -> confronter(idSite, localites));
    }

    private List<Appariement> confronter(long idSite, LocalitesDuSite localites) {
        List<PointDEcoute> locaux = serviceSites.listerPoints(idSite);
        Set<String> codesLocaux = new HashSet<>();
        for (PointDEcoute local : locaux) {
            codesLocaux.add(local.code());
        }
        List<PointVigieChiro> candidatesPossibles = localites.positions().stream()
                .filter(distante -> !codesLocaux.contains(distante.code()))
                .toList();

        List<Appariement> appariements = new ArrayList<>();
        for (PointDEcoute local : locaux) {
            if (localites.contient(local.code())) {
                continue; // déjà apparié par le code : rien à décider
            }
            appariements.add(new Appariement(local, verdictPour(local, candidatesPossibles)));
        }
        return List.copyOf(appariements);
    }

    private Verdict verdictPour(PointDEcoute local, List<PointVigieChiro> candidatesPossibles) {
        // L'ordre compte : un point scellé le reste, qu'il ait ou non un candidat en face. Chercher
        // d'abord un candidat ferait miroiter une fusion que rien ne pourrait appliquer.
        int surLaPlateforme = nuitsSurLaPlateforme(local);
        if (surLaPlateforme > 0) {
            return new Verdict.Scelle(surLaPlateforme);
        }
        if (local.latitude() == null || local.longitude() == null) {
            return new Verdict.SansPosition();
        }
        List<PointVigieChiro> proches = candidatesPossibles.stream()
                .filter(distante -> DistanceGeo.memeEndroit(
                        local.latitude(), local.longitude(), distante.latitude(), distante.longitude()))
                .toList();
        return switch (proches.size()) {
            case 0 -> new Verdict.AucunCandidat();
            case 1 ->
                new Verdict.Fusionnable(
                        proches.getFirst(),
                        DistanceGeo.metresEntre(
                                local.latitude(),
                                local.longitude(),
                                proches.getFirst().latitude(),
                                proches.getFirst().longitude()));
            default -> new Verdict.PlusieursCandidats(proches);
        };
    }

    /// Nuits de ce point **déjà sur la plateforme**.
    ///
    /// `StatutWorkflow#estSurLaPlateforme` et non `== DEPOSE` : une nuit **récupérée** en vient aussi,
    /// et son nom de localité est tout aussi scellé. Le prédicat existe précisément pour qu'on n'oublie
    /// pas le second cas (#2581).
    private int nuitsSurLaPlateforme(PointDEcoute local) {
        return (int) passages.findByPoint(local.id()).stream()
                .map(Passage::statutWorkflow)
                .filter(Objects::nonNull)
                .filter(StatutWorkflow::estSurLaPlateforme)
                .count();
    }
}

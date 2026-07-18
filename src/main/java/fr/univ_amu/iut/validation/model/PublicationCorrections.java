package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ResultatEcriture;
import fr.univ_amu.iut.commun.model.AcquisitionAncrage;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/// **Publication des corrections observateur vers VigieChiro** (#723, retour 2 de l'EPIC #1154) :
/// pousse vers la plateforme, pour un passage, chaque observation **poussable** : taxon observateur
/// **et** certitude déclarés (#1139), **et** ancrage plateforme présent (`PATCH
/// /donnees/#id/observations/#indice`, contrat #1203). Action **explicite et idempotente** : elle
/// pousse tout ce qui est publiable à chaque appel (re-pousser la même valeur est un no-op côté
/// serveur), donc rejouable après complément ou coupure, sans mémoire d'état.
///
/// Les observations revues non poussables sont **écartées et comptées** (à compléter / sans ancrage /
/// hors référentiel), jamais devinées : la certitude n'est pas dérivée. L'**ancrage**, lui, s'acquiert
/// **ici quand il manque** (#1838) : c'est le moment où il sert réellement. Une nuit importée par CSV
/// (#1565) n'a pas d'ancrage ; plutôt que de renvoyer l'utilisateur vers un réimport, la publication le
/// rapatrie elle-même (`remplacer = true`, qui **préserve les validations**) avant de pousser - même
/// geste qu'à la réactivation (#1571), déclenché par le besoin plutôt que par une heuristique.
/// Le bilan serveur de la participation n'est régénéré que par le **dernier** envoi
/// (`?no_bilan=true` sur les autres) ; si ce dernier envoi échoue, le bilan serveur restera périmé
/// jusqu'à la prochaine publication ou au prochain traitement.
///
/// Orchestration réseau **sans IHM** (règle ArchUnit `model_sans_javafx`), **bloquante** : à appeler
/// hors du fil JavaFX. Injecté de façon **optionnelle** (module `PublicationCorrectionsModule`,
/// absent des injecteurs sans `connexion`), comme [ImportObservations].
public class PublicationCorrections {

    private final ClientVigieChiro client;
    private final LienVigieChiroDao liens;
    private final ObservationDao observations;

    /// Import des observations, **optionnel** : il porte l'acquisition de l'ancrage manquant (#1838).
    /// Absent, la publication se comporte comme avant (les non ancrées restent écartées et comptées).
    private final Optional<ImportObservations> importateur;

    public PublicationCorrections(
            ClientVigieChiro client,
            LienVigieChiroDao liens,
            ObservationDao observations,
            Optional<ImportObservations> importateur) {
        this.client = Objects.requireNonNull(client, "client");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.observations = Objects.requireNonNull(observations, "observations");
        this.importateur = Objects.requireNonNull(importateur, "importateur");
    }

    /// Variante **suivie et annulable** (#1838) : acquiert d'abord l'**ancrage manquant** (rapatriement
    /// des `donnees`, page par page, annulable), puis publie. C'est le seul moment où la publication
    /// touche au réseau avant d'envoyer : une nuit déjà ancrée n'en paie pas le coût.
    ///
    /// @param progres avancement de la phase d'ancrage (aucun si l'ancrage est déjà là)
    /// @param jeton annulation honorée à chaque page rapatriée
    public BilanPublication publier(Long idPassage, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(jeton, "jeton");
        acquerirAncrageSiNecessaire(idPassage, progres, jeton);
        return publier(idPassage);
    }

    /// L'ancrage manquant de ce passage peut-il être **acquis** (#1838) ? Vrai quand l'import est
    /// disponible, la nuit **rattachée** à une participation et l'ancrage effectivement absent.
    ///
    /// Lecture **locale** (les liens sont en base) : l'IHM peut donc l'interroger avant de proposer la
    /// publication, pour annoncer « ces observations seront d'abord ancrées » au lieu de « rien à
    /// publier ». Sans cela l'aperçu écarterait des corrections que la publication saurait pourtant
    /// pousser - et la confirmation contredirait le bilan.
    public boolean ancrageAcquerable(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        return importateur
                .filter(import_ -> import_.estRattache(idPassage))
                .filter(import_ -> import_.ancrageManquant(idPassage))
                .isPresent();
    }

    /// Acquiert l'ancrage manquant avant de publier (#1838), en déléguant au geste partagé
    /// [AcquisitionAncrage] - le même que celui de la réactivation (#1571), qui en a besoin pour une
    /// autre raison au même endroit du domaine (ADR 0019).
    private void acquerirAncrageSiNecessaire(Long idPassage, Consumer<Progression> progres, JetonAnnulation jeton) {
        importateur.ifPresent(import_ -> AcquisitionAncrage.acquerirSiNecessaire(import_, idPassage, progres, jeton));
    }

    /// Publie les corrections du passage, **sans toucher à l'ancrage** (celui qui manque restera compté).
    /// Lève une [RegleMetierException] si **aucune** observation du passage n'est revue (rien à publier :
    /// valider ou corriger d'abord) ; les autres cas non publiables sont comptés dans le bilan, pas levés.
    ///
    /// @param idPassage passage cible
    /// @return le bilan de la publication (poussées, écartées par cause, refus détaillés)
    public BilanPublication publier(Long idPassage) {
        TriPublication tri = trier(idPassage);
        List<String> echecs = pousser(tri.publiables(), liens.tous(LienVigieChiro.ENTITE_TAXON));
        return new BilanPublication(
                tri.publiables().size() - echecs.size(),
                tri.sansCertitude(),
                tri.sansAncrage(),
                tri.horsReferentiel(),
                List.copyOf(echecs));
    }

    /// Trie les observations revues du passage au regard de la publication, **sans aucun réseau** :
    /// l'aperçu de la confirmation IHM (« N corrections vont être publiées… ») et le plan d'envoi de
    /// [#publier]. Lève une [RegleMetierException] si aucune observation n'est revue.
    public TriPublication trier(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        List<Observation> revues = observations.revuesDuPassage(idPassage);
        if (revues.isEmpty()) {
            throw new RegleMetierException("Aucune observation revue sur ce passage : validez ou corrigez"
                    + " d'abord vos observations (et déclarez votre certitude) avant de publier.");
        }
        Map<String, String> objectids = liens.tous(LienVigieChiro.ENTITE_TAXON);
        List<Observation> publiables = new ArrayList<>();
        int sansAncrage = 0;
        int sansCertitude = 0;
        int horsReferentiel = 0;
        for (Observation revue : revues) {
            if (revue.idDonneeVigieChiro() == null || revue.indiceVigieChiro() == null) {
                sansAncrage++;
            } else if (revue.certitudeObservateur() == null) {
                sansCertitude++;
            } else if (!objectids.containsKey(revue.taxonObservateur())) {
                horsReferentiel++;
            } else {
                publiables.add(revue);
            }
        }
        return new TriPublication(List.copyOf(publiables), sansCertitude, sansAncrage, horsReferentiel);
    }

    /// Pousse les corrections une à une ; seul le **dernier** envoi laisse le serveur régénérer son
    /// bilan (levier `no_bilan` du contrat #1203). Un refus n'interrompt pas la rafale : chaque
    /// observation a sa chance, les échecs sont détaillés un par un.
    private List<String> pousser(List<Observation> poussables, Map<String, String> objectids) {
        List<String> echecs = new ArrayList<>();
        for (int i = 0; i < poussables.size(); i++) {
            Observation o = poussables.get(i);
            boolean dernier = i == poussables.size() - 1;
            ResultatEcriture resultat = client.corrigerObservation(
                    o.idDonneeVigieChiro(),
                    o.indiceVigieChiro(),
                    objectids.get(o.taxonObservateur()),
                    o.certitudeObservateur(),
                    dernier);
            if (!resultat.estReussie()) {
                echecs.add(enClair(o, resultat.echec()));
            }
        }
        return echecs;
    }

    /// Message d'échec exploitable : identifie l'observation (id local + ancrage) et suggère le remède
    /// du cas connu (`404` = ancrage périmé après un re-compute serveur).
    private static String enClair(Observation o, String echec) {
        String cause = echec.startsWith("HTTP 404")
                ? echec + " (ancrage périmé : réimportez depuis VigieChiro puis republiez)"
                : echec;
        return "Observation " + o.id() + " (donnée " + o.idDonneeVigieChiro() + ", indice " + o.indiceVigieChiro()
                + ") : " + cause;
    }
}

package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationADeposer;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ResultatParticipation;
import fr.univ_amu.iut.commun.model.InfosPoint;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.ReferentielPoint;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.util.Objects;
import java.util.Optional;

/// **Passerelle de synchronisation passage ↔ participation VigieChiro** (axe 4). Ancre : le lien
/// `LienVigieChiro.ENTITE_PASSAGE` (`passage.id` → `_id` participation). Trois sens :
///  - [#creerPour] : crée la participation sur le site rattaché et pose le lien ;
///  - [#pousserVers] : envoie les métadonnées locales (météo / config / dates) vers la participation (PATCH) ;
///  - [#tirerDepuis] : recopie météo/config de la participation vers le passage local (cas « préparé sur le web »).
///
/// Vit dans `passage` (types `Passage`/`MaterielMicro`), consomme le port socle [ReferentielPoint] pour le
/// code de localité + l'id du site (sans dépendre de `sites`, ArchUnit), le [ClientVigieChiro] et les liens du
/// socle. Le mapping pur est délégué à [CorrespondanceParticipation]. Activée par `OptionalBinder` (absente
/// hors connexion), même patron que `DepotVigieChiro`.
public final class SynchronisationParticipation {

    private static final String NON_LIE = "Ce passage n'est pas encore lié à une participation VigieChiro.";
    private static final String INTROUVABLE =
            "Participation VigieChiro introuvable (non connecté, ou supprimée côté plateforme).";

    private final ClientVigieChiro client;
    private final LienVigieChiroDao liens;
    private final PassageDao passageDao;
    private final MaterielMicroDao materielDao;
    private final ReferentielPoint referentielPoint;

    public SynchronisationParticipation(
            ClientVigieChiro client,
            LienVigieChiroDao liens,
            PassageDao passageDao,
            MaterielMicroDao materielDao,
            ReferentielPoint referentielPoint) {
        this.client = Objects.requireNonNull(client, "client");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.materielDao = Objects.requireNonNull(materielDao, "materielDao");
        this.referentielPoint = Objects.requireNonNull(referentielPoint, "referentielPoint");
    }

    /// L'`_id` de la participation liée à `idPassage`, ou vide s'il n'a pas encore été déposé/rattaché.
    public Optional<String> participationDe(Long idPassage) {
        return liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage));
    }

    /// Crée la participation du passage sur son site rattaché (verrouillé) et **mémorise le lien**
    /// `ENTITE_PASSAGE`. Prérequis (sinon [RegleMetierException]) : passage connu, point résolu, site rattaché.
    public ResultatParticipation creerPour(Long idPassage) {
        Passage passage = chargerPassage(idPassage);
        InfosPoint point = infosPoint(passage);
        String objectidSite = liens.objectidPour(LienVigieChiro.ENTITE_SITE, String.valueOf(point.idSite()))
                .orElseThrow(() -> new RegleMetierException("Site non rattaché à VigieChiro : connectez-vous et"
                        + " synchronisez vos sites avant de créer la participation."));

        ParticipationADeposer participation =
                CorrespondanceParticipation.versParticipation(point.code(), passage, materielDao.pour(idPassage));
        ResultatParticipation resultat = client.creerParticipation(objectidSite, participation);
        resultat.id()
                .ifPresent(id ->
                        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), id)));
        return resultat;
    }

    /// Pousse les métadonnées locales (météo / config / dates) vers la participation liée (PATCH `If-Match`,
    /// etag relu juste avant). [RegleMetierException] si non lié / introuvable.
    public ResultatParticipation pousserVers(Long idPassage) {
        Passage passage = chargerPassage(idPassage);
        String objectid = participationDe(idPassage).orElseThrow(() -> new RegleMetierException(NON_LIE));
        ParticipationDetail distant =
                client.participation(objectid).orElseThrow(() -> new RegleMetierException(INTROUVABLE));
        InfosPoint point = infosPoint(passage);
        ParticipationADeposer maj =
                CorrespondanceParticipation.versParticipation(point.code(), passage, materielDao.pour(idPassage));
        return client.modifierParticipation(objectid, distant.etag(), maj);
    }

    /// Recopie la météo et la configuration micro de la participation vers le passage local (cas « préparé sur
    /// le web »), en **préservant les températures** locales (l'API ne les porte pas). [RegleMetierException]
    /// si non lié / introuvable.
    public void tirerDepuis(Long idPassage) {
        Passage passage = chargerPassage(idPassage);
        String objectid = participationDe(idPassage).orElseThrow(() -> new RegleMetierException(NON_LIE));
        ParticipationDetail distant =
                client.participation(objectid).orElseThrow(() -> new RegleMetierException(INTROUVABLE));

        MeteoReleve fusion =
                CorrespondanceParticipation.fusionnerMeteo(MeteoPassage.lire(passage.donneesMeteo()), distant.meteo());
        passageDao.update(avecMeteo(passage, MeteoPassage.definirReleve(passage.donneesMeteo(), fusion)));
        if (distant.configuration() != null && !distant.configuration().isEmpty()) {
            materielDao.definir(CorrespondanceParticipation.microDepuis(idPassage, distant.configuration()));
        }
    }

    private Passage chargerPassage(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        return passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException("Passage introuvable : " + idPassage));
    }

    private InfosPoint infosPoint(Passage passage) {
        return referentielPoint
                .pour(passage.idPoint())
                .orElseThrow(() ->
                        new RegleMetierException("Point d'écoute introuvable pour le passage " + passage.id() + "."));
    }

    /// Recopie du record `Passage` avec une nouvelle météo (le record n'a pas de `with…`).
    private static Passage avecMeteo(Passage p, String donneesMeteo) {
        return new Passage(
                p.id(),
                p.numeroPassage(),
                p.annee(),
                p.dateEnregistrement(),
                p.heureDebut(),
                p.heureFin(),
                p.parametresAcquisition(),
                p.statutWorkflow(),
                p.verdictVerification(),
                p.commentaire(),
                donneesMeteo,
                p.deposeLe(),
                p.idPoint(),
                p.idEnregistreur());
    }
}

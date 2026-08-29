package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationADeposer;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.ResultatEcriture;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.FuseauDuPoint;
import fr.univ_amu.iut.commun.model.InfosPoint;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.ReferentielPoint;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.ReleveParticipation;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
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

    private static final String NON_LIE = "Ce passage n'est pas encore lié à une participation Vigie-Chiro.";
    private static final String INTROUVABLE =
            "Participation Vigie-Chiro introuvable (non connecté, ou supprimée côté plateforme).";

    private final ClientVigieChiro client;
    private final ReleveDeParticipation releve;
    private final LienVigieChiroDao liens;
    private final PassageDao passageDao;
    private final MaterielMicroDao materielDao;

    /// Pour garantir la ligne `recorder` avant d'y accrocher le passage : `recorder_id` est une clé
    /// étrangère **non nulle** (#1828).
    private final EnregistreurDao enregistreurDao;

    private final ReferentielPoint referentielPoint;

    /// Ce que les enregistrements de la nuit prouvent de ses horaires (#1878).
    private final FenetreObserveeNuit fenetreObservee;

    /// Le fuseau des heures de la nuit, dérivé de la commune du point (#3442). Sert aux DEUX
    /// moitiés : ce qu'on envoie et ce qu'on relit. Les séparer déplacerait la nuit à chaque
    /// aller-retour, cliquet de #1860.
    private final FuseauDuPoint fuseaux;

    public SynchronisationParticipation(
            ClientVigieChiro client,
            LienVigieChiroDao liens,
            PassageDao passageDao,
            MaterielMicroDao materielDao,
            EnregistreurDao enregistreurDao,
            ReferentielPoint referentielPoint,
            FenetreObserveeNuit fenetreObservee,
            FuseauDuPoint fuseaux,
            ReleveDeParticipation releve) {
        this.releve = Objects.requireNonNull(releve, "releve");
        this.client = Objects.requireNonNull(client, "client");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.materielDao = Objects.requireNonNull(materielDao, "materielDao");
        this.enregistreurDao = Objects.requireNonNull(enregistreurDao, "enregistreurDao");
        this.referentielPoint = Objects.requireNonNull(referentielPoint, "referentielPoint");
        this.fenetreObservee = Objects.requireNonNull(fenetreObservee, "fenetreObservee");
        this.fuseaux = Objects.requireNonNull(fuseaux, "fuseaux");
    }

    /// Le refus « site non rattaché », qui nomme le geste **qui marche** pour ce carré-là (#3854).
    ///
    /// Il conseillait « connectez-vous et synchronisez vos sites ». Or la synchronisation dérive les sites
    /// de `GET /moi/participations` : elle n'atteint que les carrés où une nuit est **déjà** déposée, donc
    /// le conseil était inapplicable à qui essaie d'en déposer une première. Mesuré par la sonde #3669 :
    /// `/moi/sites` rend 0 pour un compte qui dépose depuis des mois, son carré appartenant à un tiers.
    /// C'est le jumeau du défaut que #3806 a corrigé côté fenêtre de déclaration.
    ///
    /// Le conseil coûte une requête, **sur le chemin d'échec uniquement**. **Injoignable ne tranche pas** :
    /// ni « récupérez-le », ni « il n'existe pas », le refus dit qu'il n'a pas pu vérifier (ADR 3458).
    private String conseilSiteNonRattache(String numeroCarre) {
        if (numeroCarre == null) {
            return ConseilSiteNonRattache.sansNumeroDeCarre();
        }
        return switch (client.chercherCarre(numeroCarre)) {
            case ReponseApi.Succes<List<SiteVigieChiro>>(List<SiteVigieChiro> trouves) ->
                ConseilSiteNonRattache.selonCeQuiExiste(numeroCarre, trouves);
            case ReponseApi.NonConnecte<List<SiteVigieChiro>> nonConnecte ->
                ConseilSiteNonRattache.sansAvoirPuVerifier("vous n'êtes pas connecté") + " Reconnectez-vous,"
                        + " puis réessayez.";
            case ReponseApi.Injoignable<List<SiteVigieChiro>>(String cause) ->
                ConseilSiteNonRattache.sansAvoirPuVerifier("Vigie-Chiro injoignable : " + cause)
                        + " Réessayez plus tard.";
            case ReponseApi.Refuse<List<SiteVigieChiro>>(int statut, String corps) ->
                ConseilSiteNonRattache.sansAvoirPuVerifier("Vigie-Chiro a répondu HTTP " + statut)
                        + " Réessayez plus tard.";
        };
    }

    /// L'`_id` de la participation liée à `idPassage`, ou vide s'il n'a pas encore été déposé/rattaché.
    public Optional<String> participationDe(Long idPassage) {
        return liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage));
    }

    /// Crée la participation du passage sur son site rattaché (verrouillé) et **mémorise le lien**
    /// `ENTITE_PASSAGE`. Prérequis (sinon [RegleMetierException]) : passage connu, point résolu, site rattaché.
    public ResultatEcriture creerPour(Long idPassage) {
        Passage passage = chargerPassage(idPassage);
        InfosPoint point = infosPoint(passage);
        String objectidSite = liens.objectidPour(LienVigieChiro.ENTITE_SITE, String.valueOf(point.idSite()))
                .orElseThrow(() -> new RegleMetierException(conseilSiteNonRattache(point.numeroCarre())));

        ParticipationADeposer participation = CorrespondanceParticipation.versParticipation(
                point.code(), passage, materielDao.pour(idPassage), fuseaux.pour(passage.idPoint()));
        ResultatEcriture resultat = client.creerParticipation(objectidSite, participation);
        resultat.id()
                .ifPresent(id ->
                        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), id)));
        return resultat;
    }

    /// Pousse les métadonnées locales (météo / config / dates) vers la participation liée
    /// (`PATCH /participations/#id`). [RegleMetierException] si non lié / introuvable.
    ///
    /// **Aucun `If-Match` n'est envoyé** : la plateforme l'ignore sur cette route (#4523). La
    /// concurrence est tenue ici, sur le patron de [fr.univ_amu.iut.sites.model.PublicationPoint] :
    /// on relit juste avant d'écrire, et on renonce si l'`_etag` a bougé (#4552).
    ///
    /// **La fenêtre ne se ferme pas** : sur une collision exacte, entre la relecture et l'envoi, on
    /// écrase sans le savoir. Seul le serveur pourrait l'empêcher, et il ne le fait pas. Risque
    /// assumé, pas oubli.
    public EnvoiParticipation pousserVers(Long idPassage) {
        Passage declare = chargerPassage(idPassage);
        String objectid = participationDe(idPassage).orElseThrow(() -> new RegleMetierException(NON_LIE));
        ParticipationDetail distant =
                client.participation(objectid).enOptionnel().orElseThrow(() -> new RegleMetierException(INTROUVABLE));
        // Réaligné seulement une fois l'envoi acquis (passage lié, participation lisible) : le réalignement
        // écrit en base, il n'a pas à laisser de trace sur un chemin qui va échouer.
        Passage passage = realignerSurLesPreuves(declare);
        InfosPoint point = infosPoint(passage);
        // La relecture est le coeur de la garde (#4552) : entre la lecture du haut et maintenant, un autre
        // poste a pu écrire. Elle passe avant le corps, qui doit partir de l'état le plus frais (#4603).
        ParticipationDetail juste =
                client.participation(objectid).enOptionnel().orElseThrow(() -> new RegleMetierException(INTROUVABLE));
        // #4707 : la reference est la BASE, ce que la plateforme portait a notre derniere lecture, et non
        // la lecture du haut prise il y a quelques millisecondes. Sans ce deplacement, un collegue qui
        // saisit la meteo dix minutes plus tot n'est pas vu : nos deux lectures concordent sur SA valeur,
        // et notre corps, venu du passage local, l'ecrase sans rien dire.
        //
        // Faute de base - une nuit anterieure a la migration V43 -, on retombe sur la garde d'avant, qui
        // ne voit qu'une course etroite. C'est peu, et c'est defini.
        boolean conflit = releve.base(idPassage)
                .map(base -> aChangeDepuisLaBase(base, juste))
                .orElseGet(() -> aChangeSurCeQueNousEcrivons(distant, juste));
        if (conflit) {
            return new EnvoiParticipation.ModifieEntreTemps(realignementEntre(declare, passage));
        }
        // #1844 : la configuration distante est passée au mapping pour être **préservée**. Le PATCH
        // remplace le dictionnaire entier ; sans elle, chaque envoi effacerait les champs saisis sur le web
        // que l'app ne modélise pas (micro0_numero_serie, micro1_*, canal_*). Elle vient de la relecture :
        // on envoie l'état qu'on vient de valider, pas un état plus ancien. Les deux coïncident tant que
        // la garde compare la configuration entière ; ils cesseraient de coïncider si elle se resserrait
        // sur les seules clés que nous posons (#4603).
        ParticipationADeposer maj = CorrespondanceParticipation.versParticipation(
                point.code(),
                passage,
                materielDao.pour(idPassage),
                juste.configuration(),
                fuseaux.pour(passage.idPoint()));
        ResultatEcriture ecriture = client.modifierParticipation(objectid, juste.etag(), maj);
        // #4706 : la base ne se note QUE sur une ecriture acceptee, et sur la relecture, qui est l'etat
        // contre lequel on a valide. Un refus decrirait un etat distant qui n'existe pas ; et noter
        // apres un renoncement rendrait la base egale a LEUR valeur, si bien que la tentative suivante
        // conclurait qu'ils n'ont rien change et ecraserait leur travail en silence.
        if (ecriture.estReussie()) {
            releve.noter(idPassage, objectid, juste);
        }
        // #1885 : le réalignement a modifié les données de l'utilisateur, il ne peut pas rester tacite.
        return EnvoiParticipation.ecrit(ecriture, realignementEntre(declare, passage));
    }

    /// Vrai si un champ que le `PATCH` écrit a changé entre les deux lectures (#4603).
    ///
    /// L'`_etag` ne convient pas : il bouge sur **tout** le document, et l'ouvrier d'analyse du socle
    /// le fait bouger seul, sans aucun humain, en avançant l'état du traitement. Comparer ce que nous
    /// émettons laisse passer ce qui n'écrase rien, et refuse encore ce qui écraserait.
    /// Vrai si la plateforme a changé un champ que le `PATCH` écrit, **depuis notre dernière lecture**
    /// (#4707).
    ///
    /// C'est la question que deux lectures prises dans le même appel ne peuvent pas poser : elles ne
    /// couvrent que leur propre intervalle, quelques millisecondes, alors que le besoin porte sur le
    /// temps qui s'est écoulé depuis que nous avons regardé.
    private static boolean aChangeDepuisLaBase(ReleveParticipation base, ParticipationDetail juste) {
        return !Objects.equals(base.dateDebut(), juste.dateDebut())
                || !Objects.equals(base.dateFin(), juste.dateFin())
                || !Objects.equals(base.meteo(), juste.meteo())
                || !Objects.equals(base.configuration(), juste.configuration());
    }

    private static boolean aChangeSurCeQueNousEcrivons(ParticipationDetail avant, ParticipationDetail juste) {
        return !Objects.equals(avant.dateDebut(), juste.dateDebut())
                || !Objects.equals(avant.dateFin(), juste.dateFin())
                || !Objects.equals(avant.meteo(), juste.meteo())
                || !Objects.equals(avant.configuration(), juste.configuration());
    }

    /// Le réalignement survenu entre le passage **déclaré** et le passage **envoyé**, ou vide si les heures
    /// n'ont pas bougé (cas nominal : les preuves confirmaient la déclaration, ou il n'y en avait pas).
    private static Optional<EnvoiParticipation.Realignement> realignementEntre(Passage declare, Passage envoye) {
        if (declare == envoye) {
            return Optional.empty();
        }
        return Optional.of(new EnvoiParticipation.Realignement(
                declare.heureDebut(), declare.heureFin(), envoye.heureDebut(), envoye.heureFin()));
    }

    /// Réaligne les heures du passage sur ce que ses enregistrements **prouvent**, avant de les envoyer
    /// (#1878).
    ///
    /// Les colonnes `start_time` / `end_time` portent une valeur **déclarée**, qui peut avoir dérivé ;
    /// les fichiers et les séquences, eux, portent la nuit **telle qu'elle a eu lieu**. Envoyer la preuve
    /// plutôt que la déclaration rend la cohérence structurelle : la nuit se réaligne d'elle-même à chaque
    /// aller-retour, au lieu de dépendre du fait qu'aucune conversion n'ait jamais fauté (#1860, où
    /// l'erreur **composait** à chaque cycle).
    ///
    /// La correction est **persistée** : sans cela, l'IHM continuerait d'afficher des heures fausses
    /// pendant que la plateforme afficherait les justes - on aurait déplacé l'incohérence, pas résolue.
    ///
    /// Sans preuve locale (nuit **squelette** rapatriée, sans fichier ni séquence), le passage est rendu
    /// **inchangé** : on ne fabrique rien.
    private Passage realignerSurLesPreuves(Passage passage) {
        Optional<FenetreObserveeNuit.Bornes> observee = fenetreObservee.pour(passage.id());
        if (observee.isEmpty() || !observee.get().contredisent(passage)) {
            return passage;
        }
        FenetreObserveeNuit.Bornes bornes = observee.get();
        Passage realigne = avecHoraires(
                passage, bornes.dateEnregistrement(), bornes.heure(bornes.debut()), bornes.heure(bornes.fin()));
        passageDao.update(realigne);
        return realigne;
    }

    /// Copie du passage avec la date et les heures de nuit remplacées.
    private static Passage avecHoraires(Passage passage, String date, String heureDebut, String heureFin) {
        return new Passage(
                passage.id(),
                passage.numeroPassage(),
                passage.annee(),
                date,
                heureDebut,
                heureFin,
                passage.parametresAcquisition(),
                passage.statutWorkflow(),
                passage.verdictVerification(),
                passage.commentaire(),
                passage.donneesMeteo(),
                passage.deposeLe(),
                passage.idPoint(),
                passage.idEnregistreur(),
                passage.idCampagne());
    }

    /// Recopie la météo et la configuration micro de la participation vers le passage local (cas « préparé sur
    /// le web »), en **préservant les températures** locales (l'API ne les porte pas). [RegleMetierException]
    /// si non lié / introuvable.
    public void tirerDepuis(Long idPassage) {
        Passage passage = chargerPassage(idPassage);
        String objectid = participationDe(idPassage).orElseThrow(() -> new RegleMetierException(NON_LIE));
        ParticipationDetail distant =
                client.participation(objectid).enOptionnel().orElseThrow(() -> new RegleMetierException(INTROUVABLE));

        // #4706 : ce que la plateforme portait devient la BASE. Sans elle, une modification faite ici
        // ne se distingue pas d'une modification faite la-bas, et le conflit ne se constate pas.
        releve.noter(idPassage, objectid, distant);

        MeteoReleve fusion =
                CorrespondanceParticipation.fusionnerMeteo(MeteoPassage.lire(passage.donneesMeteo()), distant.meteo());
        passageDao.update(avecMeteoEtEnregistreur(
                passage,
                MeteoPassage.definirReleve(passage.donneesMeteo(), fusion),
                enregistreurATirer(passage, distant)));
        if (distant.configuration() != null && !distant.configuration().isEmpty()) {
            materielDao.definir(CorrespondanceParticipation.microDepuis(idPassage, distant.configuration()));
        }
    }

    /// Numéro de série à retenir pour ce passage (#1828). Le micro et la météo se lisaient déjà dans la
    /// `configuration` de la participation ; le **numéro de série y était ignoré**, si bien qu'une nuit
    /// rapatriée avant #1814 restait « inconnue » alors que la plateforme le portait.
    ///
    /// On n'adopte le numéro distant que s'il désigne un **appareil réel** : ni absent, ni sentinelle
    /// ([Enregistreur#estInconnu]). Sans cette garde, une participation déposée avec un « INCONNU » (le
    /// défaut que #1828 corrige côté dépôt) **écraserait** un numéro local pourtant juste, lu du journal à
    /// l'import. Le local, lui, prime toujours dès qu'il est réel.
    private String enregistreurATirer(Passage passage, ParticipationDetail distant) {
        if (!Enregistreur.estInconnu(passage.idEnregistreur())) {
            return passage.idEnregistreur();
        }
        String distantSerie = CorrespondanceParticipation.serieDepuis(distant.configuration());
        if (Enregistreur.estInconnu(distantSerie)) {
            return passage.idEnregistreur();
        }
        if (enregistreurDao.findById(distantSerie).isEmpty()) {
            enregistreurDao.insert(new Enregistreur(distantSerie, null, null));
        }
        return distantSerie;
    }

    /// Écarts entre le passage local et la **participation liée** côté VigieChiro (« la bonne nuit au
    /// bon endroit », pré-vol du dépôt #1046) : compare le **point** (code localité) et la **nuit**
    /// (date UTC de `date_debut` : le mappeur pousse la date du passage telle quelle en UTC). Liste de
    /// messages lisibles, vide si tout concorde **ou** si aucune participation n'est liée (rien à
    /// vérifier : une création est correcte par construction). Ne compare pas météo/config : la
    /// synchronisation de la modale les gère.
    public List<String> ecartsAvecDistant(Long idPassage) {
        Optional<String> objectid = participationDe(idPassage);
        if (objectid.isEmpty()) {
            return List.of();
        }
        Passage passage = chargerPassage(idPassage);
        InfosPoint point = infosPoint(passage);
        Optional<ParticipationDetail> distant =
                client.participation(objectid.get()).enOptionnel();
        if (distant.isEmpty()) {
            return List.of("participation liée injoignable (" + objectid.get()
                    + ") : hors connexion, ou participation disparue côté Vigie-Chiro");
        }
        List<String> ecarts = new ArrayList<>();
        if (!point.code().equals(distant.get().point())) {
            ecarts.add("point d'écoute « " + point.code() + " » en local, « "
                    + distant.get().point() + " » sur la participation");
        }
        Optional<LocalDate> nuitDistante = nuitDistante(distant.get());
        if (nuitDistante.isEmpty()) {
            ecarts.add("date de début absente ou illisible sur la participation");
        } else if (passage.dateEnregistrement() != null
                && !nuitDistante.get().toString().equals(passage.dateEnregistrement())) {
            ecarts.add("nuit du " + passage.dateEnregistrement() + " en local, du " + nuitDistante.get()
                    + " sur la participation");
        }
        return List.copyOf(ecarts);
    }

    /// Date UTC du `date_debut` distant (ISO `+00:00`), vide si absent ou illisible.
    private static Optional<LocalDate> nuitDistante(ParticipationDetail distant) {
        if (distant.dateDebut() == null || distant.dateDebut().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(distant.dateDebut()).toLocalDate());
        } catch (DateTimeParseException illisible) {
            return Optional.empty();
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

    /// Recopie du record `Passage` avec une nouvelle météo et un nouvel enregistreur (le record n'a pas de
    /// `with…`).
    private static Passage avecMeteoEtEnregistreur(Passage p, String donneesMeteo, String idEnregistreur) {
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
                idEnregistreur,
                p.idCampagne());
    }
}

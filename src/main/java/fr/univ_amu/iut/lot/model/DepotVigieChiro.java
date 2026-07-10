package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FichierSigne;
import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationADeposer;
import fr.univ_amu.iut.commun.api.ResultatParticipation;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.passage.model.CouvertureNuageuse;
import fr.univ_amu.iut.passage.model.MaterielMicro;
import fr.univ_amu.iut.passage.model.MeteoPassage;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// **Dépôt d'une nuit** (un passage) sur l'API VigieChiro (#142) : crée la participation à partir des
/// métadonnées locales (localité, fenêtre nuit, météo #702, configuration micro #697), puis téléverse les
/// `fichiers` fournis (déclaration → `PUT` S3 → finalisation, cf. [ClientVigieChiro]).
///
/// Service d'**orchestration** multi-feature (patron de `ServiceLot`) : sens autorisé `lot → passage` /
/// `lot → sites` / `lot → commun`, graphe acyclique. Il **oriente** le dépôt ; la politique du choix des
/// fichiers (originaux bruts vs séquences, archives) est décidée par l'appelant qui passe la liste des
/// [Path] : le service reste testable et indépendant de cette décision.
///
/// Prérequis (sinon [RegleMetierException], rien n'est déposé) : le passage existe, son point est connu,
/// et **son site est rattaché à VigieChiro** (lien `vigiechiro_link`, établi à la connexion/synchro).
public final class DepotVigieChiro {

    private final PassageDao passageDao;
    private final PointDao pointDao;
    private final MaterielMicroDao materielDao;
    private final LienVigieChiroDao liens;
    private final ClientVigieChiro client;

    public DepotVigieChiro(
            PassageDao passageDao,
            PointDao pointDao,
            MaterielMicroDao materielDao,
            LienVigieChiroDao liens,
            ClientVigieChiro client) {
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.materielDao = Objects.requireNonNull(materielDao, "materielDao");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.client = Objects.requireNonNull(client, "client");
    }

    /// Dépose la nuit `idPassage` sur VigieChiro : crée la participation sur le site rattaché puis
    /// téléverse chaque fichier de `fichiers`. Renvoie un [BilanDepot] (participation créée + fichiers
    /// déposés / en échec) ; un échec de téléversement isolé n'interrompt pas les suivants (dépôt
    /// partiel relançable), mais l'échec de **création de la participation** lève.
    public BilanDepot deposer(Long idPassage, List<Path> fichiers) {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(fichiers, "fichiers");

        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException("Passage introuvable : " + idPassage));
        PointDEcoute point = pointDao.findById(passage.idPoint())
                .orElseThrow(() -> new RegleMetierException("Point d'écoute introuvable pour le passage " + idPassage));
        String objectidSite = liens.objectidPour(LienVigieChiro.ENTITE_SITE, String.valueOf(point.idSite()))
                .orElseThrow(() -> new RegleMetierException("Site non rattaché à VigieChiro : connectez-vous à"
                        + " VigieChiro et synchronisez vos sites avant de déposer."));

        ParticipationADeposer participation = construireParticipation(passage, point);
        ResultatParticipation creation = client.creerParticipation(objectidSite, participation);
        String participationId = creation.id()
                .orElseThrow(() -> new RegleMetierException(
                        "Création de la participation refusée par VigieChiro : " + creation.echec()));

        // Mémorise le lien passage → participation (axe 4.2) dès la participation créée, avant l'upload : même
        // un dépôt partiel doit permettre de réimporter les résultats Tadarida de cette participation.
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), participationId));

        List<String> echecs = new ArrayList<>();
        int deposees = 0;
        for (Path fichier : fichiers) {
            if (televerser(fichier)) {
                deposees++;
            } else {
                echecs.add(fichier.getFileName().toString());
            }
        }
        return new BilanDepot(participationId, deposees, echecs);
    }

    /// Téléverse un fichier en trois temps (déclaration → `PUT` S3 → finalisation). `false` si l'une des
    /// étapes échoue ou si le fichier est illisible (l'appelant le compte en échec, sans interrompre).
    private boolean televerser(Path fichier) {
        try {
            String titre = fichier.getFileName().toString();
            Optional<FichierSigne> signe = client.creerFichier(titre);
            if (signe.isEmpty()) {
                return false;
            }
            byte[] octets = Files.readAllBytes(fichier);
            return client.televerserVersS3(signe.get().urlSignee(), octets, mime(titre))
                    && client.finaliserFichier(signe.get().id());
        } catch (IOException illisible) {
            return false;
        }
    }

    private ParticipationADeposer construireParticipation(Passage passage, PointDEcoute point) {
        return new ParticipationADeposer(
                point.code(), debutVc(passage), finVc(passage), meteo(passage), configuration(passage), null);
    }

    /// Bloc météo VigieChiro depuis les données du passage (#702), ou `null` si aucune donnée exploitable.
    /// `vent` reprend le `name()` de l'énum (`NUL|FAIBLE|MOYEN|FORT`, déjà les codes attendus) ; la
    /// couverture est traduite vers les tranches de l'API.
    private static MeteoDepot meteo(Passage passage) {
        if (passage.donneesMeteo() == null || passage.donneesMeteo().isBlank()) {
            return null;
        }
        MeteoReleve releve = MeteoPassage.lire(passage.donneesMeteo());
        String vent = releve.vent() == null ? null : releve.vent().name();
        String couverture = codeCouverture(releve.couvertureNuageuse());
        return vent == null && couverture == null ? null : new MeteoDepot(vent, couverture);
    }

    /// Tranche de couverture nuageuse au format API (`0-25|25-50|50-75|75-100`), ou `null`.
    private static String codeCouverture(CouvertureNuageuse couverture) {
        if (couverture == null) {
            return null;
        }
        return switch (couverture) {
            case DE_0_A_25 -> "0-25";
            case DE_25_A_50 -> "25-50";
            case DE_50_A_75 -> "50-75";
            case DE_75_A_100 -> "75-100";
        };
    }

    /// Configuration matérielle VigieChiro (`micro0_*` #697 + n° de série de l'enregistreur), ou `null` si
    /// rien n'est renseigné. Dictionnaire libre clé → valeur, sérialisé tel quel. `MaterielMicroDao.pour`
    /// renvoie un matériel **vide** (et non `null`) quand le passage n'a pas de fiche micro.
    private Map<String, String> configuration(Passage passage) {
        MaterielMicro micro = materielDao.pour(passage.id());
        Map<String, String> config = new LinkedHashMap<>();
        if (passage.idEnregistreur() != null) {
            config.put("detecteur_enregistreur_numserie", passage.idEnregistreur());
        }
        if (micro.typeMicro() != null) {
            config.put("micro0_type", micro.typeMicro());
        }
        if (micro.positionMicro() != null) {
            config.put("micro0_position", micro.positionMicro().name());
        }
        if (micro.hauteurMetres() != null) {
            config.put("micro0_hauteur", hauteur(micro.hauteurMetres()));
        }
        return config.isEmpty() ? null : config;
    }

    /// Début de la nuit au **format datetime attendu par VigieChiro** (RFC 1123 en UTC), ou `null` si date
    /// ou heure de début manquante.
    private static String debutVc(Passage passage) {
        if (passage.dateEnregistrement() == null || passage.heureDebut() == null) {
            return null;
        }
        return rfc1123Utc(LocalDate.parse(passage.dateEnregistrement()), LocalTime.parse(passage.heureDebut()));
    }

    /// Fin de la nuit (RFC 1123 en UTC). La nuit **franchit minuit** quand l'heure de fin ne suit pas l'heure
    /// de début : la date de fin est alors le lendemain. `null` si date ou heure de fin manquante.
    private static String finVc(Passage passage) {
        if (passage.dateEnregistrement() == null || passage.heureFin() == null) {
            return null;
        }
        LocalDate jour = LocalDate.parse(passage.dateEnregistrement());
        LocalTime fin = LocalTime.parse(passage.heureFin());
        if (passage.heureDebut() != null && !fin.isAfter(LocalTime.parse(passage.heureDebut()))) {
            jour = jour.plusDays(1);
        }
        return rfc1123Utc(jour, fin);
    }

    /// Formate un instant **local** (heures du passage, fuseau système de l'observateur) au format datetime
    /// de l'API Eve : **RFC 1123 en UTC** (ex. `Fri, 04 Jul 2026 19:00:00 GMT`). Eve **refuse l'ISO 8601** en
    /// entrée (`422 must be of datetime type`, vérifié en réel) ; et le portail stocke en UTC (`19:00 UTC`
    /// pour un départ `21:00` local), d'où la conversion.
    private static String rfc1123Utc(LocalDate jour, LocalTime heure) {
        return LocalDateTime.of(jour, heure)
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    /// Hauteur du micro en texte, sans décimale superflue (`4.0` → `4`).
    private static String hauteur(Double metres) {
        return metres == Math.rint(metres) ? String.valueOf(metres.intValue()) : String.valueOf(metres);
    }

    /// Type de média déduit de l'extension du fichier, pour le `Content-Type` du `PUT` S3 (il doit
    /// correspondre à la signature calculée côté serveur). `.wav` → `audio/x-wav`, sinon binaire.
    private static String mime(String nom) {
        return nom.toLowerCase().endsWith(".wav") ? "audio/x-wav" : "application/octet-stream";
    }
}

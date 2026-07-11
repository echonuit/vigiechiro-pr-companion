package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FichierSigne;
import fr.univ_amu.iut.commun.api.ResultatParticipation;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/// **Dépôt d'une nuit** (un passage) sur l'API VigieChiro (#142), **reprenable par unité** (#982) :
/// s'assure d'abord que la **participation existe** (réutilisée via le lien `ENTITE_PASSAGE`, créée en
/// repli par [SynchronisationParticipation]), pose le **plan de dépôt** en base (`depot_unite`, #981,
/// idempotent : les unités déjà déposées d'une tentative précédente sont conservées), puis téléverse
/// **uniquement les unités restantes**, en persistant chaque succès/échec **au fil de l'eau**.
///
/// Statuts honnêtes (#980) : le passage bascule « Dépôt en cours » dès qu'un téléversement est entamé
/// (reprise comprise), et « Déposé » **seulement** quand toutes les unités sont `depose` — jamais sur
/// un dépôt partiel. Une interruption (annulation coopérative, coupure, fermeture) laisse « Dépôt en
/// cours » : la prochaine tentative reprend là où elle s'était arrêtée.
///
/// Le téléversement est **en flux** (`PUT` S3 streamé depuis le disque) : une archive ZIP de dépôt
/// peut peser ~700 Mo, on ne la charge plus en mémoire. La politique du choix des fichiers (séquences
/// vs archives) reste décidée par l'appelant qui passe la liste des [Path].
public final class DepotVigieChiro {

    private final SynchronisationParticipation participations;
    private final ClientVigieChiro client;
    private final DepotUniteDao depotUnites;
    private final PassageDao passageDao;
    private final MoteurWorkflowPassage moteurWorkflow;
    private final Horloge horloge;

    public DepotVigieChiro(
            SynchronisationParticipation participations,
            ClientVigieChiro client,
            DepotUniteDao depotUnites,
            PassageDao passageDao,
            MoteurWorkflowPassage moteurWorkflow,
            Horloge horloge) {
        this.participations = Objects.requireNonNull(participations, "participations");
        this.client = Objects.requireNonNull(client, "client");
        this.depotUnites = Objects.requireNonNull(depotUnites, "depotUnites");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.moteurWorkflow = Objects.requireNonNull(moteurWorkflow, "moteurWorkflow");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Variante sans annulation ni suivi de [#deposer(Long, List, BooleanSupplier, SuiviDepot)].
    public BilanDepot deposer(Long idPassage, List<Path> fichiers) {
        return deposer(idPassage, fichiers, () -> false, SuiviDepot.inerte());
    }

    /// Dépose la nuit `idPassage` : réutilise ou crée sa participation, synchronise le plan de dépôt,
    /// bascule « Dépôt en cours », puis (re)téléverse les unités restantes une à une — `annule` est
    /// consulté **entre deux unités** (annulation coopérative), `suivi` est notifié au fil de l'eau
    /// (hors fil JavaFX). À la fin, « Déposé » seulement si toutes les unités sont en ligne.
    ///
    /// @return le bilan (participation + fichiers déposés **cette fois-ci** / en échec)
    /// @throws RegleMetierException si la création de la participation est refusée
    public BilanDepot deposer(Long idPassage, List<Path> fichiers, BooleanSupplier annule, SuiviDepot suivi) {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(fichiers, "fichiers");
        Objects.requireNonNull(annule, "annule");
        Objects.requireNonNull(suivi, "suivi");
        chargerPassage(idPassage); // échec rapide : passage inexistant → refus métier avant toute écriture

        String participationId =
                participations.participationDe(idPassage).orElseGet(() -> creerParticipation(idPassage));

        Map<String, Path> fichiersParIdentifiant = parIdentifiant(fichiers);
        depotUnites.synchroniserPlan(idPassage, plan(idPassage, fichiersParIdentifiant));
        suivi.planEtabli(depotUnites.parPassage(idPassage));

        List<DepotUnite> restantes = depotUnites.restantes(idPassage);
        if (!restantes.isEmpty()) {
            basculerVers(idPassage, StatutWorkflow.DEPOT_EN_COURS);
        }

        int deposees = 0;
        List<String> echecs = new ArrayList<>();
        for (DepotUnite unite : restantes) {
            if (annule.getAsBoolean()) {
                break; // annulation coopérative : le passage reste « Dépôt en cours », reprenable
            }
            if (televerserUne(unite, fichiersParIdentifiant.get(unite.identifiantUnite()), suivi)) {
                deposees++;
            } else {
                echecs.add(unite.identifiantUnite());
            }
        }

        if (depotUnites.toutesDeposees(idPassage)) {
            basculerVers(idPassage, StatutWorkflow.DEPOSE);
        }
        return new BilanDepot(participationId, deposees, echecs);
    }

    /// Téléverse une unité en persistant son avancement au fil de l'eau : `en_cours` avant l'envoi,
    /// `depose` (avec l'id distant) ou `echec` (avec la raison) après. `false` en cas d'échec.
    private boolean televerserUne(DepotUnite unite, Path fichier, SuiviDepot suivi) {
        depotUnites.mettreAJour(unite.id(), StatutDepotUnite.EN_COURS, unite.fichierIdDistant(), null, maintenant());
        suivi.uniteDemarree(unite.identifiantUnite());
        Televersement resultat = fichier == null
                ? Televersement.echec("fichier introuvable sur le disque (archives à régénérer ?)")
                : televerser(fichier);
        if (resultat.reussi()) {
            depotUnites.mettreAJour(unite.id(), StatutDepotUnite.DEPOSE, resultat.fichierId(), null, maintenant());
            suivi.uniteDeposee(depotUnites.findById(unite.id()).orElse(unite));
            return true;
        }
        depotUnites.mettreAJour(unite.id(), StatutDepotUnite.ECHEC, null, resultat.raison(), maintenant());
        suivi.uniteEchouee(unite.identifiantUnite(), resultat.raison());
        return false;
    }

    /// Plan de dépôt dérivé des fichiers fournis : une unité « à déposer » par fichier, typée d'après
    /// son extension. La synchronisation (#981) conservera le statut des unités déjà suivies.
    private List<DepotUnite> plan(Long idPassage, Map<String, Path> fichiersParIdentifiant) {
        String maintenant = maintenant();
        List<DepotUnite> plan = new ArrayList<>(fichiersParIdentifiant.size());
        for (String identifiant : fichiersParIdentifiant.keySet()) {
            plan.add(DepotUnite.aDeposer(idPassage, identifiant, typeDe(identifiant), maintenant));
        }
        return plan;
    }

    private static Map<String, Path> parIdentifiant(List<Path> fichiers) {
        Map<String, Path> parIdentifiant = new LinkedHashMap<>();
        for (Path fichier : fichiers) {
            parIdentifiant.put(fichier.getFileName().toString(), fichier);
        }
        return parIdentifiant;
    }

    /// Applique la transition de statut au passage (« Dépôt en cours » à l'entame, « Déposé » quand tout
    /// est en ligne), validée par le moteur de workflow (#980). Le passage à « Déposé » horodate
    /// `deposeLe` ; déjà « Déposé » (dépôt manuel entre-temps) → sans effet.
    private void basculerVers(Long idPassage, StatutWorkflow cible) {
        Passage passage = chargerPassage(idPassage);
        if (passage.statutWorkflow() == cible && cible == StatutWorkflow.DEPOSE) {
            return;
        }
        moteurWorkflow.exigerTransitionAutorisee(passage.statutWorkflow(), cible);
        String deposeLe = cible == StatutWorkflow.DEPOSE ? horloge.maintenant().toString() : passage.deposeLe();
        passageDao.update(new Passage(
                passage.id(),
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.parametresAcquisition(),
                cible,
                passage.verdictVerification(),
                passage.commentaire(),
                passage.donneesMeteo(),
                deposeLe,
                passage.idPoint(),
                passage.idEnregistreur()));
    }

    private Passage chargerPassage(Long idPassage) {
        return passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException("Passage introuvable : " + idPassage));
    }

    /// Crée la participation (repli lazy quand elle n'a pas été créée à l'import) et renvoie son id, ou lève
    /// avec le détail du refus VigieChiro.
    private String creerParticipation(Long idPassage) {
        ResultatParticipation creation = participations.creerPour(idPassage);
        return creation.id()
                .orElseThrow(() -> new RegleMetierException(
                        "Création de la participation refusée par VigieChiro : " + creation.echec()));
    }

    /// Téléverse un fichier en trois temps (déclaration → `PUT` S3 **en flux** → finalisation) et renvoie
    /// l'issue : l'id distant du fichier créé, ou la raison de l'échec de l'étape fautive.
    private Televersement televerser(Path fichier) {
        String titre = fichier.getFileName().toString();
        Optional<FichierSigne> signe = client.creerFichier(titre);
        if (signe.isEmpty()) {
            return Televersement.echec("déclaration du fichier refusée par VigieChiro");
        }
        if (!client.televerserVersS3(signe.get().urlSignee(), fichier, mime(titre))) {
            return Televersement.echec("téléversement S3 refusé (réseau ou fichier illisible)");
        }
        if (!client.finaliserFichier(signe.get().id())) {
            return Televersement.echec("finalisation refusée par VigieChiro");
        }
        return Televersement.reussi(signe.get().id());
    }

    /// Issue d'un téléversement unitaire : l'id distant en cas de succès, la raison sinon.
    private record Televersement(String fichierId, String raison) {
        static Televersement reussi(String fichierId) {
            return new Televersement(fichierId, null);
        }

        static Televersement echec(String raison) {
            return new Televersement(null, raison);
        }

        boolean reussi() {
            return fichierId != null;
        }
    }

    private String maintenant() {
        return horloge.maintenant().toString();
    }

    /// Type d'unité déduit de l'extension (`.zip` → archive, sinon séquence WAV).
    private static TypeDepotUnite typeDe(String nom) {
        return nom.toLowerCase(Locale.ROOT).endsWith(".zip") ? TypeDepotUnite.ZIP : TypeDepotUnite.WAV;
    }

    /// Type de média déduit de l'extension du fichier, pour le `Content-Type` du `PUT` S3 (il doit
    /// correspondre à la signature calculée côté serveur). `.wav` → `audio/x-wav`, `.zip` →
    /// `application/zip`, sinon binaire.
    private static String mime(String nom) {
        String minuscule = nom.toLowerCase(Locale.ROOT);
        if (minuscule.endsWith(".wav")) {
            return "audio/x-wav";
        }
        return minuscule.endsWith(".zip") ? "application/zip" : "application/octet-stream";
    }
}

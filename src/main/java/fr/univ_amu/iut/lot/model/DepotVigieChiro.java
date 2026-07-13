package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FichierSigne;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.ResultatLancement;
import fr.univ_amu.iut.commun.api.ResultatParticipation;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.api.TraitementVigieChiro;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

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

    /// Nombre de téléversements menés **en parallèle** (#984), calqué sur le front web
    /// (`max_concurrent_uploads = 5`) : le réseau est le goulot, pas les écritures `depot_unite`
    /// (minuscules, que le `busy_timeout` SQLite sérialise sans échec). Au-delà, on sature surtout le
    /// serveur sans gagner.
    private static final int NB_UPLOADS_PARALLELES = 5;

    private static final String PARAM_ID_PASSAGE = "idPassage";

    private final SynchronisationParticipation participations;
    private final ClientVigieChiro client;

    /// Traitement serveur (compute + lecture de l'état) : le client transporte, celui-ci décide (#1261).
    private final TraitementVigieChiro traitement;
    private final DepotUniteDao depotUnites;
    private final PassageDao passageDao;
    private final MoteurWorkflowPassage moteurWorkflow;
    private final Horloge horloge;
    private final ReconciliationDepot reconciliation;

    public DepotVigieChiro(
            SynchronisationParticipation participations,
            ClientVigieChiro client,
            TraitementVigieChiro traitement,
            DepotUniteDao depotUnites,
            PassageDao passageDao,
            MoteurWorkflowPassage moteurWorkflow,
            Horloge horloge) {
        this.participations = Objects.requireNonNull(participations, "participations");
        this.client = Objects.requireNonNull(client, "client");
        this.traitement = Objects.requireNonNull(traitement, "traitement");
        this.depotUnites = Objects.requireNonNull(depotUnites, "depotUnites");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.moteurWorkflow = Objects.requireNonNull(moteurWorkflow, "moteurWorkflow");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.reconciliation = new ReconciliationDepot(client, depotUnites, horloge);
    }

    /// Variante sans annulation ni suivi de [#deposer(Long, List, BooleanSupplier, SuiviDepot)].
    public BilanDepot deposer(Long idPassage, List<Path> fichiers) {
        return deposer(idPassage, fichiers, () -> false, SuiviDepot.inerte());
    }

    /// Lance le **traitement serveur** (compute, #984) de la participation liée au passage `idPassage` :
    /// équivalent du bouton « Lancer la participation » du web, à déclencher une fois les fichiers déposés.
    /// Lève une [RegleMetierException] si aucune participation n'est liée au passage (rien à traiter :
    /// déposer d'abord).
    ///
    /// **Première demande seulement.** Si la participation a déjà été calculée, la demande est bloquée
    /// **ici**, sans appeler le serveur : cf. [#lancerTraitement(Long, boolean)].
    public ResultatLancement lancerTraitement(Long idPassage) {
        return lancerTraitement(idPassage, false);
    }

    /// Lance le traitement serveur, en autorisant éventuellement une **relance**.
    ///
    /// ⚠️ **Une relance n'est pas un simple « réessayer ».** À chaque compute, le serveur **supprime toutes
    /// les `donnees` avant de recalculer** (`task_participation.py:726-731`). Sur une nuit déposée en
    /// **archives ZIP** — le mode par défaut depuis #984 — les WAV extraits ne sont pas conservés sur S3
    /// (#1244) : le recalcul ne peut donc pas les relire, et les observations sont **définitivement
    /// perdues**. Tant que la participation n'a **jamais** été calculée, le lancement est sûr ; ensuite, il
    /// détruit. D'où la garde : `forcer` doit être demandé explicitement (option `--forcer`, #1265), en
    /// connaissance de cause.
    ///
    /// La garde est **locale** : on relit l'état du traitement et on refuse de notre propre chef, sans rien
    /// demander au serveur (qui, lui, accepterait — il l'accepte volontiers passé 24 h).
    ///
    /// **Fail-safe depuis #1284** : si l'état ne peut pas être **lu** (injoignable, refus), on ne lance
    /// pas sans `forcer` — avant, un simple délai réseau au moment de la relecture faisait passer la
    /// garde, et un compute parti malgré tout aurait détruit les observations d'une nuit ZIP. Ne pas
    /// pouvoir prouver que le lancement est sûr, c'est ne pas lancer.
    public ResultatLancement lancerTraitement(Long idPassage, boolean forcer) {
        Objects.requireNonNull(idPassage, PARAM_ID_PASSAGE);
        String participationId = participations
                .participationDe(idPassage)
                .orElseThrow(() -> new RegleMetierException(
                        "Aucune participation VigieChiro liée à ce passage : déposez d'abord la nuit."));
        if (!forcer) {
            switch (traitement.etat(participationId)) {
                case ReponseApi.Succes<Traitement>(Traitement dejaCalcule) -> {
                    if (estDejaCalcule(dejaCalcule)) {
                        return ResultatLancement.relanceBloquee(dejaCalcule);
                    }
                }
                case ReponseApi.NonConnecte<Traitement> nonConnecte -> {
                    return ResultatLancement.injoignable();
                }
                case ReponseApi.Injoignable<Traitement> injoignable -> {
                    return ResultatLancement.injoignable();
                }
                case ReponseApi.Refuse<Traitement>(int statut, String corps) -> {
                    return ResultatLancement.refuse(statut, corps);
                }
            }
        }
        return traitement.lancer(participationId);
    }

    /// Un calcul a-t-il **déjà eu lieu** pour cette participation ? Vrai s'il est terminé ou en échec : dans
    /// les deux cas, relancer écraserait le résultat précédent. Un traitement encore [Traitement#enAttente]
    /// n'est pas concerné (rien à écraser, et c'est le serveur qui refusera la demande concurrente).
    private static boolean estDejaCalcule(Traitement traitement) {
        return traitement.resultatsDisponibles() || traitement.enEchec();
    }

    /// `true` si une participation VigieChiro est **liée** au passage (dépôt via l'API effectué), donc
    /// susceptible d'être lancée (compute). Simple lecture du lien local, sans réseau.
    public boolean participationLiee(Long idPassage) {
        Objects.requireNonNull(idPassage, PARAM_ID_PASSAGE);
        return participations.participationDe(idPassage).isPresent();
    }

    /// Dépose la nuit `idPassage` : réutilise ou crée sa participation, synchronise le plan de dépôt,
    /// bascule « Dépôt en cours », puis (re)téléverse les unités restantes **en parallèle** (jusqu'à
    /// [#NB_UPLOADS_PARALLELES] simultanées, comme le front web) — `annule` est consulté **avant chaque
    /// unité** (annulation coopérative : les unités non entamées restent reprenables), `suivi` est
    /// notifié au fil de l'eau (hors fil JavaFX, émissions concurrentes). À la fin, « Déposé » seulement
    /// si toutes les unités sont en ligne.
    ///
    /// @return le bilan (participation + fichiers déposés **cette fois-ci** / en échec)
    /// @throws RegleMetierException si la création de la participation est refusée
    public BilanDepot deposer(Long idPassage, List<Path> fichiers, BooleanSupplier annule, SuiviDepot suivi) {
        Objects.requireNonNull(idPassage, PARAM_ID_PASSAGE);
        Objects.requireNonNull(fichiers, "fichiers");
        Objects.requireNonNull(annule, "annule");
        Objects.requireNonNull(suivi, "suivi");
        chargerPassage(idPassage); // échec rapide : passage inexistant → refus métier avant toute écriture

        String participationId =
                participations.participationDe(idPassage).orElseGet(() -> creerParticipation(idPassage));
        // Pré-vol (#1046) : la participation (liée ou fraîchement créée) doit correspondre au passage —
        // même point, même nuit. Refus explicite sinon : on ne poste jamais « la mauvaise nuit au
        // mauvais endroit » (participations héritées du bug de date, rattachement manuel erroné…).
        verifierCorrespondance(idPassage);

        Map<String, Path> fichiersParIdentifiant = parIdentifiant(fichiers);
        depotUnites.synchroniserPlan(idPassage, plan(idPassage, fichiersParIdentifiant));
        suivi.planEtabli(depotUnites.parPassage(idPassage));
        reconciliation.reconcilier(idPassage, participationId, suivi);

        List<DepotUnite> restantes = depotUnites.restantes(idPassage);
        if (!restantes.isEmpty()) {
            basculerVers(idPassage, StatutWorkflow.DEPOT_EN_COURS);
        }

        AtomicInteger deposees = new AtomicInteger();
        List<String> echecs = Collections.synchronizedList(new ArrayList<>());
        int parallelisme = Math.max(1, Math.min(NB_UPLOADS_PARALLELES, restantes.size()));
        try (ExecutorService executeur = Executors.newFixedThreadPool(parallelisme)) {
            for (DepotUnite unite : restantes) {
                executeur.submit(() -> deposerUneUnite(
                        unite, fichiersParIdentifiant, participationId, annule, suivi, deposees, echecs));
            }
        } // close() attend la fin de toutes les tâches soumises (ExecutorService AutoCloseable, Java 19+).

        if (depotUnites.toutesDeposees(idPassage)) {
            basculerVers(idPassage, StatutWorkflow.DEPOSE);
        }
        return new BilanDepot(participationId, deposees.get(), List.copyOf(echecs));
    }

    /// Traite une unité dans un worker du dépôt parallèle (#984) : respecte l'annulation coopérative
    /// (unité laissée « à déposer », donc reprenable), délègue à [#televerserUne], puis consigne le
    /// résultat dans les accumulateurs **partagés et thread-safe**. Toute exception inattendue (écriture
    /// DB, I/O) est convertie en échec pour ne pas faire tomber le lot entier — le plan reprenable
    /// reprendra l'unité au prochain essai.
    private void deposerUneUnite(
            DepotUnite unite,
            Map<String, Path> fichiersParIdentifiant,
            String participationId,
            BooleanSupplier annule,
            SuiviDepot suivi,
            AtomicInteger deposees,
            List<String> echecs) {
        if (annule.getAsBoolean()) {
            return;
        }
        try {
            if (televerserUne(unite, fichiersParIdentifiant.get(unite.identifiantUnite()), participationId, suivi)) {
                deposees.incrementAndGet();
            } else {
                echecs.add(unite.identifiantUnite());
            }
        } catch (RuntimeException erreur) {
            echecs.add(unite.identifiantUnite());
        }
    }

    /// Téléverse une unité en persistant son avancement au fil de l'eau : `en_cours` avant l'envoi,
    /// `depose` (avec l'id distant) ou `echec` (avec la raison) après. `false` en cas d'échec.
    private boolean televerserUne(DepotUnite unite, Path fichier, String participationId, SuiviDepot suivi) {
        depotUnites.mettreAJour(unite.id(), StatutDepotUnite.EN_COURS, unite.fichierIdDistant(), null, maintenant());
        suivi.uniteDemarree(unite.identifiantUnite());
        Televersement resultat = fichier == null
                ? Televersement.echec("fichier introuvable sur le disque (archives à régénérer ?)")
                : televerser(
                        fichier, participationId, fraction -> suivi.uniteProgresse(unite.identifiantUnite(), fraction));
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

    /// Pré-vol du dépôt (#1046) : refuse si la participation liée ne correspond pas au passage local
    /// (point, nuit) — le détail des écarts est remonté tel quel (IHM et CLI l'affichent).
    private void verifierCorrespondance(Long idPassage) {
        List<String> ecarts = participations.ecartsAvecDistant(idPassage);
        if (!ecarts.isEmpty()) {
            throw new RegleMetierException("Dépôt refusé, la participation liée ne correspond pas au passage : "
                    + String.join(" ; ", ecarts)
                    + ". Vérifiez le rattachement (modale « Modifier le passage », synchronisation) avant de"
                    + " déposer.");
        }
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
    private Televersement televerser(Path fichier, String participationId, DoubleConsumer progression) {
        String titre = fichier.getFileName().toString();
        Optional<FichierSigne> signe = client.creerFichier(titre, participationId);
        if (signe.isEmpty()) {
            return Televersement.echec("déclaration du fichier refusée par VigieChiro");
        }
        if (!client.televerserVersS3(signe.get().urlSignee(), fichier, mime(titre), progression)) {
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

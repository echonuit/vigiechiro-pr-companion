package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FichierSigne;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.ResultatEcriture;
import fr.univ_amu.iut.commun.api.ResultatLancement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.api.TraitementVigieChiro;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.dao.DepotPlanDao;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
/// vs archives) reste décidée par l'appelant, qui passe une [SourceDepot].
///
/// Le moteur ne connaît **que des identifiants** jusqu'au moment d'envoyer (#1993) : le plan est donc
/// posable avant que les archives existent, et la [SourceDepot] résout chaque fichier au dernier
/// moment. C'est ce qui rendra possible de générer au fil de l'eau et de libérer une archive dès
/// qu'elle est en ligne (#1994, #1995).
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
    private final DepotPlanDao depotPlans;
    private final PassageDao passageDao;
    private final MoteurWorkflowPassage moteurWorkflow;
    private final Horloge horloge;
    private final ReconciliationDepot reconciliation;

    public DepotVigieChiro(
            SynchronisationParticipation participations,
            ClientVigieChiro client,
            TraitementVigieChiro traitement,
            DepotUniteDao depotUnites,
            DepotPlanDao depotPlans,
            PassageDao passageDao,
            MoteurWorkflowPassage moteurWorkflow,
            Horloge horloge) {
        this.participations = Objects.requireNonNull(participations, "participations");
        this.client = Objects.requireNonNull(client, "client");
        this.traitement = Objects.requireNonNull(traitement, "traitement");
        this.depotUnites = Objects.requireNonNull(depotUnites, "depotUnites");
        this.depotPlans = Objects.requireNonNull(depotPlans, "depotPlans");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.moteurWorkflow = Objects.requireNonNull(moteurWorkflow, "moteurWorkflow");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.reconciliation = new ReconciliationDepot(client, depotUnites, horloge);
    }

    /// Variante sans annulation ni suivi de [#deposer(Long, SourceDepot, BooleanSupplier, SuiviDepot)],
    /// adossee a des fichiers deja presents sur le disque ([SourceDepot#desFichiers]).
    ///
    /// Il n'existe **pas** de variante `List<Path>` a quatre parametres : elle formerait avec celle-ci
    /// une paire de surcharges que le type d'un argument seul distingue, ce que les matchers `any()`
    /// des tests ne savent pas departager. Les appelants qui veulent annulation et suivi emballent
    /// explicitement leur liste.
    public BilanDepot deposer(Long idPassage, List<Path> fichiers) {
        Objects.requireNonNull(fichiers, "fichiers");
        return deposer(idPassage, SourceDepot.desFichiers(fichiers), () -> false, SuiviDepot.inerte());
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
                        "Aucune participation Vigie-Chiro liée à ce passage : déposez d'abord la nuit."));
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
    public BilanDepot deposer(Long idPassage, SourceDepot source, BooleanSupplier annule, SuiviDepot suivi) {
        Objects.requireNonNull(idPassage, PARAM_ID_PASSAGE);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(annule, "annule");
        Objects.requireNonNull(suivi, "suivi");
        chargerPassage(idPassage); // échec rapide : passage inexistant → refus métier avant toute écriture

        String participationId =
                participations.participationDe(idPassage).orElseGet(() -> creerParticipation(idPassage));
        // Pré-vol (#1046) : la participation (liée ou fraîchement créée) doit correspondre au passage —
        // même point, même nuit. Refus explicite sinon : on ne poste jamais « la mauvaise nuit au
        // mauvais endroit » (participations héritées du bug de date, rattachement manuel erroné…).
        verifierCorrespondance(idPassage);

        exigerLotInchange(idPassage, source);
        depotUnites.synchroniserPlan(idPassage, plan(idPassage, source.identifiants()));
        // Empreinte de la liste source (#1993) : posée avec le plan, relue à la reprise pour établir que
        // les mêmes identifiants désignent bien le même contenu (les archives sont nommées par leur rang
        // dans une partition qui dépend de la liste source). Le refus s'appuiera dessus en #1994.
        depotPlans.enregistrer(new DepotPlan(idPassage, source.empreinte(), maintenant()));
        suivi.planEtabli(depotUnites.parPassage(idPassage));
        reconciliation.reconcilier(idPassage, participationId, suivi);

        List<DepotUnite> restantes = depotUnites.restantes(idPassage);
        if (!restantes.isEmpty()) {
            basculerVers(idPassage, StatutWorkflow.DEPOT_EN_COURS);
        }

        AtomicInteger deposees = new AtomicInteger();
        List<String> echecs = Collections.synchronizedList(new ArrayList<>());
        // La source peut brider ce parallelisme (#1995) : une source qui **produit** ses fichiers borne
        // ainsi ce qui existe sur le disque a un instant donne. Le mode WAV, lui, garde les 5 envois.
        int plafond = Math.min(NB_UPLOADS_PARALLELES, source.parallelismeMax());
        int parallelisme = Math.max(1, Math.min(plafond, restantes.size()));
        try (ExecutorService executeur = Executors.newFixedThreadPool(parallelisme)) {
            for (DepotUnite unite : restantes) {
                executeur.submit(
                        () -> deposerUneUnite(unite, source, participationId, annule, suivi, deposees, echecs));
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
            SourceDepot source,
            String participationId,
            BooleanSupplier annule,
            SuiviDepot suivi,
            AtomicInteger deposees,
            List<String> echecs) {
        if (annule.getAsBoolean()) {
            return;
        }
        try {
            Path fichier = source.resoudre(unite.identifiantUnite()).orElse(null);
            if (televerserUne(unite, fichier, participationId, suivi, source)) {
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
    private boolean televerserUne(
            DepotUnite unite, Path fichier, String participationId, SuiviDepot suivi, SourceDepot source) {
        depotUnites.mettreAJour(unite.id(), StatutDepotUnite.EN_COURS, unite.fichierIdDistant(), null, maintenant());
        suivi.uniteDemarree(unite.identifiantUnite());
        Televersement resultat = fichier == null
                ? Televersement.echec("fichier introuvable sur le disque (archives à régénérer ?)")
                : televerser(
                        fichier, participationId, fraction -> suivi.uniteProgresse(unite.identifiantUnite(), fraction));
        if (resultat.reussi()) {
            depotUnites.mettreAJour(unite.id(), StatutDepotUnite.DEPOSE, resultat.fichierId(), null, maintenant());
            suivi.uniteDeposee(depotUnites.findById(unite.id()).orElse(unite));
            // L'unite est prouvee en ligne : la source peut liberer ce qu'elle avait materialise (#1995).
            // Apres le commit et jamais avant, sinon une coupure laisserait une unite ni en ligne ni sur
            // le disque - la reprise la regenererait, mais on aurait perdu la preuve de l'envoi.
            source.liberer(unite.identifiantUnite());
            return true;
        }
        depotUnites.mettreAJour(unite.id(), StatutDepotUnite.ECHEC, null, resultat.raison(), maintenant());
        suivi.uniteEchouee(unite.identifiantUnite(), resultat.raison());
        return false;
    }

    /// Refuse de reprendre un dépôt dont la **liste source a changé** depuis que le plan a été posé
    /// (#1994), quand quelque chose est déjà en ligne.
    ///
    /// Les archives sont nommées par leur rang dans une partition qui dépend de la liste source. Si
    /// celle-ci change (séquence ajoutée, retirée, re-transformée), l'archive `N` régénérée ne contient
    /// plus la même chose que celle déjà déposée sous ce nom : reprendre écraserait en ligne une archive
    /// par une autre, **sans que rien ne le signale**. On refuse donc explicitement.
    ///
    /// Le refus ne vaut que si des unités sont **déjà déposées** : tant que rien n'est acquis, changer
    /// d'avis est légitime et le plan repart simplement à neuf.
    private void exigerLotInchange(Long idPassage, SourceDepot source) {
        Optional<DepotPlan> planPose = depotPlans.parPassage(idPassage);
        if (planPose.isEmpty() || planPose.get().empreinte().equals(source.empreinte())) {
            return;
        }
        long dejaEnLigne = depotUnites.parPassage(idPassage).stream()
                .filter(unite -> unite.statut() == StatutDepotUnite.DEPOSE)
                .count();
        if (dejaEnLigne == 0) {
            return;
        }
        throw new RegleMetierException("Le lot a changé depuis le début de ce dépôt (" + dejaEnLigne
                + " fichier(s) déjà en ligne) : les archives régénérées ne contiendraient plus la même"
                + " chose que celles déjà déposées. Réinitialisez le dépôt pour repartir d'un plan"
                + " cohérent.");
    }

    /// Plan de dépôt dérivé des **identifiants** de la source : une unité « à déposer » par identifiant,
    /// typée d'après son extension. La synchronisation (#981) conservera le statut des unités déjà
    /// suivies.
    ///
    /// Le plan ne dépend plus que des identifiants, jamais des fichiers eux-mêmes : c'est ce qui permet
    /// de le poser **avant que les archives existent** (#1993).
    private List<DepotUnite> plan(Long idPassage, List<String> identifiants) {
        String maintenant = maintenant();
        List<DepotUnite> plan = new ArrayList<>(identifiants.size());
        for (String identifiant : identifiants) {
            plan.add(DepotUnite.aDeposer(idPassage, identifiant, typeDe(identifiant), maintenant));
        }
        return plan;
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
                    + ". Vérifiez le rattachement (modale « Modifier le passage », « Récupérer depuis"
                    + " Vigie-Chiro ») avant de déposer.");
        }
    }

    /// Crée la participation (repli lazy quand elle n'a pas été créée à l'import) et renvoie son id, ou lève
    /// avec le détail du refus VigieChiro.
    private String creerParticipation(Long idPassage) {
        ResultatEcriture creation = participations.creerPour(idPassage);
        return creation.id()
                .orElseThrow(() -> new RegleMetierException(
                        "Création de la participation refusée par Vigie-Chiro : " + creation.echec()));
    }

    /// Téléverse un fichier en trois temps (déclaration → `PUT` S3 **en flux** → finalisation) et renvoie
    /// l'issue : l'id distant du fichier créé, ou la raison de l'échec de l'étape fautive.
    private Televersement televerser(Path fichier, String participationId, DoubleConsumer progression) {
        String titre = fichier.getFileName().toString();
        // #1284 : chaque étape échoue avec sa cause exacte (non connecté / injoignable / HTTP n),
        // plus jamais un « refusé par VigieChiro » générique quand c'était le réseau.
        ReponseApi<FichierSigne> declaration = client.creerFichier(titre, participationId);
        if (!(declaration instanceof ReponseApi.Succes<FichierSigne>(FichierSigne signe))) {
            return Televersement.echec("déclaration du fichier : " + causeDe(declaration));
        }
        if (!client.televerserVersS3(signe.urlSignee(), fichier, mime(titre), progression)) {
            return Televersement.echec("téléversement S3 refusé (réseau ou fichier illisible)");
        }
        ReponseApi<String> finalisation = client.finaliserFichier(signe.id());
        if (finalisation.echec().isPresent()) {
            return Televersement.echec("finalisation : " + causeDe(finalisation));
        }
        return Televersement.reussi(signe.id());
    }

    /// Cause d'échec en clair d'une étape du téléversement (vocabulaire unique [ReponseApi#echec()]).
    private static String causeDe(ReponseApi<?> reponse) {
        return reponse.echec().orElse("issue inattendue");
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

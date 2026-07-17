package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Micro;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Cœur d'exécution d'un import (copie protégée R9, renommage R6/R7, transformation R10/R11, persistance
/// atomique O7). Extrait de [ServiceImport] (Extract Class) pour garder l'orchestrateur cohésif : le
/// service reste la **façade** (verrou anti-concurrent #54, inspection, sauvegarde #148, requêtes R5/#147),
/// tandis que ce moteur porte le **pipeline par nuit** partagé entre l'import mono-nuit et multi-nuits.
///
/// Deux points d'entrée, un seul pipeline : [#importerUneNuit] traite **une** sous-liste de WAV → **un**
/// passage ; [#importerNuits] boucle dessus (une nuit = un passage), après un pré-contrôle R5 global. Les
/// invariants d'un import (inspection, journal, point, options) transitent via [ContexteImport] pour ne
/// pas allonger les signatures.
final class MoteurImport {

    private final CopieProtegee copie;
    private final PreparationOriginaux preparation;
    private final DecoupageParallele decoupage;
    private final FabriqueEntitesImport fabriqueEntites;
    private final AgregatImportDao agregatDao;
    private final UniteDeTravail uniteDeTravail;
    private final Workspace workspace;

    MoteurImport(
            CopieProtegee copie,
            PreparationOriginaux preparation,
            DecoupageParallele decoupage,
            FabriqueEntitesImport fabriqueEntites,
            AgregatImportDao agregatDao,
            UniteDeTravail uniteDeTravail,
            Workspace workspace) {
        this.copie = Objects.requireNonNull(copie, "copie");
        this.preparation = Objects.requireNonNull(preparation, "preparation");
        this.decoupage = Objects.requireNonNull(decoupage, "decoupage");
        this.fabriqueEntites = Objects.requireNonNull(fabriqueEntites, "fabriqueEntites");
        this.agregatDao = Objects.requireNonNull(agregatDao, "agregatDao");
        this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    /// Importe **plusieurs nuits** d'une même carte en **un passage par nuit** (même point, n° de passage
    /// propre porté par chaque [NuitAImporter], date propre à la nuit). Appelé sous le verrou posé par
    /// [ServiceImport], à partir d'une **unique** inspection (portée par `ctx`).
    ///
    /// **Échec rapide (R5)** : tous les n° sont vérifiés **libres avant** la première copie ; aucun
    /// demi-groupe n'est amorcé si l'un est pris. **Atomicité par nuit** : chaque nuit est une transaction
    /// distincte, donc si la nuit *i* échoue, les nuits *0..i-1* déjà importées demeurent. La progression
    /// (#33) est agrégée (« Nuit i/N · … »), l'annulation (#146) est vérifiée entre deux nuits.
    ResultatImportMultiNuits importerNuits(
            ContexteImport ctx, Prefixe prefixeBase, List<NuitAImporter> nuits, Consumer<Progression> progres) {
        // Pré-contrôle R5 de TOUS les n° avant la première copie : échec rapide, aucun demi-groupe amorcé.
        for (NuitAImporter nuit : nuits) {
            if (agregatDao.passageExistePour(ctx.idPoint(), prefixeBase.annee(), nuit.numeroPassage())) {
                throw new RegleMetierException("Un passage n°"
                        + nuit.numeroPassage()
                        + " existe déjà pour ce point en "
                        + prefixeBase.annee()
                        + " : ajustez la numérotation avant d'importer.");
            }
        }

        int total = nuits.size();
        List<ResultatImport> resultats = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            NuitAImporter nuit = nuits.get(index);
            Prefixe prefixe = new Prefixe(
                    prefixeBase.carre(), prefixeBase.annee(), nuit.numeroPassage(), prefixeBase.codePoint());
            resultats.add(importerUneNuit(
                    ctx,
                    prefixe,
                    nuit.nuit().originaux(),
                    nuit.nuit().dateNuit(),
                    progressionNuit(progres, index, total)));
            ctx.jeton().leverSiAnnule(); // arrêt au plus tôt entre deux nuits (les nuits déjà créées demeurent)
        }
        return new ResultatImportMultiNuits(resultats);
    }

    /// Importe **une** nuit (une sous-liste de WAV → **un** passage), cœur partagé entre l'import mono-nuit
    /// ([ServiceImport]) et l'import multi-nuits ([#importerNuits]). Reçoit la **sous-liste d'originaux** de
    /// la nuit et sa **date propre** (`dateNuit`) : chaque nuit a son [Prefixe] (donc son `dossierSession`),
    /// sa date de passage et son sous-ensemble de fichiers, mais partage l'unique inspection de la carte
    /// (portée par `ctx`). Le contrôle R5 (n° libre) et la garde « dossier vide » sont assurés **en amont**.
    ResultatImport importerUneNuit(
            ContexteImport ctx,
            Prefixe prefixe,
            List<Path> originauxNuit,
            LocalDate dateNuit,
            Consumer<Progression> progres) {
        RapportInspection rapport = ctx.rapport();
        JournalParse journal = ctx.journal();
        Long idPoint = ctx.idPoint();
        boolean conserverOriginaux = ctx.conserverOriginaux();
        boolean ecraser = ctx.ecraser();
        JetonAnnulation jeton = ctx.jeton();

        String nomSession = prefixe.nomDossierSession();
        Path dossierSession = workspace.dossierSession(nomSession);
        Path dossierBruts = workspace.dossierBruts(nomSession);
        Path dossierTransformes = workspace.dossierTransformes(nomSession);

        // Progression déterminée (#33) : N transformations, précédées de N copies UNIQUEMENT quand on
        // conserve les originaux (mode sans copie → pas de phase de copie, la source est lue en place).
        int nbOriginaux = originauxNuit.size();
        int totalEtapes = (conserverOriginaux ? nbOriginaux : 0) + nbOriginaux;

        // Suivi par fichier (#947) : le plan de la nuit est annoncé AVANT toute écriture, une entrée par
        // original (l'ordre du plan fixe les numéros 1..N que copies et transformations cibleront). En
        // multi-nuits, chaque nuit replanifie la table.
        ctx.suiviFichiers().planEtabli(nomsDes(originauxNuit));

        // Annulation (#146) : la copie et la transformation se font dans un dossier de session neuf ;
        // une annulation lève OperationAnnuleeException et on supprime la session partielle. Comme la
        // persistance est atomique en fin de course (O7), aucun passage n'est créé → pas de demi-état.
        List<SourceOriginal> sources;
        long volumeOriginaux;
        List<ResultatDecoupage> resultatsDecoupage;
        Path cheminJournalCopie;
        Path cheminReleveCopie;
        try {
            // 1-2) Originaux à transformer : copie protégée + renommage R6 (mode conservation) OU lecture
            //      directe de la source avec nom R6 calculé (mode sans copie). Cf. PreparationOriginaux.
            sources = preparation.preparer(
                    conserverOriginaux,
                    originauxNuit,
                    dossierBruts,
                    prefixe,
                    totalEtapes,
                    progres,
                    jeton,
                    ctx.suiviFichiers());
            // Volume des originaux CONSERVÉS dans le workspace : en mode sans copie, rien n'est stocké
            // localement → 0, sinon M-Passage afficherait le volume de la source (carte SD) comme des
            // bruts purgeables alors qu'aucun bruts/ n'existe.
            volumeOriginaux = conserverOriginaux
                    ? volumeTotal(sources.stream().map(SourceOriginal::chemin).toList())
                    : 0L;

            // Journal + relevé à la racine de la session, indépendamment du choix de conservation (petits
            // fichiers nécessaires à l'aval) ; cette écriture crée aussi le dossier de session en mode
            // sans copie (où aucun bruts/ n'est produit).
            cheminJournalCopie = ctx.sansJournal()
                    ? JournalDeRepli.ecrireTraceSynthetique(dossierSession)
                    : copie.copierVers(rapport.cheminJournal(), dossierSession);
            // Relevé climatique restreint à CETTE nuit (#1696) : sur une carte multi-nuits à log unique,
            // le THLog couvre tout le déploiement ; on n'en garde que les mesures de la nuit du passage.
            cheminReleveCopie = rapport.aUnReleveClimatique()
                    ? copierReleveDeNuit(rapport.cheminReleveClimatique(), dossierSession, dateNuit)
                    : null;

            // 3) Transformation R10/R11 (découpée en parallèle, #12, résiliente #155 : un original invalide
            //    est rejeté et consigné, pas bloquant).
            // Garde-fou double expansion (#…) : la fréquence d'acquisition du log (Fe…kHz) fait foi pour
            // rejeter une source déjà ralentie ; null en mode dégradé (pas de journal → seuil absolu).
            resultatsDecoupage = decoupage.decouper(
                    sources,
                    dossierTransformes,
                    prefixe,
                    journal.frequenceEchantillonnageHz(),
                    nbOriginaux,
                    totalEtapes,
                    progres,
                    jeton,
                    ctx.suiviFichiers());
            // Re-vérification après la phase de transformation : une annulation pendant la DERNIÈRE
            // transformation (postérieure à son propre point de contrôle) doit aussi stopper l'import.
            jeton.leverSiAnnule();
        } catch (RuntimeException echec) {
            // Annulation (#146) OU erreur fatale (p. ex. écriture workspace impossible, #155) : on nettoie
            // la session partielle et on remonte — pas de demi-état sur disque ni de passage persisté.
            supprimerSessionPartielle(dossierSession);
            throw echec;
        }

        // Dimension doublon (#214/#147) : passages déjà en base pour cette nuit (même série + date) AVANT
        // cet import (vide en écrasement : c'est un remplacement, pas un doublon). Décision déléguée au DAO,
        // qui possède déjà les lectures de nuit, pour garder l'orchestrateur cohésif. Date null-safe via
        // String.valueOf (→ "null", sans correspondance).
        List<PassageExistant> doublonsNuit =
                agregatDao.doublonsDeNuitPourRapport(ecraser, journal.numeroSerie(), String.valueOf(dateNuit));

        // Bilan d'import résilient (#155) : tri transformés / rejetés + rapport, délégué à la fabrique.
        RapportImportFabrique.BilanImport bilan =
                RapportImportFabrique.bilan(ctx.dossierSource(), rapport, resultatsDecoupage, doublonsNuit);
        List<TransformationOriginal> transformations = bilan.transformations();
        if (transformations.isEmpty()) {
            supprimerSessionPartielle(dossierSession);
            throw new RegleMetierException("Aucun enregistrement original n'a pu être importé : "
                    + bilan.rejets().size()
                    + " fichier(s) rejeté(s) (ex. « "
                    + (bilan.rejets().isEmpty() ? "" : bilan.rejets().get(0).erreur())
                    + " »).");
        }
        RapportImport rapportImport = bilan.rapport();

        // 4) Construction des entités de l'agrégat.
        Enregistreur enregistreur = new Enregistreur(journal.numeroSerie(), journal.versionModele(), null);
        Micro micro = fabriqueEntites.micro(journal);
        Passage passage = fabriqueEntites.passage(journal, idPoint, prefixe, dateNuit);
        long volumeSequences = transformations.stream()
                .flatMap(t -> t.sequences().stream())
                .mapToLong(SequenceProduite::octets)
                .sum();
        SessionDEnregistrement session =
                new SessionDEnregistrement(null, dossierSession.toString(), volumeOriginaux, volumeSequences, null);
        // Entité journal **uniforme** : en mode dégradé, le journal de repli porte déjà des évènements
        // vides et l'anomalie « import dégradé », donc on construit la trace de la même façon (#107).
        JournalDuCapteur journalEntite = new JournalDuCapteur(
                null, cheminJournalCopie.toString(), journal.evenementsJson(), journal.anomaliesJson(), null);
        ReleveClimatique releveEntite =
                cheminReleveCopie == null ? null : new ReleveClimatique(null, cheminReleveCopie.toString(), null, null);

        // Dernière fenêtre d'annulation : juste avant le point de non-retour (la persistance). Au-delà,
        // le passage est créé. Cette vérification nettoie elle-même la session (hors du bloc try/catch).
        verifierAnnulation(jeton, dossierSession);

        // 5) Persistance atomique de l'agrégat (O7 : tout ou rien).
        long[] ids = new long[2]; // [idPassage, idSession]
        uniteDeTravail.executer(cx -> {
            // Écrasement (#214) : on supprime l'ancien passage au quadruplet DANS la même transaction que
            // l'insertion du nouveau, donc tout-ou-rien (ON DELETE CASCADE pour session/originaux/séquences).
            if (ecraser) {
                agregatDao.supprimerPassageAuQuadruplet(cx, idPoint, prefixe.annee(), prefixe.numeroPassage());
            }
            agregatDao.upsertEnregistreur(cx, enregistreur);
            if (micro != null) {
                agregatDao.insererMicroSiAbsent(cx, micro);
            }
            ids[0] = agregatDao.insererPassage(cx, passage);
            ids[1] = agregatDao.insererSession(cx, ids[0], session);
            agregatDao.insererJournal(cx, ids[1], journalEntite);
            if (releveEntite != null) {
                agregatDao.insererReleve(cx, ids[1], releveEntite);
            }
            for (TransformationOriginal t : transformations) {
                EnregistrementOriginal original = new EnregistrementOriginal(
                        null,
                        t.nomOriginal(),
                        t.cheminOriginal().toString(),
                        t.dureeSourceSecondes(),
                        t.frequenceSourceHz(),
                        t.sha256(),
                        null,
                        t.tailleSourceOctets());
                long idOriginal = agregatDao.insererOriginal(cx, ids[1], original);
                for (SequenceProduite sp : t.sequences()) {
                    // #530 : l'heure réelle de la tranche est encodée dans son nom (_AAAAMMJJ_HHMMSS_000),
                    // extraite ici pour être persistée (recorded_at) et servir le tri / filtre par heure.
                    // #1299 : taille et empreinte courte, calculées à l'écriture de la tranche, sont
                    // persistées comme preuves d'identité (réactivation d'un passage archivé).
                    SequenceDEcoute sequence = new SequenceDEcoute(
                            null,
                            sp.nomFichier(),
                            null,
                            sp.index(),
                            sp.offsetSourceSecondes(),
                            sp.dureeSecondes(),
                            sp.chemin().toString(),
                            false,
                            null,
                            Prefixe.horodatageDe(sp.nomFichier()).orElse(null),
                            sp.octets(),
                            sp.empreinte());
                    agregatDao.insererSequence(cx, ids[1], idOriginal, sequence);
                }
            }
        });

        Passage passagePersiste = avecId(passage, ids[0]);
        SessionDEnregistrement sessionPersistee =
                new SessionDEnregistrement(ids[1], session.cheminRacine(), volumeOriginaux, volumeSequences, ids[0]);
        int nombreSequences =
                transformations.stream().mapToInt(t -> t.sequences().size()).sum();
        return new ResultatImport(
                passagePersiste,
                sessionPersistee,
                journal.numeroSerie(),
                transformations.size(),
                nombreSequences,
                journal.anomalies(),
                rapportImport);
    }

    /// Remappe la progression **locale** d'une nuit (fraction 0→1) dans sa **tranche globale**
    /// `[index/total, (index+1)/total]` et préfixe le libellé par « Nuit i/N · », pour une barre de
    /// progression continue à l'échelle de tout l'import multi-nuits (#33).
    private static Consumer<Progression> progressionNuit(Consumer<Progression> global, int index, int total) {
        return locale -> global.accept(new Progression(
                "Nuit " + (index + 1) + "/" + total + " · " + locale.libelle(), (index + locale.fraction()) / total));
    }

    /// Supprime la **session partielle** (dossier `bruts/`+`transformes/` en cours de constitution)
    /// laissée par un import **annulé** (#146), pour ne pas accumuler des fichiers à moitié copiés.
    /// Best-effort (cf. [ExtracteurZip#supprimerRecursivement]).
    private static void supprimerSessionPartielle(Path dossierSession) {
        ExtracteurZip.supprimerRecursivement(dossierSession);
    }

    /// Vérifie l'annulation **hors du bloc try/catch de nettoyage** (#146) : si annulé, supprime la
    /// session partielle puis lève [OperationAnnuleeException]. Utilisé juste avant la persistance, où le
    /// `catch` couvrant la copie/transformation ne s'applique plus.
    private static void verifierAnnulation(JetonAnnulation jeton, Path dossierSession) {
        if (jeton.estAnnule()) {
            supprimerSessionPartielle(dossierSession);
            throw new OperationAnnuleeException();
        }
    }

    private static Passage avecId(Passage p, long id) {
        return new Passage(
                id,
                p.numeroPassage(),
                p.annee(),
                p.dateEnregistrement(),
                p.heureDebut(),
                p.heureFin(),
                p.parametresAcquisition(),
                p.statutWorkflow(),
                p.verdictVerification(),
                p.commentaire(),
                p.donneesMeteo(),
                p.deposeLe(),
                p.idPoint(),
                p.idEnregistreur());
    }

    /// Noms de fichiers (sans chemin) des originaux, dans l'ordre du plan (numéros 1..N du suivi #947).
    private static List<String> nomsDes(List<Path> originaux) {
        return originaux.stream()
                .map(original -> original.getFileName().toString())
                .toList();
    }

    private static long volumeTotal(List<Path> fichiers) {
        long total = 0;
        try {
            for (Path fichier : fichiers) {
                total += Files.size(fichier);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Calcul du volume des originaux impossible", e);
        }
        return total;
    }

    /// Écrit dans la session un relevé climatique THLog **restreint à la nuit** `dateNuit` (#1696). La
    /// source (carte SD) est lue seule (R9) ; ce n'est volontairement pas une copie fidèle (empreinte
    /// différente), donc on n'emprunte pas [CopieProtegee]. En mono-nuit, toutes les lignes sont
    /// conservées : comportement inchangé.
    private static Path copierReleveDeNuit(Path source, Path dossierSession, LocalDate dateNuit) {
        try {
            List<String> filtrees = FiltreThLogNuit.filtrer(Files.readAllLines(source), dateNuit);
            Files.createDirectories(dossierSession);
            Path cible = dossierSession.resolve(source.getFileName());
            Files.write(cible, filtrees);
            return cible;
        } catch (IOException echec) {
            throw new UncheckedIOException("Écriture du relevé climatique de la nuit impossible : " + source, echec);
        }
    }
}

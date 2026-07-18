package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.AcquisitionAncrage;
import fr.univ_amu.iut.commun.model.ExecutionParallele;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/// **Réactive un passage archivé** (#1302, EPIC #1297) : l'utilisateur désigne le dossier où il a
/// retrouvé ses fichiers, et l'application rebranche, **fichier par fichier**, uniquement ce qu'elle a
/// pu **vérifier**.
///
/// Le risque à fermer n'est pas de recopier des fichiers, c'est de recopier les **mauvais** : deux jeux
/// peuvent porter les mêmes noms sans être les mêmes (redécoupe, autre expansion, autre nuit du même
/// carré). Rebrancher des observations sur le mauvais audio produit un résultat **scientifiquement faux,
/// et silencieux** : l'utilisateur validerait un cri en écoutant autre chose. La garde
/// ([RebranchementSequences], cascade #1309) ne laisse donc **jamais** passer un fichier non vérifié : il
/// est compté, motivé, rapporté.
///
/// **Deux voies, une seule garde** (#1406). L'utilisateur désigne **un dossier** - une sauvegarde, un
/// disque externe, une **carte SD entière contenant plusieurs nuits** - et ce service **reconnaît** ce
/// qu'il contient, en confrontant les noms des fichiers à ceux qu'il a en base :
///
/// - les **séquences** y sont : on les rebranche directement (rien à recalculer) ;
/// - seuls les **bruts** y sont : [ReactivationDepuisBruts] les régénère, puis les soumet à **la même
///   garde**. La reproductibilité de la transformation est une **preuve**, pas un prérequis ;
/// - ni les unes ni les autres : on le dit ([VoieReactivation#AUCUNE]), on n'invente rien.
///
/// Les fichiers qui ne correspondent à **aucun** nom connu de cette session - les autres nuits de la
/// carte, par exemple - sont simplement **ignorés** : ni lus, ni copiés, ni touchés.
///
/// **Idempotent** : une séquence dont le fichier est déjà là est comptée `dejaPresentes` et laissée
/// intacte ; rejouer la réactivation ne casse rien. **Non destructif** : les fichiers sont **copiés**
/// depuis le dossier source (jamais déplacés), et ni les observations ni les vérifications ne sont
/// touchées - on rebranche des chemins, on ne recalcule rien.
///
/// **Une des trois coutures d'un même concept d'import** (#1662, EPIC B). La reconstruction (#1305,
/// [ServiceReconstructionPassages], hydratation d'un squelette #1710) et l'import groupé (#1708)
/// rapatrient les **observations** d'une nuit depuis la plateforme, mais *sans audio ni ancrage*
/// (`idDonneeVigieChiro` null) : le passage devient **consultable**. La réactivation est la variante
/// « **observations + audio + ancrage** », celle qui **complète** les deux autres : elle rebranche les
/// **fichiers** (le passage devient **écoutable**) et, sur un passage reconstruit, rapatrie les `donnees`
/// pour **acquérir l'ancrage différé** ([#acquerirAncrageSiNecessaire], #1571). Les trois partagent la
/// même exigence - ne rebrancher que ce qui est **vérifié**, et **dire** ce qui manque - et la même
/// surface : hors du fil JavaFX, progression, annulation, rapport honnête (jamais un simple « c'est fait »).
public class ServiceReactivationPassage {

    private static final String PARAM_ID_PASSAGE = "idPassage";

    /// Parallélisme de la régénération des bruts à l'hydratation (#1779), calqué sur l'import
    /// (`ServiceImport.PARALLELISME_FICHIERS`) : autant de tâches que de cœurs, le pic disque/mémoire étant
    /// borné par le sémaphore de [ExecutionParallele].
    private static final int PARALLELISME = Runtime.getRuntime().availableProcessors();

    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final EnregistrementOriginalDao originalDao;
    private final ServiceDisponibiliteAudio disponibilite;

    /// La garde, partagée par les deux voies.
    private final RebranchementSequences rebranchement;

    private final ReactivationDepuisBruts depuisBruts;

    /// La voie « bruts » d'un passage **reconstruit** (#1650) : il n'a pas d'inventaire d'originaux, on part
    /// donc du dossier (log + bruts) plutôt que des originaux connus.
    private final HydratationDepuisBruts hydratation;

    /// Remplacement du placeholder d'un passage reconstruit par les vrais originaux, après hydratation (#1651).
    private final AdoptionOriginauxReconstruits adoption;

    /// Port d'import des observations (#1264), **optionnel** (la feature « Import VigieChiro » est
    /// désactivable, #1057). Il sert ici la **phase d'ancrage** (#1571) : un passage reconstruit par CSV
    /// (#1565) a des observations sans ancrage plateforme, acquis à la réactivation quand l'audio revient.
    private final Optional<ImportObservations> importObservations;

    public ServiceReactivationPassage(
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            EnregistrementOriginalDao originalDao,
            VerificationIdentiteAudio verification,
            ServiceDisponibiliteAudio disponibilite,
            Optional<CrisAttendus> crisAttendus,
            Optional<RegenerationSequences> regeneration,
            Optional<InventaireBrutsSource> inventaireBruts,
            AdoptionOriginauxReconstruits adoption,
            Optional<ImportObservations> importObservations) {
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.originalDao = Objects.requireNonNull(originalDao, "originalDao");
        this.disponibilite = Objects.requireNonNull(disponibilite, "disponibilite");
        this.rebranchement = new RebranchementSequences(
                Objects.requireNonNull(verification, "verification"),
                Objects.requireNonNull(crisAttendus, "crisAttendus"));
        this.depuisBruts = new ReactivationDepuisBruts(
                verification, rebranchement, Objects.requireNonNull(regeneration, "regeneration"));
        // Voie hydratation (#1682) : les tranches y sont **régénérées** depuis le brut désigné, l'acoustique
        // n'y est donc qu'un indice (pas de veto). Un rebranchement dédié, en mode régénération, porte cette
        // règle - même vérification, même port de cris, mais acceptation structurelle.
        this.hydratation = new HydratationDepuisBruts(
                Objects.requireNonNull(inventaireBruts, "inventaireBruts"),
                regeneration,
                new RebranchementSequences(verification, crisAttendus, true),
                new ExecutionParallele(PARALLELISME));
        this.adoption = Objects.requireNonNull(adoption, "adoption");
        this.importObservations = Objects.requireNonNull(importObservations, "importObservations");
    }

    /// Réactive le passage depuis `dossierSource` (exploré **récursivement**).
    ///
    /// @param progres notifié au fil de l'opération (elle lit les fichiers : compter ~0,1 ms par séquence
    ///     avec empreinte, ~3,4 ms sans, cf. #1309)
    /// @throws RegleMetierException si le passage est introuvable, jamais importé, ou si le dossier
    ///     source n'existe pas
    public RapportReactivation reactiver(Long idPassage, Path dossierSource, Consumer<Progression> progres) {
        return reactiver(idPassage, dossierSource, progres, JetonAnnulation.neutre());
    }

    /// Variante **suivie et annulable** (#1597) à **un seul** consommateur : les deux phases longues (la
    /// régénération des séquences puis l'ancrage réseau) y sont reportées au **même** `progres` (console,
    /// journal). L'IHM, elle, préfère la variante à **deux** consommateurs ci-dessous, pour suivre chaque
    /// phase sur sa propre barre (#1780).
    public RapportReactivation reactiver(
            Long idPassage, Path dossierSource, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(progres, "progres");
        return reactiver(idPassage, dossierSource, progres, progres, jeton);
    }

    /// Variante à **deux progressions** (#1780) : depuis S3 (#1571) la réactivation enchaîne deux phases
    /// longues, désormais suivies **séparément** pour que la modale les montre sur deux barres distinctes -
    /// `progresRegeneration` pour la **régénération / le rebranchement** des séquences (phase disque, 0 -> 1)
    /// et `progresAncrage` pour l'**acquisition de l'ancrage** des observations (~48 pages sur VigieChiro,
    /// phase réseau, 0 -> 1). Le `jeton` est consulté **aux frontières de phase** (avant le rebranchement,
    /// avant l'ancrage) et **page par page** pendant l'ancrage.
    ///
    /// @param jeton consulté aux points de contrôle ; `annuler()` interrompt à la prochaine frontière /
    ///     page, en levant une [OperationAnnuleeException]
    public RapportReactivation reactiver(
            Long idPassage,
            Path dossierSource,
            Consumer<Progression> progresRegeneration,
            Consumer<Progression> progresAncrage,
            JetonAnnulation jeton) {
        Objects.requireNonNull(idPassage, PARAM_ID_PASSAGE);
        Objects.requireNonNull(dossierSource, "dossierSource");
        Objects.requireNonNull(progresRegeneration, "progresRegeneration");
        Objects.requireNonNull(progresAncrage, "progresAncrage");
        Objects.requireNonNull(jeton, "jeton");
        if (!Files.isDirectory(dossierSource)) {
            throw new RegleMetierException("Dossier introuvable : " + dossierSource + ".");
        }
        SessionDEnregistrement session = sessionDao
                .trouverParPassage(idPassage)
                .orElseThrow(() -> new RegleMetierException(
                        "Passage jamais importé localement : rien à réactiver pour " + idPassage + "."));

        List<SequenceDEcoute> sequences = sequenceDao.findBySession(session.id());
        List<EnregistrementOriginal> originaux = originalDao.findBySession(session.id());
        CandidatsReactivation candidats = CandidatsReactivation.dans(dossierSource);
        Optional<Prefixe> prefixe = prefixeDe(session);

        // Ce que le dossier contient, constaté et non supposé.
        jeton.leverSiAnnule();
        VoieReactivation voie = reconnaitre(sequences, originaux, candidats, prefixe);
        BilanReactivation bilan;
        if (voie == VoieReactivation.BRUTS) {
            bilan = depuisBruts.appliquer(sequences, originaux, candidats, prefixe, progresRegeneration);
        } else if (voie == VoieReactivation.RECONSTRUIT) {
            // Un passage reconstruit peut être hydraté depuis ses bruts (log + WAV) : si c'est possible, la
            // voie devient BRUTS (les séquences ont été régénérées) et on remplace le placeholder par les
            // vrais originaux (#1651) ; sinon on reste sur le compte rendu honnête (#1648), sans rien inventer.
            Optional<ResultatHydratation> hydrate =
                    hydratation.appliquer(sequences, dossierSource, prefixe, progresRegeneration, jeton);
            if (hydrate.isPresent()) {
                voie = VoieReactivation.BRUTS;
                ResultatHydratation resultat = hydrate.orElseThrow();
                bilan = resultat.bilan();
                adopterOriginaux(session, originaux, resultat);
            } else {
                bilan = rebranchement.rebrancher(sequences, candidats, progresRegeneration);
            }
        } else {
            bilan = rebranchement.rebrancher(sequences, candidats, progresRegeneration);
        }

        RapportReactivation rapport = conclure(idPassage, session, sequences, bilan, voie);
        jeton.leverSiAnnule();
        acquerirAncrageSiNecessaire(idPassage, rapport, progresAncrage, jeton);
        return rapport;
    }

    /// **Phase d'ancrage** (#1571) : acquiert l'ancrage plateforme (`idDonneeVigieChiro` / indice) des
    /// observations d'un passage reconstruit par CSV (#1565), qui en sont dépourvues. On ne le fait qu'**ici**,
    /// à la réactivation, et seulement si l'**audio est (re)devenu disponible** : l'ancrage n'est requis que
    /// pour publier des corrections, et corriger suppose d'avoir écouté. Le ré-import depuis les `donnees`
    /// (remplacer = true) rapatrie l'ancrage tout en **préservant les validations** de l'observateur ; la
    /// passe `donnees` complète y est acceptable, opération déjà lourde et voulue.
    ///
    /// Ne se déclenche que sur un passage **reconstruit** (rattaché à une participation, ancrage manquant) :
    /// un passage importé normalement porte déjà son ancrage, la phase ne s'y déclenche pas — donc aucune
    /// dépendance réseau n'est imposée à la réactivation d'un passage local ordinaire.
    private void acquerirAncrageSiNecessaire(
            Long idPassage, RapportReactivation rapport, Consumer<Progression> progresAncrage, JetonAnnulation jeton) {
        if (importObservations.isEmpty() || rapport.decompte().disponibilite() == DisponibiliteAudio.ABSENTE) {
            return;
        }
        AcquisitionAncrage.acquerirSiNecessaire(importObservations.get(), idPassage, progresAncrage, jeton);
    }

    /// Reconnaît ce que le dossier contient. Les **séquences** l'emportent sur les **bruts** : quand les
    /// deux sont là (une sauvegarde de l'espace de travail entier), rebrancher les tranches existantes est
    /// à la fois plus rapide et plus sûr que les recalculer.
    private static VoieReactivation reconnaitre(
            List<SequenceDEcoute> sequences,
            List<EnregistrementOriginal> originaux,
            CandidatsReactivation candidats,
            Optional<Prefixe> prefixe) {
        boolean sequencesTrouvees = sequences.stream()
                .filter(sequence -> !Files.exists(Path.of(sequence.cheminFichier())))
                .anyMatch(sequence -> !candidats.pour(sequence.nomFichier()).isEmpty());
        if (sequencesTrouvees) {
            return VoieReactivation.TRANSFORMES;
        }
        boolean brutsTrouves = originaux.stream()
                .anyMatch(original -> !candidats.brutsDe(original, prefixe).isEmpty());
        if (brutsTrouves) {
            return VoieReactivation.BRUTS;
        }
        return sansInventaireExploitable(originaux) ? VoieReactivation.RECONSTRUIT : VoieReactivation.AUCUNE;
    }

    /// Un passage **reconstruit** (#1305) ne porte aucun original exploitable pour repartir des bruts : ses
    /// « originaux » se réduisent à un placeholder sans **fréquence d'acquisition** (ni empreinte). Sans
    /// cette fréquence, aucune tranche ne peut être régénérée, et la voie « bruts » ne peut rien apparier.
    /// On distingue ce cas de l'absence pure ([VoieReactivation#AUCUNE]) pour rendre un compte rendu
    /// **honnête** au lieu de prétendre « introuvables » (#1648, EPIC #1653) : un passage réel, même
    /// archivé ou purgé, garde en base la fréquence d'acquisition de ses originaux.
    private static boolean sansInventaireExploitable(List<EnregistrementOriginal> originaux) {
        return originaux.stream().noneMatch(original -> original.frequenceEchantillonnageHz() != null);
    }

    /// Remplace le placeholder du passage reconstruit par les vrais originaux régénérés (#1651). Les
    /// placeholders sont les originaux **sans fréquence d'acquisition** (ce que pose la reconstruction) ; les
    /// vrais originaux, eux, la portent (lue du log). Sans objet si rien n'a été hydraté.
    private void adopterOriginaux(
            SessionDEnregistrement session, List<EnregistrementOriginal> originaux, ResultatHydratation resultat) {
        List<EnregistrementOriginal> placeholders = originaux.stream()
                .filter(original -> original.frequenceEchantillonnageHz() == null)
                .toList();
        adoption.adopter(session, placeholders, resultat.brutsRebranches(), resultat.frequenceAcquisitionHz());
    }

    /// Remet la fiche du passage d'aplomb, puis rend compte.
    ///
    /// Quand l'audio est **entièrement** revenu, le marqueur d'archivage tombe (l'audit redevient exigeant
    /// sur ses séquences). Une réactivation **partielle** le conserve : les absences restantes sont
    /// toujours expliquées par l'archivage, pas par une corruption.
    private RapportReactivation conclure(
            Long idPassage,
            SessionDEnregistrement session,
            List<SequenceDEcoute> sequences,
            BilanReactivation bilan,
            VoieReactivation voie) {
        disponibilite.invalider(idPassage);
        DecompteAudio decompte = disponibilite.decompte(idPassage);
        sessionDao.majVolumeSequences(session.id(), volumeSequences(sequences));
        if (decompte.disponibilite() == DisponibiliteAudio.COMPLETE) {
            sessionDao.marquerArchivee(session.id(), null);
        }
        return new RapportReactivation(
                bilan.reactivees,
                bilan.ecarts.size(),
                bilan.manquantes,
                bilan.dejaPresentes,
                bilan.confianceMinimale,
                bilan.ecarts,
                decompte,
                voie,
                indiceAcoustique(bilan));
    }

    /// Indice acoustique **non bloquant** (#1682) du bilan, ou `null` s'il n'a rien mesuré (voies autres que
    /// l'hydratation, ou aucune séquence porteuse de cris) : rien à afficher.
    private static IndiceAcoustique indiceAcoustique(BilanReactivation bilan) {
        return bilan.acoustiqueMesurees > 0
                ? new IndiceAcoustique(bilan.acoustiqueMesurees, bilan.acoustiqueConcordantes)
                : null;
    }

    /// Préfixe de la session, relu du **nom de son dossier** (`Car…-2026-Pass1-A1`) : c'est le seul endroit
    /// où `passage` peut le retrouver sans dépendre de `sites` (cycle). Vide si le dossier a été renommé à
    /// la main : la voie « transformés » n'en a pas besoin, seule la voie « bruts » s'y refusera.
    private static Optional<Prefixe> prefixeDe(SessionDEnregistrement session) {
        Path nom = Path.of(session.cheminRacine()).getFileName();
        return Prefixe.depuisNomDossier(nom == null ? null : nom.toString());
    }

    /// Volume des séquences **présentes** sur disque, pour remettre la fiche du passage d'aplomb
    /// (l'archivage l'avait mis à zéro).
    private static long volumeSequences(List<SequenceDEcoute> sequences) {
        long total = 0;
        for (SequenceDEcoute sequence : sequences) {
            total += tailleSilencieuse(Path.of(sequence.cheminFichier()));
        }
        return total;
    }

    private static long tailleSilencieuse(Path fichier) {
        try {
            return Files.exists(fichier) ? Files.size(fichier) : 0;
        } catch (IOException e) {
            return 0;
        }
    }
}

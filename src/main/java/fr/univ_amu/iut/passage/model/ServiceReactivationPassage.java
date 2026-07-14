package fr.univ_amu.iut.passage.model;

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
public class ServiceReactivationPassage {

    private static final String PARAM_ID_PASSAGE = "idPassage";

    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final EnregistrementOriginalDao originalDao;
    private final ServiceDisponibiliteAudio disponibilite;

    /// La garde, partagée par les deux voies.
    private final RebranchementSequences rebranchement;

    private final ReactivationDepuisBruts depuisBruts;

    public ServiceReactivationPassage(
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            EnregistrementOriginalDao originalDao,
            VerificationIdentiteAudio verification,
            ServiceDisponibiliteAudio disponibilite,
            Optional<CrisAttendus> crisAttendus,
            Optional<RegenerationSequences> regeneration) {
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.originalDao = Objects.requireNonNull(originalDao, "originalDao");
        this.disponibilite = Objects.requireNonNull(disponibilite, "disponibilite");
        this.rebranchement = new RebranchementSequences(
                Objects.requireNonNull(verification, "verification"),
                Objects.requireNonNull(crisAttendus, "crisAttendus"));
        this.depuisBruts = new ReactivationDepuisBruts(
                verification, rebranchement, Objects.requireNonNull(regeneration, "regeneration"));
    }

    /// Réactive le passage depuis `dossierSource` (exploré **récursivement**).
    ///
    /// @param progres notifié au fil de l'opération (elle lit les fichiers : compter ~0,1 ms par séquence
    ///     avec empreinte, ~3,4 ms sans, cf. #1309)
    /// @throws RegleMetierException si le passage est introuvable, jamais importé, ou si le dossier
    ///     source n'existe pas
    public RapportReactivation reactiver(Long idPassage, Path dossierSource, Consumer<Progression> progres) {
        Objects.requireNonNull(idPassage, PARAM_ID_PASSAGE);
        Objects.requireNonNull(dossierSource, "dossierSource");
        Objects.requireNonNull(progres, "progres");
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
        VoieReactivation voie = reconnaitre(sequences, originaux, candidats, prefixe);
        BilanReactivation bilan = voie == VoieReactivation.BRUTS
                ? depuisBruts.appliquer(sequences, originaux, candidats, prefixe, progres)
                : rebranchement.rebrancher(sequences, candidats, progres);

        return conclure(idPassage, session, sequences, bilan, voie);
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
        return brutsTrouves ? VoieReactivation.BRUTS : VoieReactivation.AUCUNE;
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
                voie);
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

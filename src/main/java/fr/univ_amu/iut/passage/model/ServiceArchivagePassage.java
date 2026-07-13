package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// **Archive un passage** (#1300, EPIC #1297) : purge volontairement son audio local (les séquences
/// de `transformes/` et les `bruts/` encore là) pour récupérer l'espace disque, en préservant tout
/// ce qui fait l'intérêt du passage archivé : observations, vérifications, résultats Tadarida (le
/// CSV n'est pas une séquence, il reste), journal du capteur et relevé climatique. Après
/// l'opération, la [DisponibiliteAudio] du passage vaut `ABSENTE` : consultable, plus écoutable,
/// réactivable par réimport (#1302).
///
/// L'ordre des étapes est la moitié du contrat :
///
/// 1. **Garde métier** : seul un passage [StatutWorkflow#DEPOSE] s'archive. Archiver avant le dépôt,
///    c'est perdre l'audio avant que le serveur ne l'ait analysé : refus net, pas un avertissement.
/// 2. **Capturer l'identité AVANT de supprimer** ([BackfillEmpreintes#remplirSession]) : c'est la
///    dernière occasion de poser taille et empreinte (#1299) sur les séquences qui n'en ont pas ;
///    après la purge, seule la cascade (#1309) resterait.
/// 3. **Purger** : les fichiers des séquences (par leurs `file_path` persistés : la base dit ce qui
///    est de l'audio, tout le reste de `transformes/` est préservé), puis les `bruts/`
///    ([ServicePurgeOriginaux]). Best-effort, comme la purge des originaux : un fichier verrouillé
///    n'interrompt pas le reste (la disponibilité dira `PARTIELLE`, l'archivage reste rejouable).
/// 4. **Marquer le geste** ([fr.univ_amu.iut.passage.model.dao.SessionDao#marquerArchivee]) : le
///    fait déclaré qui distingue « archivé » de « corrompu » pour l'audit (#1303, #1348).
/// 5. **Invalider** le cache de [ServiceDisponibiliteAudio] : l'état observé a changé.
///
/// Idempotent : ré-archiver un passage déjà archivé re-purge ce qui resterait et repose le marqueur.
public class ServiceArchivagePassage {

    private static final String PARAM_ID_PASSAGE = "idPassage";

    private final PassageDao passageDao;
    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final BackfillEmpreintes backfillEmpreintes;
    private final ServicePurgeOriginaux purgeOriginaux;
    private final ServiceDisponibiliteAudio disponibilite;
    private final Horloge horloge;

    public ServiceArchivagePassage(
            PassageDao passageDao,
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            BackfillEmpreintes backfillEmpreintes,
            ServicePurgeOriginaux purgeOriginaux,
            ServiceDisponibiliteAudio disponibilite,
            Horloge horloge) {
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.backfillEmpreintes = Objects.requireNonNull(backfillEmpreintes, "backfillEmpreintes");
        this.purgeOriginaux = Objects.requireNonNull(purgeOriginaux, "purgeOriginaux");
        this.disponibilite = Objects.requireNonNull(disponibilite, "disponibilite");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Espace récupérable (octets) par l'archivage du passage : les fichiers de séquences encore
    /// présents + les `bruts/` encore là. Sert à l'**annonce avant confirmation** (l'opération est
    /// destructive : l'utilisateur décide en connaissant le gain). 0 si le passage n'a pas de
    /// session (jamais importé) ou plus aucun fichier.
    public long volumeRecuperable(Long idPassage) {
        Objects.requireNonNull(idPassage, PARAM_ID_PASSAGE);
        return sessionDao
                .trouverParPassage(idPassage)
                .map(session -> volumeSequences(sequenceDao.findBySession(session.id()))
                        + purgeOriginaux.volumeRecuperableSession(Path.of(session.cheminRacine())))
                .orElse(0L);
    }

    /// Séquences du passage **encore sans empreinte** (#1299) : à annoncer avant la confirmation,
    /// car leur identité sera capturée in extremis par l'archivage ; si cette capture échouait
    /// (fichiers déjà partis), elles ne seraient réactivables que par la cascade (#1309).
    public int sequencesSansEmpreinte(Long idPassage) {
        Objects.requireNonNull(idPassage, PARAM_ID_PASSAGE);
        return sessionDao
                .trouverParPassage(idPassage)
                .map(session -> sequenceDao.sansEmpreinteDeSession(session.id()).size())
                .orElse(0);
    }

    /// Archive le passage : capture les empreintes, purge l'audio, pose le marqueur, invalide la
    /// disponibilité. Voir le déroulé dans la doc de classe.
    ///
    /// @return le bilan (octets libérés, séquences purgées, empreintes capturées in extremis)
    /// @throws RegleMetierException si le passage est introuvable, jamais importé, ou non déposé
    public BilanArchivage archiver(Long idPassage) {
        Objects.requireNonNull(idPassage, PARAM_ID_PASSAGE);
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException("Passage introuvable : " + idPassage + "."));
        if (passage.statutWorkflow() != StatutWorkflow.DEPOSE) {
            throw new RegleMetierException("Seul un passage déposé peut être archivé : celui-ci est « "
                    + passage.statutWorkflow().libelle()
                    + " ». Archiver avant le dépôt perdrait l'audio avant son analyse par la plateforme.");
        }
        SessionDEnregistrement session = sessionDao
                .trouverParPassage(idPassage)
                .orElseThrow(() -> new RegleMetierException(
                        "Passage jamais importé localement : rien à archiver pour " + idPassage + "."));

        int empreintesCapturees =
                backfillEmpreintes.remplirSession(session.id()).sequencesRemplies();
        long octetsLiberes = purgerSequences(sequenceDao.findBySession(session.id()));
        octetsLiberes += purgeOriginaux.purgerSession(Path.of(session.cheminRacine()));
        sessionDao.marquerOriginauxPurges(session.id());
        sessionDao.marquerArchivee(session.id(), horloge.maintenant());
        disponibilite.invalider(idPassage);
        return new BilanArchivage(octetsLiberes, empreintesCapturees);
    }

    /// Supprime les fichiers des séquences (best-effort) et renvoie les octets libérés. On supprime
    /// par les `file_path` **persistés** : la base sait ce qui est de l'audio ; le CSV Tadarida et
    /// tout autre fichier de `transformes/` ne sont pas touchés.
    private long purgerSequences(List<SequenceDEcoute> sequences) {
        long liberes = 0;
        for (SequenceDEcoute sequence : sequences) {
            liberes += supprimerSilencieux(Path.of(sequence.cheminFichier()));
        }
        return liberes;
    }

    /// Supprime un fichier et renvoie sa taille, 0 s'il était absent ou insupprimable (best-effort).
    private static long supprimerSilencieux(Path fichier) {
        try {
            long taille = Files.size(fichier);
            return Files.deleteIfExists(fichier) ? taille : 0;
        } catch (IOException e) {
            return 0;
        }
    }

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

    /// Bilan d'un archivage : octets rendus au disque et séquences dont l'empreinte (#1299) a été
    /// capturée in extremis avant la purge.
    public record BilanArchivage(long octetsLiberes, int empreintesCapturees) {}
}

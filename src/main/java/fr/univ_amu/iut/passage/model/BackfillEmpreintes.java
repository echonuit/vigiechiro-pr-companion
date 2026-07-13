package fr.univ_amu.iut.passage.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Rétro-remplissage **applicatif** des preuves d'identité (#1299) : pose `size_bytes` et
/// `content_fingerprint` sur les séquences importées avant la migration V23, et `size_bytes` sur
/// les originaux, en relisant les fichiers **encore présents** sur disque. Sans lui, aucun passage
/// existant ne serait réactivable par empreinte (#1302) : l'empreinte n'est calculable que tant que
/// les fichiers sont là.
///
/// **Idempotent** : ne cible que les lignes sans empreinte ([SequenceDao#sansEmpreinte()]) ou sans
/// taille ([EnregistrementOriginalDao#sansTaille()]). **Reprenable** : chaque ligne est renseignée
/// par sa propre mise à jour, une interruption reprend où elle s'était arrêtée. **Best-effort** :
/// un fichier absent ou illisible est simplement compté ignoré, il reste explicitement sans
/// empreinte (l'IHM, issue D, doit dire que ces passages ne seront pas réactivables par empreinte).
///
/// Contrairement au backfill de l'horodatage (#530, pur SQL + parsing), celui-ci **lit le disque**
/// (64 Kio par séquence) : sur un gros workspace froid, compter en secondes, voire en dizaines de
/// secondes sur disque mécanique. Il n'est donc **pas** rejoué au démarrage : il se déclenche
/// explicitement (commande CLI `retro-empreintes`), et l'archivage (#1300) capturera l'identité
/// d'un passage **avant** d'en purger les fichiers via [#remplirSession].
public final class BackfillEmpreintes {

    private final SequenceDao sequenceDao;
    private final EnregistrementOriginalDao originalDao;

    @Inject
    public BackfillEmpreintes(SequenceDao sequenceDao, EnregistrementOriginalDao originalDao) {
        this.sequenceDao = sequenceDao;
        this.originalDao = originalDao;
    }

    /// Renseigne tout le workspace : toutes les séquences sans empreinte et tous les originaux sans
    /// taille dont le fichier est présent.
    public Bilan remplirTout() {
        return remplir(sequenceDao.sansEmpreinte());
    }

    /// Renseigne les séquences d'**une session** (avant archivage du passage, #1300 : dernière
    /// occasion de capturer l'identité, les fichiers vont être supprimés). Les originaux sans
    /// taille sont traités globalement (ils sont peu nombreux et souvent déjà purgés).
    public Bilan remplirSession(Long idSession) {
        return remplir(sequenceDao.sansEmpreinteDeSession(idSession));
    }

    private Bilan remplir(Iterable<SequenceDEcoute> sequencesSansEmpreinte) {
        int sequencesRemplies = 0;
        int sequencesIgnorees = 0;
        for (SequenceDEcoute sequence : sequencesSansEmpreinte) {
            if (poserEmpreinte(sequence)) {
                sequencesRemplies++;
            } else {
                sequencesIgnorees++;
            }
        }
        int originauxRemplis = 0;
        int originauxIgnores = 0;
        for (EnregistrementOriginal original : originalDao.sansTaille()) {
            if (poserTaille(original)) {
                originauxRemplis++;
            } else {
                originauxIgnores++;
            }
        }
        return new Bilan(sequencesRemplies, sequencesIgnorees, originauxRemplis, originauxIgnores);
    }

    /// Pose taille + empreinte d'une séquence depuis son fichier ; `false` si le fichier est absent
    /// ou illisible (la ligne reste sans empreinte).
    private boolean poserEmpreinte(SequenceDEcoute sequence) {
        Path fichier = Path.of(sequence.cheminFichier());
        try {
            if (!Files.isRegularFile(fichier)) {
                return false;
            }
            sequenceDao.majEmpreinte(sequence.id(), Files.size(fichier), Empreintes.empreinteCourte(fichier));
            return true;
        } catch (IOException | IllegalStateException e) {
            return false;
        }
    }

    /// Pose la taille d'un original depuis son fichier ; `false` si le fichier est absent ou
    /// illisible (bruts purgés, carte SD non montée...).
    private boolean poserTaille(EnregistrementOriginal original) {
        Path fichier = Path.of(original.cheminFichier());
        try {
            if (!Files.isRegularFile(fichier)) {
                return false;
            }
            originalDao.majTaille(original.id(), Files.size(fichier));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /// Bilan d'une passe de rétro-remplissage : lignes renseignées, lignes ignorées (fichier absent
    /// ou illisible, restées explicitement sans empreinte).
    public record Bilan(int sequencesRemplies, int sequencesIgnorees, int originauxRemplis, int originauxIgnores) {}
}

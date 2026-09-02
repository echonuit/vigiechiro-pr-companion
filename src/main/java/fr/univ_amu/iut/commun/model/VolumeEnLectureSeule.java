package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Le volume qui porte ce chemin est-il monté en **lecture seule** (#4991) ?
///
/// Lit le drapeau du **volume**, et non les permissions du dossier : sous Windows c'est
/// `FILE_READ_ONLY_VOLUME`, celui que le système positionne pour une carte protégée en écriture. Une
/// carte SD dont le contrôleur est passé en lecture seule - le mode de fin de vie normal de cette
/// mémoire - s'y reconnaît.
///
/// **Elle n'écrit rien**, contrairement à [SondeAccessibilite] qui éprouve une destination par un
/// témoin. `Files.isWritable` ne répond pas non plus à cette question, et ment sur les partages
/// réseau comme sous Windows.
public final class VolumeEnLectureSeule {

    // Pourquoi aucune écriture sur la carte de l'observateur, alors que la sonde des destinations en
    // fait une (#4991). Trois raisons, par ordre de poids :
    //
    // 1. un témoin qui RÉUSSIT ne prouve rien. Il établit que cette écriture-là est passée, pas que
    //    la carte gardera ce qu'on lui confie. Son bénéfice se réduit au seul cas « accepte puis
    //    perd », qu'il ne détecte pas non plus ;
    // 2. le volume est AMOVIBLE. Une carte débranchée pendant l'écriture ou l'effacement du témoin
    //    laisse la FAT incohérente. Ce risque existe pour toute écriture, mais il serait ici créé
    //    par nous, là où on n'écrivait jamais. Les quatre usages de la sonde sont des destinations
    //    sur disque interne, et ne l'ont jamais posé ;
    // 3. `SondeAccessibilite` CRÉE le dossier avant de l'éprouver, son contrat étant « ce dossier
    //    peut-il servir de destination ». Sur une source, créer un dossier manquant n'a aucun sens.

    private static final Logger LOG = Logger.getLogger(VolumeEnLectureSeule.class.getName());

    private VolumeEnLectureSeule() {}

    /// `true` si le volume portant `chemin` est monté en lecture seule.
    ///
    /// Rend `false` dès que la question ne peut pas être posée - chemin absent, volume disparu entre
    /// deux gestes, système qui ne renseigne pas le drapeau. **Ne jamais affirmer sur une lecture
    /// ratée** : le silence est le comportement d'aujourd'hui, et une fausse alerte apprendrait à
    /// ignorer le vrai message.
    public static boolean vrai(Path chemin) {
        Objects.requireNonNull(chemin, "chemin");
        try {
            FileStore volume = Files.getFileStore(chemin);
            return volume.isReadOnly();
        } catch (IOException | RuntimeException illisible) {
            LOG.log(Level.FINE, illisible, () -> "Volume illisible, on ne conclut pas : " + chemin);
            return false;
        }
    }
}

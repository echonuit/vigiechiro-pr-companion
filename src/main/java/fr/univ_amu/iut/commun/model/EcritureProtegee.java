package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/// Écrit un **secret** sur disque : jamais dans un fichier plus permissif que lui, jamais à moitié
/// (#2735).
///
/// ## Le défaut qu'elle corrige
///
/// Écrire puis restreindre laisse une **fenêtre** : entre les deux, le fichier existe avec les
/// permissions de l'umask, souvent `644`, et le secret est lisible par les autres comptes de la
/// machine. La fenêtre est brève, mais elle se rouvre à **chaque création** du fichier, donc à chaque
/// reconnexion après une déconnexion, qui supprime le fichier.
///
/// Ici, le contenu n'atterrit que dans un fichier **créé d'emblée** avec les bonnes permissions : il
/// n'existe à aucun instant de fichier permissif contenant le secret.
///
/// ## Et le remplacement est atomique
///
/// Le temporaire est déplacé sur la cible par un `ATOMIC_MOVE`, dans le **même dossier** donc sur le
/// même système de fichiers. Un lecteur voit l'ancien contenu ou le nouveau, jamais un fichier
/// tronqué : une interruption en cours d'écriture laissait auparavant un JSON coupé, que le lecteur
/// traduit en « non connecté » - une déconnexion inexpliquée plutôt qu'une erreur.
///
/// Le déplacement conserve les permissions du temporaire : la cible hérite donc de `600`, y compris si
/// le fichier remplacé était plus permissif.
///
/// ## Ce qu'un arrêt brutal peut laisser
///
/// Le temporaire est supprimé sur toute erreur. Une coupure de courant entre sa création et le
/// déplacement peut en revanche en laisser un dans le dossier de travail. Il porte le secret, mais avec
/// **ses permissions restreintes** : c'est un résidu à balayer, pas une fuite.
///
/// ## Windows
///
/// `PosixFilePermissions` n'existe pas sur un système de fichiers non POSIX. Le temporaire y est créé
/// sans attribut, et le fichier reste protégé par les **ACL du profil utilisateur** : le dossier de
/// travail vit sous le profil, dont les autres comptes non administrateurs n'ont pas la clé. C'est une
/// protection réelle mais **différente**, et c'est pourquoi elle est écrite ici plutôt que sous-entendue.
public final class EcritureProtegee {

    /// Lecture et écriture pour le seul propriétaire (`600`).
    private static final Set<PosixFilePermission> PROPRIETAIRE_SEUL =
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private EcritureProtegee() {}

    /// Écrit `contenu` dans `cible`, en UTF-8, en remplaçant ce qui s'y trouvait.
    ///
    /// @throws IOException si l'écriture ou le remplacement échoue ; la cible garde alors son contenu
    ///     précédent, et le temporaire est supprimé plutôt que laissé sur le disque avec le secret
    public static void ecrire(Path cible, String contenu) throws IOException {
        Path dossier = cible.getParent();
        Files.createDirectories(dossier);
        Path temporaire = Files.createTempFile(dossier, ".secret-", ".tmp", attributsDeCreation());
        try {
            Files.writeString(temporaire, contenu);
            Files.move(temporaire, cible, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException echec) {
            Files.deleteIfExists(temporaire);
            throw echec;
        }
    }

    /// Les permissions posées **à la création** du temporaire, ou aucun attribut hors POSIX.
    ///
    /// Redondant avec le JDK, et volontairement : mesuré sous Java 25 avec un umask à `0002`,
    /// `Files.createTempFile` sans attribut crée déjà `rw-------` là où `Files.createFile` donne
    /// `rw-rw-r--`. Mais sa Javadoc qualifie ces permissions d'« implementation specific » : une
    /// propriété de sécurité ne se repose pas sur un détail d'implémentation, elle se demande.
    ///
    /// C'est aussi pourquoi le mutant qui inverse ce test survit à PIT : sur ce JDK, les deux chemins
    /// donnent le même fichier. Il est **équivalent par construction**, pas mal couvert.
    private static FileAttribute<?>[] attributsDeCreation() {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return new FileAttribute<?>[0];
        }
        return new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PROPRIETAIRE_SEUL)};
    }
}

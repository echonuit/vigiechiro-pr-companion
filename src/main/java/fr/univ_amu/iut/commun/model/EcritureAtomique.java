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

/// Écrit un fichier **d'un seul coup** : un lecteur y voit l'ancien contenu ou le nouveau, **jamais un
/// fichier tronqué** (#2735, généralisée à la clôture du lot #2722).
///
/// ## Le remplacement est atomique
///
/// Le contenu part dans un temporaire du **même dossier** - donc du même système de fichiers - déplacé
/// sur la cible par un `ATOMIC_MOVE`. Une interruption en cours d'écriture laissait auparavant un
/// fichier coupé : un `connexion.json` tronqué que le lecteur traduit en « non connecté » (une
/// déconnexion inexpliquée plutôt qu'une erreur), ou un manifeste de sauvegarde que la restauration
/// refuse.
///
/// ## Et pour un secret, les permissions dès la création
///
/// [#ecrireSecret] pose en plus les permissions **à la création du temporaire**. Écrire puis
/// restreindre laisserait une **fenêtre** : entre les deux appels, le fichier existe avec celles de
/// l'umask, souvent `644`, et le secret est lisible par les autres comptes de la machine. Elle se
/// rouvre à **chaque création** du fichier, donc à chaque reconnexion, puisque se déconnecter le
/// supprime.
///
/// Le déplacement conserve les permissions du temporaire : la cible hérite donc de `600`, y compris si
/// le fichier remplacé était plus permissif.
///
/// ⚠️ **Les deux donnent le même fichier sur les JDK actuels**, et c'est mesuré : `Files.createTempFile`
/// sans attribut crée déjà `rw-------`. Ce qui les distingue n'est donc pas le résultat observé mais la
/// **garantie** : [#ecrireSecret] l'exige et le resterait si le JDK changeait son défaut, [#ecrire] s'en
/// remet à lui. C'est aussi pourquoi PIT ne peut pas distinguer les deux chemins - un mutant
/// **équivalent par construction**, pas une couverture manquante.
///
/// Le choix de la méthode reste donc porteur de sens **au point d'appel** : il dit si le fichier est un
/// secret, ce qu'aucune permission ne dira jamais à sa place.
///
/// ## Ce qu'un arrêt brutal peut laisser
///
/// Le temporaire est supprimé sur toute erreur. Une coupure de courant entre sa création et le
/// déplacement peut en revanche en laisser un dans le dossier. Quand il porte un secret, il porte aussi
/// **ses permissions restreintes** : c'est un résidu à balayer, pas une fuite.
///
/// ## Windows
///
/// `PosixFilePermissions` n'existe pas sur un système de fichiers non POSIX. Le temporaire y est créé
/// sans attribut, et le fichier reste protégé par les **ACL du profil utilisateur** : le dossier de
/// travail vit sous le profil, dont les autres comptes non administrateurs n'ont pas la clé. C'est une
/// protection réelle mais **différente**, et c'est pourquoi elle est écrite ici plutôt que sous-entendue.
public final class EcritureAtomique {

    /// Lecture et écriture pour le seul propriétaire (`600`).
    private static final Set<PosixFilePermission> PROPRIETAIRE_SEUL =
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private EcritureAtomique() {}

    /// Écrit `contenu` dans `cible`, en UTF-8, en remplaçant ce qui s'y trouvait. La forme à employer
    /// pour ce qui n'est **pas** un secret : un manifeste de sauvegarde, un export.
    ///
    /// @throws IOException si l'écriture ou le remplacement échoue ; la cible garde alors son contenu
    ///     précédent, et le temporaire est supprimé plutôt que laissé sur le disque
    public static void ecrire(Path cible, String contenu) throws IOException {
        ecrire(cible, contenu, false);
    }

    /// Comme [#ecrire], et **restreint au propriétaire** : la forme à employer pour un secret, qui ne
    /// doit à aucun instant exister dans un fichier plus permissif que lui.
    public static void ecrireSecret(Path cible, String contenu) throws IOException {
        ecrire(cible, contenu, true);
    }

    private static void ecrire(Path cible, String contenu, boolean secret) throws IOException {
        Path dossier = cible.getParent();
        Files.createDirectories(dossier);
        Path temporaire = Files.createTempFile(dossier, ".ecriture-", ".tmp", attributsDeCreation(secret));
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
    private static FileAttribute<?>[] attributsDeCreation(boolean secret) {
        if (!secret || !FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return new FileAttribute<?>[0];
        }
        return new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PROPRIETAIRE_SEUL)};
    }
}

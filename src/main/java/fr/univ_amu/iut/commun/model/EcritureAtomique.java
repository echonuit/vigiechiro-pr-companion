package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
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

    /// Cinq tentatives espacées de 150 ms : ~600 ms d'insistance au total. Assez pour traverser une
    /// analyse antivirus, trop court pour qu'un utilisateur croie l'application figée.
    private static final int TENTATIVES = 5;

    private static final long MILLIS_ENTRE_TENTATIVES = 150L;

    private static final Deplacement DEPLACEMENT_REEL =
            (source, cible) -> Files.move(source, cible, StandardCopyOption.ATOMIC_MOVE);

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
        ecrire(cible, contenu, secret, DEPLACEMENT_REEL, Thread::sleep);
    }

    /// [#ecrire(Path,String,boolean)], avec le déplacement et l'attente **injectés**.
    ///
    /// ⚠️ Sans cette couture, la reprise n'est éprouvable **nulle part** : le cas qu'elle traverse ne se
    /// produit que sous Windows, où un lecteur concurrent bloque le remplacement, et sous POSIX le
    /// déplacement **réussit** quoi qu'on tienne ouvert. Mesuré par sonde (#3777). Même raison que
    /// `GestesFichiers` (#3525), `TailleFichier` (#3627) et `CouleurCli` (#3738).
    static void ecrire(Path cible, String contenu, boolean secret, Deplacement deplacement, Attente attente)
            throws IOException {
        Path dossier = cible.getParent();
        Files.createDirectories(dossier);
        Path temporaire = Files.createTempFile(dossier, ".ecriture-", ".tmp", attributsDeCreation(secret));
        try {
            Files.writeString(temporaire, contenu);
            deplacerEnInsistant(temporaire, cible, deplacement, attente);
        } catch (IOException | RuntimeException echec) {
            Files.deleteIfExists(temporaire);
            throw echec;
        }
    }

    /// Déplace, et **réessaie** tant que la cible est tenue par quelqu'un d'autre.
    ///
    /// ## Pourquoi une reprise, et pourquoi elle est bornée
    ///
    /// Mesuré sous Windows Server 2025 (#3777) : **n'importe quel** lecteur concurrent fait échouer le
    /// remplacement en `AccessDeniedException` - un `Files.newInputStream` suffit, il n'y a pas besoin
    /// d'un verrou. Or c'est le chemin d'écriture du fichier d'**amorçage** (#3507), et les tenues
    /// concurrentes y sont **ordinaires** : un antivirus qui analyse le fichier au moment où on le
    /// remplace, un outil de sauvegarde, une seconde instance qui lit.
    ///
    /// Insister quelques centaines de millisecondes traverse la tenue **transitoire**, qui est le cas
    /// mesuré. Insister sans fin masquerait le cas **durable** - une seconde instance ouverte - qui, lui,
    /// demande une action de l'utilisateur : d'où le butoir, et un refus qui **nomme** la situation.
    ///
    /// ⚠️ La reprise ne regarde pas le système d'exploitation. Sous POSIX, une `AccessDeniedException`
    /// est un vrai refus de droits : elle coûtera le butoir avant d'échouer, et c'est le prix assumé
    /// pour ne pas déduire un comportement d'un nom de plateforme - ce que [fr.univ_amu.iut.cli.CouleurCli]
    /// a appris à ne plus faire.
    private static void deplacerEnInsistant(Path temporaire, Path cible, Deplacement deplacement, Attente attente)
            throws IOException {
        AccessDeniedException dernier = null;
        for (int tentative = 1; tentative <= TENTATIVES; tentative++) {
            try {
                deplacement.deplacer(temporaire, cible);
                return;
            } catch (AccessDeniedException tenue) {
                dernier = tenue;
                if (tentative < TENTATIVES) {
                    patienter(attente);
                }
            }
        }
        throw refusCible(cible, dernier);
    }

    private static void patienter(Attente attente) throws IOException {
        try {
            attente.attendre(MILLIS_ENTRE_TENTATIVES);
        } catch (InterruptedException interrompu) {
            Thread.currentThread().interrupt();
            throw new IOException("Écriture interrompue pendant l'attente du fichier", interrompu);
        }
    }

    /// Le refus, quand la cible reste tenue : il **nomme** la cause et dit quoi faire.
    ///
    /// ⚠️ Sans cela l'utilisateur lirait « accès refusé » sur un fichier dont il est propriétaire, ce
    /// qui l'enverrait chercher un problème de droits qui n'existe pas.
    private static IOException refusCible(Path cible, AccessDeniedException cause) {
        IOException refus =
                new IOException("Le fichier « " + cible.getFileName() + " » est tenu ouvert par un autre programme, "
                        + "après " + TENTATIVES + " tentatives. Fermer l'application si elle est déjà "
                        + "lancée, ou l'outil qui garde ce fichier ouvert (antivirus, sauvegarde).");
        if (cause != null) {
            refus.initCause(cause);
        }
        return refus;
    }

    /// Ce que fait le déplacement final. Réel par défaut ; fabriqué dans les tests.
    @FunctionalInterface
    interface Deplacement {
        void deplacer(Path source, Path cible) throws IOException;
    }

    /// L'attente entre deux tentatives. Réelle par défaut ; instantanée dans les tests.
    @FunctionalInterface
    interface Attente {
        void attendre(long millis) throws InterruptedException;
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

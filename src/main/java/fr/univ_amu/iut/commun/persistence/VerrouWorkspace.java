package fr.univ_amu.iut.commun.persistence;

import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// Le **verrou d'un workspace** : un seul processus à la fois y écrit (#2731).
///
/// Rien ne l'empêchait jusqu'ici. Deux instances graphiques, une IHM et une CLI, ou une restauration
/// pendant un import : la base était remplacée sous les pieds de celui qui écrivait. Toutes les
/// garanties posées par ce lot (migration atomique #2728, filet #2729, restaurations vérifiées #2727
/// et #2730) tombent si un second processus écrit pendant l'opération.
///
/// **Verrou de fichier système**, et non fichier de PID. La différence est décisive : le système
/// d'exploitation **relâche** le verrou quand le processus meurt, donc un plantage ne condamne pas le
/// workspace. Un fichier de PID demanderait de savoir si le PID 12345 est encore vivant, question qui
/// n'a pas de réponse portable, et un verrou qu'on ne sait pas relâcher est pire que pas de verrou :
/// il transforme un incident en blocage définitif.
///
/// Le PID et l'horodatage sont **écrits dans le fichier** pour le message, jamais pour la décision.
public final class VerrouWorkspace implements AutoCloseable {

    /// Nom du fichier de verrou, à la racine du workspace.
    static final String NOM_FICHIER = ".verrou";

    /// Ce que **ce processus** verrouille déjà. Une JVM ne peut pas prendre deux fois le même verrou
    /// de fichier, et n'a d'ailleurs pas à se protéger d'elle-même : l'IHM tient le verrou pour toute
    /// sa durée, et une restauration lancée depuis cette IHM doit passer.
    private static final Set<Path> DETENUS = ConcurrentHashMap.newKeySet();

    private final FileChannel canal;
    private final FileLock verrou;
    private final Path fichier;

    /// `false` pour un jeton d'opération exclusive qui **réutilise** la détention du processus : le
    /// fermer ne doit pas relâcher le verrou de celui qui le tenait avant.
    private final boolean aRelacher;

    private VerrouWorkspace(FileChannel canal, FileLock verrou, Path fichier, boolean aRelacher) {
        this.canal = canal;
        this.verrou = verrou;
        this.fichier = fichier;
        this.aRelacher = aRelacher;
    }

    /// Ouvre le verrou le temps d'une **opération exclusive** (migration, restauration, reset).
    ///
    /// Trois cas, et un seul est un refus :
    ///
    /// - ce processus détient déjà le verrou (l'IHM) : on rend un jeton qui ne relâche rien ;
    /// - le workspace est libre : on le prend, et le jeton le rendra ;
    /// - un **autre** processus l'occupe : refus, en le nommant. C'est tout l'objet de #2731 : mieux
    ///   vaut « le workspace est utilisé par le processus 12345 » qu'un échec SQLite tardif, au
    ///   milieu d'une écriture, que personne ne sait interpréter.
    ///
    /// @throws DataAccessException si un autre processus occupe le workspace
    public static VerrouWorkspace pourOperationExclusive(Workspace workspace, String operation) {
        Path fichier = fichierDe(workspace);
        if (DETENUS.contains(fichier)) {
            return new VerrouWorkspace(null, null, fichier, false);
        }
        return prendre(workspace)
                .orElseThrow(() -> new RefusAvantEcriture(
                        "Ce dossier de travail est déjà utilisé (" + occupant(workspace) + ") :"
                                + " impossible de lancer " + operation + " en même temps. Fermez l'autre"
                                + " fenêtre ou attendez la fin de l'opération en cours, puis recommencez.",
                        null));
    }

    /// Tente de prendre le verrou. [Optional] **vide** si un autre processus le tient : c'est un
    /// refus, pas une erreur, et l'appelant décide quoi en dire.
    public static Optional<VerrouWorkspace> prendre(Workspace workspace) {
        Path fichier = fichierDe(workspace);
        try {
            Files.createDirectories(fichier.getParent());
            FileChannel canal = FileChannel.open(
                    fichier, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
            FileLock verrou = canal.tryLock();
            if (verrou == null) {
                canal.close();
                return Optional.empty();
            }
            inscrireLOccupant(canal);
            DETENUS.add(fichier);
            return Optional.of(new VerrouWorkspace(canal, verrou, fichier, true));
        } catch (OverlappingFileLockException dejaPrisIci) {
            // Ce processus le tient déjà : la JVM refuse de le prendre deux fois. Vu d'ici c'est le
            // même cas qu'un autre processus, l'appelant n'a pas à écrire.
            return Optional.empty();
        } catch (IOException echec) {
            throw new DataAccessException("Verrou du workspace impossible à poser : " + fichier, echec);
        }
    }

    /// Qui occupe le workspace, en clair, pour un message. Chaîne **vide** si le fichier ne dit rien
    /// (verrou libre, ou fichier illisible) : cette lecture ne sert qu'à mieux expliquer un refus,
    /// elle ne doit jamais le provoquer.
    public static String occupant(Workspace workspace) {
        try {
            Path fichier = fichierDe(workspace);
            return Files.isRegularFile(fichier) ? Files.readString(fichier, StandardCharsets.UTF_8) : "";
        } catch (IOException illisible) {
            return "";
        }
    }

    /// `true` tant que ce verrou est tenu par ce processus.
    public boolean detenu() {
        return DETENUS.contains(fichier);
    }

    /// Relâche le verrou, **sauf** pour un jeton qui réutilisait la détention d'un autre : fermer une
    /// opération exclusive lancée depuis l'IHM ne doit pas rendre le workspace que l'IHM tient.
    @Override
    public void close() {
        if (!aRelacher) {
            return;
        }
        try (FileChannel aFermer = canal) {
            verrou.release();
            DETENUS.remove(fichier);
        } catch (IOException echec) {
            throw new DataAccessException("Verrou du workspace impossible à relâcher", echec);
        }
    }

    private static Path fichierDe(Workspace workspace) {
        return workspace.racine().resolve(NOM_FICHIER);
    }

    /// Écrit qui tient le verrou. Le contenu est **informatif** : un second processus le lira pour
    /// nommer l'occupant, mais c'est le verrou système qui décide.
    private static void inscrireLOccupant(FileChannel canal) throws IOException {
        String occupant = "processus " + ProcessHandle.current().pid() + ", depuis " + LocalDateTime.now();
        canal.truncate(0);
        canal.write(StandardCharsets.UTF_8.encode(occupant));
        canal.force(true);
    }
}

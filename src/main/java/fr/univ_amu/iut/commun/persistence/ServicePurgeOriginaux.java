package fr.univ_amu.iut.commun.persistence;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/// Service de **purge des originaux** (`<workspace>/<session>/bruts/`) : supprime les copies d'archive
/// des enregistrements d'origine pour **récupérer de l'espace disque**, sans jamais toucher aux
/// **séquences transformées** (`transformes/`) ni à la **base** — l'écoute, la validation et la
/// cohérence n'en dépendent pas.
///
/// N'utilise que le [Workspace] et `java.nio` (aucune dépendance à une feature) : il balaie la
/// **convention de dossiers** du workspace (chaque session = un dossier enfant contenant `bruts/`), ce
/// qui permet une purge **globale** sans interroger la base. La purge **par nuit** reçoit le dossier de
/// session déjà résolu par l'appelant (cf. `ServicePassage`, côté feature `passage`).
///
/// Suppression **best-effort** (un fichier verrouillé n'interrompt pas la purge des autres) et
/// **idempotente** (purger un `bruts/` déjà absent ne fait rien).
public class ServicePurgeOriginaux {

    private final Workspace workspace;

    @Inject
    public ServicePurgeOriginaux(Workspace workspace) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    /// Espace **récupérable** (octets) : somme des tailles de tous les `bruts/` du workspace.
    public long volumeRecuperable() {
        return brutsDesSessions().stream()
                .mapToLong(ServicePurgeOriginaux::tailleDossier)
                .sum();
    }

    /// Purge les originaux de **toutes** les sessions. Renvoie le nombre de sessions purgées et le volume
    /// libéré (octets).
    public ResultatPurge purgerTout() {
        List<Path> bruts = brutsDesSessions();
        long liberes =
                bruts.stream().mapToLong(ServicePurgeOriginaux::tailleDossier).sum();
        bruts.forEach(ServicePurgeOriginaux::supprimerRecursivement);
        return new ResultatPurge(bruts.size(), liberes);
    }

    /// Purge les originaux d'**une** session, désignée par son **dossier racine** (le `root_path`
    /// persisté, cf. [Workspace#dossierBrutsDeSession(Path)]). Renvoie le volume libéré (0 si `bruts/`
    /// était déjà absent).
    public long purgerSession(Path racineSession) {
        Objects.requireNonNull(racineSession, "racineSession");
        Path bruts = workspace.dossierBrutsDeSession(racineSession);
        long liberes = tailleDossier(bruts);
        supprimerRecursivement(bruts);
        return liberes;
    }

    /// Les dossiers `bruts/` **présents** des sessions du workspace (enfants directs de la racine). Liste
    /// vide si le workspace n'existe pas encore.
    private List<Path> brutsDesSessions() {
        if (!Files.isDirectory(workspace.racine())) {
            return List.of();
        }
        try (Stream<Path> enfants = Files.list(workspace.racine())) {
            return enfants.filter(Files::isDirectory)
                    .map(workspace::dossierBrutsDeSession)
                    .filter(Files::isDirectory)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de lister les sessions du workspace", e);
        }
    }

    /// Taille cumulée (octets) des fichiers réguliers sous `dossier` (0 s'il est absent). Best-effort.
    private static long tailleDossier(Path dossier) {
        if (!Files.exists(dossier)) {
            return 0L;
        }
        try (Stream<Path> chemins = Files.walk(dossier)) {
            return chemins.filter(Files::isRegularFile)
                    .mapToLong(ServicePurgeOriginaux::tailleSilencieuse)
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long tailleSilencieuse(Path fichier) {
        try {
            return Files.size(fichier);
        } catch (IOException ignore) {
            return 0L;
        }
    }

    /// Suppression récursive best-effort (fichiers d'abord, puis dossiers) ; sans échec si `dossier` est
    /// absent.
    private static void supprimerRecursivement(Path dossier) {
        if (!Files.exists(dossier)) {
            return;
        }
        try (Stream<Path> chemins = Files.walk(dossier)) {
            chemins.sorted(Comparator.reverseOrder()).forEach(ServicePurgeOriginaux::supprimerSilencieux);
        } catch (IOException ignore) {
            // best-effort : un balayage impossible n'interrompt pas le reste.
        }
    }

    private static void supprimerSilencieux(Path chemin) {
        try {
            Files.deleteIfExists(chemin);
        } catch (IOException ignore) {
            // best-effort
        }
    }

    /// Résultat d'une purge globale : nombre de sessions dont les originaux ont été supprimés et volume
    /// total libéré (octets).
    public record ResultatPurge(int nombreSessions, long octetsLiberes) {}
}

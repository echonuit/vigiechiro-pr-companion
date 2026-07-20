package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.EspaceDisque;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/// Opérations **disque** sur le sous-dossier `depot/` d'une session (où vivent les archives ZIP à
/// déposer) : localisation, listing, suppression, espace disponible. Extrait de [ServiceLot] (qui reste
/// dédié au **workflow** de dépôt) pour la cohésion / le seuil GodClass. Sans état, lecture/écriture
/// disque pure à partir du chemin de session déjà connu de l'appelant.
final class RepertoireDepot {

    /// Sous-dossier de la session où sont écrites les archives ZIP de dépôt (R22).
    private static final String SOUS_DOSSIER = "depot";

    /// Extension des archives (le dossier `depot/` ne contient que celles-ci).
    private static final String EXTENSION_ZIP = ".zip";

    /// Le sous-dossier `depot/` de la session (où écrire/lire les archives), résolu depuis sa racine R22.
    Path dossier(String cheminRacineSession) {
        return Path.of(cheminRacineSession).resolve(SOUS_DOSSIER);
    }

    /// Archives ZIP présentes dans `depot/` (liste vide si le dossier est absent), triées par nom, avec
    /// taille et nombre d'entrées (ouverture légère du ZIP) — pour réafficher les archives déjà générées.
    List<ArchiveDepot> lister(String cheminRacineSession) {
        if (cheminRacineSession == null) {
            return List.of();
        }
        Path depot = dossier(cheminRacineSession);
        if (!Files.isDirectory(depot)) {
            return List.of();
        }
        try (Stream<Path> fichiers = Files.list(depot)) {
            return fichiers.filter(RepertoireDepot::estArchiveZip)
                    .sorted()
                    .map(RepertoireDepot::decrireArchive)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du dossier de dépôt impossible : " + depot, e);
        }
    }

    /// Supprime les archives ZIP de `depot/` et renvoie le nombre d'octets libérés (`0` si dossier absent).
    long supprimer(String cheminRacineSession) {
        Path depot = dossier(cheminRacineSession);
        if (!Files.isDirectory(depot)) {
            return 0L;
        }
        try (Stream<Path> fichiers = Files.list(depot)) {
            long liberes = 0L;
            for (Path archive : fichiers.filter(RepertoireDepot::estArchiveZip).toList()) {
                liberes += tailleSilencieuse(archive);
                Files.deleteIfExists(archive);
            }
            return liberes;
        } catch (IOException e) {
            throw new UncheckedIOException("Suppression des archives de dépôt impossible : " + depot, e);
        }
    }

    /// Espace disque **disponible** (octets) sur le système de fichiers de la session, pour anticiper si
    /// les archives tiendront.
    ///
    /// **`0` veut dire « inconnu », pas « plein »** : chemin absent, dossier introuvable, système de
    /// fichiers illisible. Les appelants doivent le traiter comme une absence d'information et non
    /// comme un refus - `ChoixSourceDepot` et `AnticipationEspaceDisque` le font tous deux
    /// explicitement. C'est l'inverse de la convention de [EspaceDisque], qui laisse
    /// remonter l'échec pour que la génération **refuse** plutôt que de parier : là-bas on est sur le
    /// point d'écrire, ici on ne fait qu'anticiper.
    ///
    /// La lecture physique elle-même est déléguée à [EspaceDisque#reel] : c'est le seul
    /// endroit de l'application qui appelle `getUsableSpace`, et il n'y a aucune raison d'en avoir deux.
    long espaceDisponible(String cheminRacineSession) {
        if (cheminRacineSession == null) {
            return 0L;
        }
        try {
            Path racine = Path.of(cheminRacineSession);
            Path reference = Files.isDirectory(racine) ? racine : racine.getParent();
            return reference == null ? 0L : EspaceDisque.reel().disponibleOctets(reference);
        } catch (IOException e) {
            return 0L;
        }
    }

    /// Décrit une archive ZIP présente sur disque : chemin, numéro (extrait du nom `…-N.zip`), taille et
    /// nombre d'entrées.
    private static ArchiveDepot decrireArchive(Path zip) {
        int nombreFichiers;
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            nombreFichiers = archive.size();
        } catch (IOException e) {
            nombreFichiers = 0;
        }
        return new ArchiveDepot(zip, numeroDepuisNom(zip), tailleSilencieuse(zip), nombreFichiers);
    }

    /// Numéro d'archive extrait du nom `<préfixe>-N.zip` (le préfixe R6 contient des tirets → dernier
    /// segment) ; `0` si le nom n'est pas conforme.
    private static int numeroDepuisNom(Path zip) {
        String nom = zip.getFileName().toString();
        int tiret = nom.lastIndexOf('-');
        if (tiret < 0 || !nom.endsWith(EXTENSION_ZIP)) {
            return 0;
        }
        try {
            return Integer.parseInt(nom.substring(tiret + 1, nom.length() - EXTENSION_ZIP.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean estArchiveZip(Path fichier) {
        return fichier.getFileName().toString().endsWith(EXTENSION_ZIP);
    }

    private static long tailleSilencieuse(Path fichier) {
        try {
            return Files.size(fichier);
        } catch (IOException e) {
            return 0L;
        }
    }
}

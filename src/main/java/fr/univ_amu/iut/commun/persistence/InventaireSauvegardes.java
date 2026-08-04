package fr.univ_amu.iut.commun.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// Ce que contient `sauvegardes/` : une entrée par sauvegarde, sa date, sa taille (#3197).
///
/// ## Pourquoi inventorier, alors qu'on ne purge pas
///
/// L'application écrit un **filet complet à chaque migration** de schéma, et rien ne les supprime
/// jamais. C'est délibéré et assumé : ils sont le filet de l'utilisateur, l'application n'a pas à
/// décider à sa place quand il n'en a plus besoin (ADR 0048). Mais elle lui demandait un ménage
/// **qu'elle ne rendait visible nulle part** : aucun écran ne disait combien il y en avait ni ce qu'ils
/// pesaient, et la restauration passait par un sélecteur de fichiers où l'on navigue à l'aveugle.
///
/// La consommation croît avec la taille de la base **multipliée par** le nombre de migrations : sur une
/// base de plusieurs centaines de mégaoctets et une application qui a vécu, cela se compte en
/// gigaoctets, sur le volume qui héberge aussi la base de travail.
///
/// Cette classe **observe**, elle ne décide pas : elle ne supprime rien et ne conseille rien. La
/// suppression existe, mais **à la demande** (`supprimer-sauvegarde`).
///
/// ## Deux natures, et un total qui ne peut ignorer ni l'une ni l'autre
///
/// Les sauvegardes de base et les filets de migration sont des **fichiers** `.db` ; les sauvegardes
/// complètes sont des **dossiers** (`base/` + `sessions/`, cf.
/// [ServiceSauvegarde#sauvegarderComplet(Path)]), et ce sont elles qui portent l'audio, donc le
/// volume. Un inventaire qui ne verrait que les fichiers mentirait précisément là où le chiffre
/// compte.
public final class InventaireSauvegardes {

    /// Préfixe des sauvegardes écrites par [ServiceSauvegarde#sauvegarder(Path)].
    private static final String PREFIXE_SAUVEGARDE = "vigiechiro-sauvegarde-";

    /// Préfixe des sauvegardes complètes (base + audio), qui sont des dossiers.
    private static final String PREFIXE_COMPLETE = "vigiechiro-sauvegarde-complete-";

    /// Préfixe des filets posés avant une migration de schéma (`MigrationSchema`).
    private static final String PREFIXE_FILET = "vigiechiro-avant-migration-";

    private InventaireSauvegardes() {}

    /// Ce qu'une entrée de `sauvegardes/` est, du point de vue de l'utilisateur qui doit choisir.
    public enum Nature {
        /// Sauvegarde de la base seule, demandée par l'utilisateur.
        BASE,
        /// Sauvegarde complète (base + audio des sessions), demandée par l'utilisateur.
        COMPLETE,
        /// Filet posé **par l'application** avant une migration de schéma : celui que personne n'a
        /// demandé, et donc celui qu'on oublie.
        FILET_MIGRATION
    }

    /// Une sauvegarde présente sur le disque.
    ///
    /// @param nom nom du fichier ou du dossier, tel qu'il apparaît dans `sauvegardes/`
    /// @param date dernière modification, seule date dont on dispose pour un filet de migration (son
    ///     nom ne porte qu'un numéro de version)
    /// @param octets taille du fichier, ou taille **du contenu** pour une sauvegarde complète
    /// @param nature ce qui l'a écrite
    public record Entree(String nom, Instant date, long octets, Nature nature) {}

    /// Inventorie `dossier`, de la plus récente à la plus ancienne.
    ///
    /// Un dossier absent rend une liste vide : c'est l'état d'une installation qui n'a jamais migré ni
    /// sauvegardé, pas une anomalie. Ce que l'application n'a pas écrit là (notes, archives déposées
    /// par l'utilisateur) est ignoré : l'inventaire répond de ce que le produit a produit.
    public static List<Entree> lire(Path dossier) {
        if (dossier == null || !Files.isDirectory(dossier)) {
            return List.of();
        }
        List<Entree> entrees = new ArrayList<>();
        try (Stream<Path> contenu = Files.list(dossier)) {
            for (Path chemin : contenu.toList()) {
                natureDe(chemin).ifPresent(nature -> entrees.add(entree(chemin, nature)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture impossible du dossier de sauvegardes " + dossier, e);
        }
        entrees.sort(Comparator.comparing(Entree::date).reversed());
        return List.copyOf(entrees);
    }

    /// Somme des tailles : le chiffre qui manquait, et le seul qui rende la question actionnable.
    public static long total(List<Entree> entrees) {
        return entrees.stream().mapToLong(Entree::octets).sum();
    }

    private static Optional<Nature> natureDe(Path chemin) {
        String nom = chemin.getFileName().toString();
        if (Files.isDirectory(chemin)) {
            return nom.startsWith(PREFIXE_COMPLETE) ? Optional.of(Nature.COMPLETE) : Optional.empty();
        }
        if (nom.startsWith(PREFIXE_FILET)) {
            return Optional.of(Nature.FILET_MIGRATION);
        }
        // Après le filet et la complète : « vigiechiro-sauvegarde-complete-… » commence aussi par le
        // préfixe des sauvegardes de base, l'ordre des tests n'est donc pas indifférent.
        return nom.startsWith(PREFIXE_SAUVEGARDE) ? Optional.of(Nature.BASE) : Optional.empty();
    }

    private static Entree entree(Path chemin, Nature nature) {
        try {
            long octets = nature == Nature.COMPLETE ? tailleDuContenu(chemin) : Files.size(chemin);
            return new Entree(
                    chemin.getFileName().toString(),
                    Files.getLastModifiedTime(chemin).toInstant(),
                    octets,
                    nature);
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture impossible de la sauvegarde " + chemin, e);
        }
    }

    private static long tailleDuContenu(Path dossier) throws IOException {
        try (Stream<Path> arborescence = Files.walk(dossier)) {
            return arborescence
                    .filter(Files::isRegularFile)
                    .mapToLong(InventaireSauvegardes::tailleOuZero)
                    .sum();
        }
    }

    /// Un fichier qui disparaît pendant le parcours ne fait pas échouer l'inventaire : il compte pour
    /// zéro. Observer ne doit jamais être plus fragile que ce qu'on observe.
    private static long tailleOuZero(Path fichier) {
        try {
            return Files.size(fichier);
        } catch (IOException illisible) {
            return 0;
        }
    }
}

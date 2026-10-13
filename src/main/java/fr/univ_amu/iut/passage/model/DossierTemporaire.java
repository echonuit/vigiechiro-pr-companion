package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.persistence.ArborescenceFichiers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/// Dossier temporaire de **régénération** des tranches, partagé par les deux voies « bruts » (#1653) : la
/// réactivation d'un passage réel ([ReactivationDepuisBruts]) comme l'hydratation d'un passage reconstruit
/// ([HydratationDepuisBruts]) régénèrent dans un temporaire, rebranchent ce qui est vérifié, puis
/// l'effacent - **un temporaire par brut**, effacé aussitôt, pour ne pas doubler transitoirement
/// l'occupation disque que l'archivage cherchait justement à libérer.
///
/// **Sous l'espace de travail, pas dans `java.io.tmpdir`** (#6174) : ce dossier est souvent un *tmpfs*,
/// et ce qui s'y écrit est de la mémoire vive, que la JVM ne voit pas. L'hydratation régénère plusieurs
/// bruts à la fois, et une nuit de huit bruts de 20 min y a pris 7031 Mo au poste. `ExtracteurZip`
/// extrait une archive au même endroit pour la même raison.
public final class DossierTemporaire {

    private static final Logger LOG = Logger.getLogger(DossierTemporaire.class.getName());

    /// Préfixe du temporaire de la voie bruts, suivi de l'identifiant de l'original.
    static final String PREFIXE_REGENERATION = "vc-regen-";

    /// Préfixe du temporaire de l'hydratation.
    static final String PREFIXE_HYDRATATION = "vc-hydrate-";

    /// Les préfixes que portent les temporaires des deux voies, et que le filet reconnaît.
    private static final List<String> PREFIXES = List.of(PREFIXE_REGENERATION, PREFIXE_HYDRATATION);

    private DossierTemporaire() {}

    /// Crée sous `base` un dossier préfixé (le préfixe rend un éventuel reliquat identifiable), et `base`
    /// elle-même si elle manque.
    static Path creer(Path base, String prefixe) {
        try {
            Files.createDirectories(base);
            return Files.createTempDirectory(base, prefixe);
        } catch (IOException e) {
            throw new UncheckedIOException("Dossier temporaire impossible sous " + base + " (" + prefixe + ")", e);
        }
    }

    /// Efface le dossier et son contenu **au mieux** : un reliquat n'est pas une raison de faire échouer
    /// une réactivation par ailleurs réussie. [#nettoyerResiduels] le reprend au démarrage suivant.
    static void supprimer(Path dossier) {
        ArborescenceFichiers.effacerAuMieux(dossier);
    }

    /// Retire, **au mieux**, les dossiers de régénération (`vc-regen-*`, `vc-hydrate-*`) restés sous
    /// `base` après une réactivation interrompue. Appelé au démarrage, comme le filet des extractions
    /// d'archive : sous l'espace de travail, un reliquat occupe le disque jusqu'à ce qu'on le retire,
    /// là où `/tmp` se vidait au redémarrage (#6174). Une `base` absente ne lève pas.
    public static void nettoyerResiduels(Path base) {
        if (base == null || !Files.isDirectory(base)) {
            return;
        }
        try (Stream<Path> entrees = Files.list(base)) {
            entrees.filter(Files::isDirectory)
                    .filter(dossier -> PREFIXES.stream()
                            .anyMatch(
                                    prefixe -> dossier.getFileName().toString().startsWith(prefixe)))
                    .forEach(ArborescenceFichiers::effacerAuMieux);
        } catch (IOException echec) {
            // Au mieux : un balayage incomplet ne fait pas échouer le démarrage.
            LOG.log(Level.FINE, echec, () -> "Balayage des temporaires de régénération incomplet sous " + base);
        }
    }
}

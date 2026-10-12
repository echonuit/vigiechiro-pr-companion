package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.persistence.ArborescenceFichiers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
final class DossierTemporaire {

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

    /// Efface le dossier et son contenu **au mieux** : un reliquat dans le dossier temporaire du système
    /// n'est pas une raison de faire échouer une réactivation par ailleurs réussie.
    static void supprimer(Path dossier) {
        ArborescenceFichiers.effacerAuMieux(dossier);
    }
}

package fr.univ_amu.iut.commun.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/// Les deux gestes de disque qu'[ArborescenceFichiers] ne peut pas faire échouer sur commande.
///
/// ## Pourquoi un port, et pourquoi ces deux-là seulement
///
/// Même raison que [fr.univ_amu.iut.commun.model.TailleFichier] (#3627) : une panne qui ne se
/// **fabrique pas de façon portable** ne se teste pas sans couture. La matrice trois plateformes de
/// #3525 l'a montré au premier passage - sept tests d'`ArborescenceFichiersTest` échouaient sous
/// Windows, non pas sur un défaut, mais parce que leurs **fixtures** ne fabriquaient rien :
///
/// - `File.setReadable(false)` rend `false` sous Windows : on n'y rend pas un dossier illisible ainsi ;
/// - `File.setWritable(false)` n'empêche pas la suppression du contenu d'un dossier.
///
/// Les tests le disaient en **échouant** plutôt qu'en passant, ce qui est le bon sens d'échec - mais
/// ils ne prouvaient rien hors POSIX, sur la plateforme qui a le plus de façons de refuser un accès.
///
/// ⚠️ Deux gestes, pas une façade du système de fichiers. Ce qui entre ici est ce dont on a **besoin
/// de fabriquer l'échec** : le parcours, dont l'`UncheckedIOException` traverse les `catch` (#3632), et
/// la suppression d'une entrée, sur laquelle repose la distinction entre les deux contrats d'effacement
/// (#3574). Le reste continue d'appeler `Files` directement.
public interface GestesFichiers {

    /// Le flux des chemins sous `racine`, `racine` comprise.
    ///
    /// ⚠️ Son échec de parcours arrive **pendant l'itération**, enveloppé dans une
    /// `UncheckedIOException` - c'est le contrat de `Files.walk`, et c'est le piège de #3632. Un double
    /// qui veut l'éprouver doit donc lever **à la consommation**, pas à la construction du flux.
    Stream<Path> parcourir(Path racine) throws IOException;

    /// Retire une entrée, sans échouer si elle a déjà disparu.
    void supprimer(Path chemin) throws IOException;

    /// Les vrais gestes, sur le vrai disque.
    static GestesFichiers reels() {
        return new GestesFichiers() {
            @Override
            public Stream<Path> parcourir(Path racine) throws IOException {
                return Files.walk(racine);
            }

            @Override
            public void supprimer(Path chemin) throws IOException {
                Files.deleteIfExists(chemin);
            }
        };
    }
}

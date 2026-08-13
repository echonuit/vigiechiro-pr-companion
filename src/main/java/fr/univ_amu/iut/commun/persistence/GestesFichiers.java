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
/// ⚠️ Trois gestes, pas une façade du système de fichiers. Ce qui entre ici est ce dont on a **besoin
/// de fabriquer l'échec** : le parcours, dont l'`UncheckedIOException` traverse les `catch` (#3632) ;
/// la suppression d'une entrée, sur laquelle repose la distinction entre les deux contrats d'effacement
/// (#3574) ; et le listage d'un dossier, dont l'échec est ce que la pesée doit rapporter sans s'arrêter
/// (#3634). Le reste continue d'appeler `Files` directement.
public interface GestesFichiers {

    /// Le flux des chemins sous `racine`, `racine` comprise.
    ///
    /// ⚠️ Son échec de parcours arrive **pendant l'itération**, enveloppé dans une
    /// `UncheckedIOException` - c'est le contrat de `Files.walk`, et c'est le piège de #3632. Un double
    /// qui veut l'éprouver doit donc lever **à la consommation**, pas à la construction du flux.
    default Stream<Path> parcourir(Path racine) throws IOException {
        return Files.walk(racine);
    }

    /// Retire une entrée, sans échouer si elle a déjà disparu.
    default void supprimer(Path chemin) throws IOException {
        Files.deleteIfExists(chemin);
    }

    /// Les entrées directes d'un dossier, sans descendre.
    ///
    /// ⚠️ Distinct de [#parcourir] : c'est celui-ci qui échoue quand un dossier **ne se laisse pas
    /// lister**, et cette panne-là ne s'annonce pas pendant l'itération mais à l'ouverture. La pesée
    /// s'en sert pour continuer et rapporter (#3634) ; le parcours récursif, lui, s'arrête.
    default Stream<Path> lister(Path dossier) throws IOException {
        return Files.list(dossier);
    }

    /// Les vrais gestes, sur le vrai disque.
    ///
    /// ⚠️ Les trois méthodes portent leur implémentation réelle **par défaut**, et un double n'écrase
    /// que celle dont il veut fabriquer l'échec. Sans cela, ajouter un geste à cette interface ferait
    /// rougir tous les doubles existants pour une raison qui ne les concerne pas - c'est arrivé dès le
    /// troisième, et la friction se paie à chaque ajout suivant.
    static GestesFichiers reels() {
        return new GestesFichiers() {};
    }
}

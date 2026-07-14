package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/// Ce que le **dossier désigné par l'utilisateur** contient, vu par leurs **noms de fichiers** (#1302,
/// #1406). Extrait de [ServiceReactivationPassage], qui devenait une classe à tout faire.
///
/// L'exploration est **récursive** : on peut désigner la racine d'une sauvegarde, un disque externe, ou
/// une **carte SD entière contenant plusieurs nuits**. Les fichiers qui ne correspondent à aucun nom
/// connu de la session sont simplement **ignorés** - ils ne sont ni lus, ni copiés, ni touchés.
///
/// **Un nom peut désigner plusieurs fichiers.** Une sauvegarde réelle en contient : une copie
/// interrompue à côté de la bonne, deux sauvegardes empilées, la même carte copiée deux fois. On les
/// garde donc **tous**, dans un ordre **stable** (par chemin), et c'est l'appelant qui les confronte
/// l'un après l'autre au contenu attendu. Ne retenir que « le premier rencontré » ferait dépendre le
/// résultat de l'ordre dans lequel le système de fichiers rend ses entrées : ce n'est pas une base pour
/// décider, et cela reviendrait à refuser une séquence qui était pourtant là, sur un tirage au sort.
final class CandidatsReactivation {

    private final Map<String, List<Path>> parNom;

    private CandidatsReactivation(Map<String, List<Path>> parNom) {
        this.parNom = parNom;
    }

    /// Indexe (récursivement) les fichiers du dossier par nom simple.
    static CandidatsReactivation dans(Path dossier) {
        Objects.requireNonNull(dossier, "dossier");
        Map<String, List<Path>> index = new HashMap<>();
        try (Stream<Path> fichiers = Files.walk(dossier)) {
            fichiers.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(fichier -> index.computeIfAbsent(
                                    fichier.getFileName().toString(), nom -> new ArrayList<>())
                            .add(fichier));
        } catch (IOException e) {
            throw new UncheckedIOException("Exploration impossible du dossier " + dossier, e);
        }
        return new CandidatsReactivation(index);
    }

    /// Tous les fichiers portant ce nom (vide si aucun).
    List<Path> pour(String nomFichier) {
        return parNom.getOrDefault(nomFichier, List.of());
    }

    /// Tous les fichiers qui pourraient être **ce brut**, sous l'un des deux noms sous lesquels un
    /// utilisateur le garde : son **nom R6** (copie du dossier `bruts/` de l'espace de travail) ou son
    /// **nom d'enregistreur** non préfixé (copie de la carte SD).
    ///
    /// Le nom d'enregistreur s'obtient en **retirant le préfixe de la session**, et non en coupant « après
    /// le dernier tiret » : un enregistreur dont le nom de fichier contient lui-même un tiret aurait été
    /// cherché sous un nom tronqué, donc **jamais trouvé** - alors qu'il était là.
    List<Path> brutsDe(EnregistrementOriginal original, Optional<Prefixe> prefixe) {
        List<Path> trouves = new ArrayList<>(pour(original.nomFichier()));
        prefixe.map(Prefixe::prefixeFichier)
                .filter(debut -> original.nomFichier().startsWith(debut))
                .map(debut -> original.nomFichier().substring(debut.length()))
                .map(this::pour)
                .ifPresent(trouves::addAll);
        return trouves;
    }
}

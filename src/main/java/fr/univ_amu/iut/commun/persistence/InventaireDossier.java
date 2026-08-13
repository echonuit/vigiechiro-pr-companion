package fr.univ_amu.iut.commun.persistence;

import fr.univ_amu.iut.commun.model.Empreintes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/// Ce que contient une arborescence, en trois chiffres : combien de fichiers, combien d'octets, et
/// une **empreinte de l'inventaire** (#2726).
///
/// L'empreinte est le SHA-256 de la liste canonique `chemin relatif` + `taille`, triée. Elle attrape
/// un fichier manquant, un fichier en trop, un renommage et une troncature, **sans lire un seul
/// octet** du contenu. C'est le compromis retenu pour la sauvegarde complète : hacher le contenu de
/// plusieurs gigaoctets d'audio doublerait le temps de la sauvegarde et celui de la restauration,
/// pour n'attraper en plus que la corruption silencieuse à taille égale. Le socle a déjà mieux pour
/// ce cas-là, `original_recording.sha256`, qui vit en base.
///
/// Les chemins sont normalisés en séparateurs `/` : une sauvegarde écrite sous Windows et relue
/// sous Linux doit donner la même empreinte, sinon la vérification accuserait le système au lieu du
/// contenu.
///
/// @param fichiers nombre de fichiers ordinaires (les dossiers ne comptent pas)
/// @param octets somme de leurs tailles
/// @param empreinte SHA-256 hexadécimal de l'inventaire
record InventaireDossier(int fichiers, long octets, String empreinte) {

    /// Parcourt `racine` et en dresse l'inventaire.
    static InventaireDossier de(Path racine) throws IOException {
        List<String> lignes = new ArrayList<>();
        int fichiers = 0;
        long octets = 0;
        try (Stream<Path> arbre = Files.walk(racine)) {
            for (Path chemin : (Iterable<Path>) arbre::iterator) {
                if (!Files.isRegularFile(chemin)) {
                    continue;
                }
                long taille = Files.size(chemin);
                fichiers++;
                octets += taille;
                lignes.add(relatifNormalise(racine, chemin) + "\t" + taille);
            }
        } catch (UncheckedIOException parcours) {
            // ⚠️ `Files.walk` n'annonce pas l'échec de parcours en `IOException` : il l'enveloppe dans
            // une `UncheckedIOException` levée pendant l'itération, qui n'hérite pas d'`IOException` et
            // traverserait donc la signature déclarée - le diagnostic de l'appelant ne s'appliquerait
            // jamais (#3632). On la ramène au type annoncé.
            throw parcours.getCause();
        }
        lignes.sort(String::compareTo);
        String canonique = String.join("\n", lignes);
        return new InventaireDossier(
                fichiers, octets, Empreintes.sha256Hex(canonique.getBytes(StandardCharsets.UTF_8)));
    }

    private static String relatifNormalise(Path racine, Path chemin) {
        return racine.relativize(chemin).toString().replace('\\', '/');
    }
}

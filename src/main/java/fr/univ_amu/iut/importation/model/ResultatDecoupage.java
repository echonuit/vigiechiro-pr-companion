package fr.univ_amu.iut.importation.model;

import java.nio.file.Path;

/// Issue du découpage **d'un** original (import résilient #155) : soit la [TransformationOriginal]
/// réussie, soit un **rejet** avec sa raison. Permet à l'import de poursuivre sur les autres fichiers au
/// lieu d'échouer en bloc, et d'alimenter le rapport d'import.
///
/// @param original chemin de l'original concerné
/// @param transformation résultat de la transformation, ou `null` si l'original a été rejeté
/// @param erreur raison du rejet, ou `null` si la transformation a réussi
record ResultatDecoupage(Path original, TransformationOriginal transformation, String erreur) {

    boolean reussi() {
        return transformation != null;
    }

    String nomFichier() {
        return original.getFileName().toString();
    }
}

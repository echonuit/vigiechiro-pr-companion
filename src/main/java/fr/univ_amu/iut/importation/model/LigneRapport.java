package fr.univ_amu.iut.importation.model;

import java.util.Objects;

/// Une ligne du **rapport d'import** (#155) : le sort d'un fichier de la source.
///
/// @param nomFichier nom du fichier concerné
/// @param statut disposition ([StatutImportFichier])
/// @param detail précision lisible (nombre de séquences produites, raison du rejet…), jamais `null`
public record LigneRapport(String nomFichier, StatutImportFichier statut, String detail) {

    public LigneRapport {
        Objects.requireNonNull(nomFichier, "nomFichier");
        Objects.requireNonNull(statut, "statut");
        detail = detail == null ? "" : detail;
    }
}

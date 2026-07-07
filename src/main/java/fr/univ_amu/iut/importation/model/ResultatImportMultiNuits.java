package fr.univ_amu.iut.importation.model;

import java.util.List;

/// Compte rendu d'un import **découpé par nuit** : un [ResultatImport] par passage créé (une nuit
/// incluse = un passage). Une nuit unique donne une liste à un élément.
///
/// @param parNuit les résultats d'import, dans l'ordre des nuits (dates croissantes)
public record ResultatImportMultiNuits(List<ResultatImport> parNuit) {

    public ResultatImportMultiNuits {
        parNuit = List.copyOf(parNuit);
    }

    /// Nombre de passages créés.
    public int nombrePassages() {
        return parNuit.size();
    }

    /// Nombre total de séquences produites sur l'ensemble des nuits.
    public int nombreSequencesTotal() {
        return parNuit.stream().mapToInt(ResultatImport::nombreSequences).sum();
    }
}

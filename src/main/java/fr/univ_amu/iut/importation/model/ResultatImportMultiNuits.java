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

    /// Volumes **cumulés** sur toutes les nuits (#2358) : chaque nuit lit sa part de la carte et écrit sa
    /// propre session. Rendre ceux de la seule première nuit sous-estimerait l'import de tout le reste,
    /// et c'est justement sur un import multi-nuits que le volume écrit surprend.
    public VolumesImport volumes() {
        return parNuit.stream().map(ResultatImport::volumes).reduce(VolumesImport.AUCUN, VolumesImport::plus);
    }

    /// Premier passage créé (nuit la plus ancienne), ou `null` si la liste est vide.
    public ResultatImport premier() {
        return parNuit.isEmpty() ? null : parNuit.getFirst();
    }

    /// Fichiers **rejetés** (#155) de **toutes** les nuits, formatés « nom : raison », pour M-Import.
    public List<String> rejetsFormates() {
        return parNuit.stream()
                .flatMap(resultat -> resultat.rapport().rejetsFormates().stream())
                .toList();
    }
}

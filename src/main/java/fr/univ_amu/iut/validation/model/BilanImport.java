package fr.univ_amu.iut.validation.model;

/// Bilan d'un import Tadarida **tolérant** (#audio) : ce qui a été réellement importé et ce qui a été
/// écarté. Un CSV Tadarida réel référence souvent des segments dont l'audio n'a pas été conservé et des
/// taxons hors du référentiel semé : plutôt que de tout rejeter, l'import garde ce qu'il peut et rend
/// compte. La vue audio en fait un message de retour.
///
/// @param resultats jeu de résultats d'identification créé
/// @param importees nombre d'observations insérées (séquence audio présente et taxon renseigné)
/// @param ignorees nombre de lignes ignorées (séquence audio absente, ou ligne sans taxon)
/// @param taxonsHorsReferentiel nombre de taxons inconnus auto-enregistrés en souches
/// @param validationsPreservees nombre de validations observateur (correction, référence, commentaire) de
///     l'ancien jeu **réattachées** aux nouvelles observations lors d'une ré-importation (0 hors réimport)
/// @param validationsPerdues nombre de validations observateur de l'ancien jeu qui n'ont **pas** retrouvé
///     d'observation correspondante dans le nouveau CSV, donc définitivement perdues (0 hors réimport)
/// @param observationsAvecEchange nombre d'observations sur lesquelles un **échange avec le validateur**
///     (#1417) a été rapatrié. Toujours 0 hors import Vigie-Chiro : ni le CSV Tadarida ni la carte SD ne
///     portent de fil. Sert à **annoncer** ces messages au lieu de les laisser découvrir par hasard (#1867)
public record BilanImport(
        ResultatsIdentification resultats,
        int importees,
        int ignorees,
        int taxonsHorsReferentiel,
        int validationsPreservees,
        int validationsPerdues,
        int observationsAvecEchange) {

    /// Constructeur d'un import **sans échange rapatrié** : le cas de tout import qui ne vient pas des
    /// `donnees` Vigie-Chiro (CSV Tadarida, carte SD), et de l'import Vigie-Chiro dont aucune observation
    /// ne porte de fil - le cas le plus courant.
    public BilanImport(
            ResultatsIdentification resultats,
            int importees,
            int ignorees,
            int taxonsHorsReferentiel,
            int validationsPreservees,
            int validationsPerdues) {
        this(resultats, importees, ignorees, taxonsHorsReferentiel, validationsPreservees, validationsPerdues, 0);
    }

    /// Constructeur d'un import **simple** (pas de réimport) : aucune validation à préserver ni perdre.
    public BilanImport(ResultatsIdentification resultats, int importees, int ignorees, int taxonsHorsReferentiel) {
        this(resultats, importees, ignorees, taxonsHorsReferentiel, 0, 0, 0);
    }

    /// Le même bilan, complété du nombre d'observations dont un **échange avec le validateur** vient
    /// d'être rapatrié. Le fil s'écrit **après** l'import (il ne peut pas voyager dans une
    /// [LigneObservation], cf. [FilsDiscussionVigieChiro]) : le bilan se complète donc là où il est su.
    public BilanImport avecEchanges(int observationsAvecEchange) {
        return new BilanImport(
                resultats,
                importees,
                ignorees,
                taxonsHorsReferentiel,
                validationsPreservees,
                validationsPerdues,
                observationsAvecEchange);
    }

    /// Identifiant du jeu de résultats créé (raccourci sur [#resultats()]).
    public Long idResultats() {
        return resultats.id();
    }
}

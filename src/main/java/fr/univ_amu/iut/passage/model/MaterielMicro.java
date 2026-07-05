package fr.univ_amu.iut.passage.model;

/// Matériel du micro déployé pour un passage (métadonnées demandées au dépôt VigieChiro), stocké dans
/// la table 1:1 `passage_equipment` ([fr.univ_amu.iut.passage.model.dao.MaterielMicroDao]).
///
/// Le **n° de série du détecteur** n'est pas ici : il vit déjà sur l'enregistreur du passage
/// ([Passage#idEnregistreur] → `recorder.serial_number`). Chaque grandeur matérielle est
/// **indépendamment optionnelle** (`null` = non renseignée) : un relevé partiel est normal.
///
/// @param idPassage passage concerné (clé naturelle 1:1, FK → `passage.id`)
/// @param positionMicro position de fixation (sol / canopée), ou `null`
/// @param hauteurMetres hauteur de fixation en mètres, ou `null`
/// @param typeMicro type de micro (texte libre, ex. modèle), ou `null`
public record MaterielMicro(Long idPassage, PositionMicro positionMicro, Double hauteurMetres, String typeMicro) {

    /// Matériel « vide » pour `idPassage` : aucune grandeur renseignée (utile comme valeur par défaut
    /// quand la table n'a pas de ligne pour ce passage).
    public static MaterielMicro vide(long idPassage) {
        return new MaterielMicro(idPassage, null, null, null);
    }

    /// `true` si aucune grandeur matérielle n'est renseignée (le n° de série, hors périmètre, n'entre
    /// pas en compte) : sert à décider de supprimer la ligne plutôt que d'y stocker un enregistrement
    /// entièrement vide.
    public boolean estVide() {
        return positionMicro == null && hauteurMetres == null && typeMicro == null;
    }
}

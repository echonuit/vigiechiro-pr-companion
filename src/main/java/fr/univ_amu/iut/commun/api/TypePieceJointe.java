package fr.univ_amu.iut.commun.api;

/// Filtre de type pour `GET /participations/{id}/pieces_jointes?<parametre>=true` : le backend
/// (`Scille/vigiechiro-api`, `participations.py:342-350`) sélectionne les fichiers rattachés par
/// catégorie. Chaque constante porte le **nom du paramètre** de requête attendu par le serveur.
///
/// - [#PROCESSING_EXTRA] : les sorties annexes du traitement, dont l'unique **CSV d'observations**
///   (`participation-<id>-observations.csv`) exploité par la reconstruction (#1565) ;
/// - [#WAV] : les enregistrements d'origine, pour le repli audio (#1244) ;
/// - [#TA] / [#TC] : les fichiers intermédiaires Tadarida ;
/// - [#PHOTOS] : les photos du site.
public enum TypePieceJointe {
    TA("ta"),
    TC("tc"),
    WAV("wav"),
    PHOTOS("photos"),
    PROCESSING_EXTRA("processing_extra");

    private final String parametre;

    TypePieceJointe(String parametre) {
        this.parametre = parametre;
    }

    /// Nom du paramètre de requête (`?<parametre>=true`) attendu par le backend pour ce filtre.
    String parametre() {
        return parametre;
    }
}

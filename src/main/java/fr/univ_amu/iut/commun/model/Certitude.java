package fr.univ_amu.iut.commun.model;

import java.util.Locale;

/// Certitude **déclarée manuellement** sur une détermination : domaine fermé à trois catégories, calé
/// sur les énumérations `observateur_probabilite` **et** `validateur_probabilite` de la plateforme
/// VigieChiro (jetons exacts `SUR | PROBABLE | POSSIBLE`, contrat #1203).
///
/// **Un seul type pour deux rôles**, parce que le serveur n'en connaît qu'un. C'est la même énumération
/// qui qualifie la correction de l'**observateur** (#1139, colonne `observer_certainty`) et le verdict du
/// **validateur** du MNHN (#1417, colonne `validator_certainty`). Le type s'est d'abord appelé
/// `CertitudeObservateur`, du nom de son premier usage ; il portait deux rôles depuis #1417, et son nom
/// mentait : une certitude n'appartient pas à celui qui la déclare, elle qualifie ce qu'il déclare.
///
/// Notion **distincte** de la probabilité numérique `[0,1]` (`prob_observer`), qui est la confiance
/// **Tadarida** recopiée à la validation (héritage du format `_Vu`) : aucune conversion de l'une vers
/// l'autre, la certitude est **vide par défaut** tant qu'elle n'a pas été saisie (miroir du site web, où
/// les listes sont vides tant qu'on n'a pas cliqué OK).
///
/// Persistée sous son [#name] ; la relecture est **tolérante** ([#depuisTexte] : valeur absente ou
/// inconnue → `null` = « non renseignée »), comme [fr.univ_amu.iut.passage.model.Vent].
public enum Certitude {
    SUR("Sûr"),
    PROBABLE("Probable"),
    POSSIBLE("Possible");

    private final String libelle;

    Certitude(String libelle) {
        this.libelle = libelle;
    }

    /// Libellé lisible pour l'IHM (ex. « Sûr »).
    public String libelle() {
        return libelle;
    }

    /// Jeton persisté et poussé à l'API (le [#name] : `SUR`, `PROBABLE`, `POSSIBLE`).
    public String jeton() {
        return name();
    }

    /// Lit une certitude depuis un texte stocké ou renvoyé par le serveur : `null` ou vide → `null`
    /// (non renseignée) ; sinon la constante correspondante (insensible à la casse), ou `null` si le
    /// texte ne correspond à aucune (tolérant : le serveur peut évoluer sans nous prévenir).
    public static Certitude depuisTexte(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        try {
            return valueOf(texte.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException inconnue) {
            return null;
        }
    }
}

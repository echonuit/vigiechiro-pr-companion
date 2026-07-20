package fr.univ_amu.iut.commun.model;

/// **La** gravité de ce que l'application a à dire, quelle que soit la surface qui le dit.
///
/// L'application en comptait **deux** échelles sans lien entre elles : celle-ci, née dans
/// `RetourOperation` côté `viewmodel`, et `SeveriteConstat` côté `audit.model`. Aucune conversion, aucun
/// renvoi documentaire, aucune mention de l'une dans l'autre (#2159).
///
/// La duplication n'était pas anodine. L'[ADR
/// 0038](../../../../../../../dev-docs/decisions/0038-l-echelle-de-severite-a-quatre-niveaux.md)
/// raconte l'ajout d'un niveau `AVERTISSEMENT` **manquant** - ce qui était vrai de l'échelle du
/// `viewmodel`, et faux de l'application : `SeveriteConstat` le portait depuis toujours. Une analyse
/// écrite depuis un seul des deux angles.
///
/// Elle vit donc en `commun.model`, la couche que **tout le monde** peut citer : un modèle qui qualifie
/// la gravité d'un constat n'a pas à dépendre d'un type de présentation, et une vue n'a pas à traduire
/// entre deux vocabulaires qui disent la même chose.
///
/// ## Ce que ce type n'est pas
///
/// Il ne remplace **aucun** état métier. `StatutWorkflow`, `StatutImportFichier`, `StatutObservation`,
/// `StatutDepotUnite`, `StatutControle`, `PreCheckNuit.Feu` disent *ce qu'une chose est* ; celui-ci dit
/// *à quel point c'est grave*. Les vues **projettent** les premiers vers celui-ci, et c'est le bon
/// endroit pour cette traduction - une pastille de nuit incomplète n'a pas la même signification qu'un
/// avertissement d'opération, même si les deux se peignent en ambre.
///
/// ## L'ordre porte la sémantique
///
/// ⚠ [fr.univ_amu.iut.commun.viewmodel.CompteRendu#severite()] prend le **maximum** par `ordinal()` : la
/// position des constantes détermine quel constat qualifie un compte rendu entier. Réordonner cette
/// énumération changerait ce comportement **sans qu'aucun test métier ne rougisse**. `SeveriteTest`
/// l'épingle explicitement, et c'est la raison d'être de ce test.
public enum Severite {

    /// L'opération a abouti, et il y a de quoi s'en féliciter.
    SUCCES,

    /// Un fait à connaître, sans gravité : un guidage, un dénombrement, une action refusée sans échec.
    INFO,

    /// L'opération a abouti, mais quelque chose mérite l'attention : une nuit déjà importée qu'on
    /// réimporte quand même, un dossier mélangeant deux enregistreurs, un numéro de passage déjà pris.
    ///
    /// Ce niveau a manqué longtemps au `viewmodel`, et son absence n'a pas produit des messages mal
    /// classés : elle a fait **sortir du type** les avertissements, qui sont redevenus des chaînes libres
    /// portant un « ⚠ » (#2050). Quand un type ne sait pas exprimer un cas, le cas ne s'y plie pas.
    AVERTISSEMENT,

    /// L'opération a échoué.
    ERREUR
}

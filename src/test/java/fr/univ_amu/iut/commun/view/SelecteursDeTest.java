package fr.univ_amu.iut.commun.view;

/// Des fabriques de désignation pour les tests, **sans base de données**.
///
/// La première version de ce fichier montait une vraie base sur un `@TempDir`, parce que la fabrique
/// demandait alors `Reglages`. Ce couplage a fait rougir 293 tests dans 27 classes, et il a été
/// retiré ([PreferenceDesignation] en raconte l'histoire).
///
/// Ce qu'il en reste ici est le signe que la conception est juste : une fabrique de test tient
/// désormais en une expression, et n'a plus rien à monter.
public final class SelecteursDeTest {

    private SelecteursDeTest() {}

    /// La fabrique d'une installation neuve : le dialogue du système, qui est le défaut du produit.
    public static Selecteurs auDefaut() {
        return new Selecteurs(() -> false);
    }

    /// La fabrique d'une installation où l'utilisateur a demandé le dialogue de l'application.
    public static Selecteurs avecLeDialogueDeLApplication() {
        return new Selecteurs(() -> true);
    }
}

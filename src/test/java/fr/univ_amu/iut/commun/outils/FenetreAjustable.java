package fr.univ_amu.iut.commun.outils;

import fr.univ_amu.iut.commun.view.Habillage;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/// Ouvre la fenêtre d'un cas TestFX en la laissant **ajustable** pour la classe suivante du fork.
///
/// Le Stage primaire est partagé entre les classes d'un même fork surefire. Celle qui l'a laissé
/// dimensionné le fige, et un `setScene` sur un stage déjà dimensionné ne le redimensionne pas : la
/// scène demandée à 980 x 980 est relue à la taille héritée, et tout ce qui vit sous cette hauteur
/// tombe hors du rectangle que le clic exige. Le défaut est revenu quatre fois - #1940, #1967,
/// #3452, #4130 - et trois fois sur la même classe témoin qui n'y est pour rien.
///
/// La taille est demandée à la **mise en page**, jamais à la fenêtre : la racine porte la taille
/// voulue en taille préférée, et [Stage#sizeToScene()] fait suivre la fenêtre. Une fenêtre ajustée
/// par `sizeToScene` reste ajustable, là où une fenêtre dimensionnée à la main ne l'est plus.
///
/// L'appel à `sizeToScene` a lieu **après** `show()` : sans surface native, la fenêtre n'a rien à
/// redimensionner et l'appel ne fait rien.
///
/// Voir l'ADR 4475 pour la décision, et `recette.FenetreDuBanc` qui délègue ici en ajoutant
/// l'habillage que ses clips exigent.
public final class FenetreAjustable {

    private FenetreAjustable() {}

    /// Pose une scène nue à la taille voulue, **sans afficher** : l'appelant garde la main pour ce
    /// qui doit précéder l'affichage, typiquement ouvrir son écran.
    public static void poser(Stage fenetre, Parent racine, double largeur, double hauteur) {
        prefererLaTaille(racine, largeur, hauteur);
        fenetre.setScene(new Scene(racine, largeur, hauteur));
    }

    /// Pose une scène habillée, pour les cas dont le rendu est jugé ou filmé.
    public static void poserHabillee(Stage fenetre, Parent racine, double largeur, double hauteur) {
        prefererLaTaille(racine, largeur, hauteur);
        fenetre.setScene(Habillage.scene(racine, largeur, hauteur));
    }

    /// Affiche la fenêtre et la met à la taille de sa scène, en la laissant ajustable.
    public static void afficher(Stage fenetre) {
        fenetre.show();
        fenetre.sizeToScene();
    }

    /// La taille voulue portée par la mise en page, comme `ConventionsDEcritureTest` le prescrit.
    ///
    /// **Ce que la mesure n'a pas pu montrer** : sur une scène posée AVEC ses dimensions, retirer
    /// cet appel ne change aucun verdict, ni avant ni après une passe de mise en page - c'est la
    /// `Scene` qui gouverne alors sa propre taille. L'appel est gardé parce que la règle écrite le
    /// prescrit et que `recette.FenetreDuBanc` le fait depuis #4130, non parce qu'un cas l'a
    /// démontré ici. Il compterait pour une scène posée sans dimensions, forme qu'aucune des
    /// classes reprises par #4582 n'emploie.
    private static void prefererLaTaille(Parent racine, double largeur, double hauteur) {
        if (racine instanceof Region region) {
            region.setPrefSize(largeur, hauteur);
        }
    }
}

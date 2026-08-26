package fr.univ_amu.iut.recette;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import javafx.scene.Parent;
import javafx.stage.Stage;

/// Ouvre la fenêtre d'un scénario filmé **à la taille voulue, sans figer le Stage du harnais**.
///
/// ## Le piège, et pourquoi il revient
///
/// Un scénario perceptif a besoin d'une fenêtre assez grande : c'est elle que le banc filme, et un
/// cadre trop étroit repousse hors de l'image ce que le cas fait juger. Le réflexe est d'écrire
/// `stage.setWidth(...)`, et c'est précisément ce qu'il ne faut pas faire.
///
/// `setWidth` / `setHeight` font passer un Stage en dimensionnement **explicite** : il cesse
/// **définitivement** de s'ajuster aux scènes qu'on lui pose ensuite. Sur une fenêtre que l'on jette,
/// aucune conséquence. Mais le Stage du harnais TestFX est **partagé par toutes les classes d'un même
/// fork** : figé par une classe, il fait échouer les suivantes sur des noeuds « invisibles », très
/// loin de la cause et seulement selon l'ordre d'exécution.
///
/// Ce défaut est revenu **quatre fois** : #1940, puis #1967 qui prédisait son retour, puis #3452, puis
/// ici en écrivant les scénarios de la connexion. Il s'est signalé les trois dernières fois sur la même
/// classe témoin, `LotDepotConnecteViewTest`, qui n'y est pour rien.
///
/// ## Ce que fait cette classe à la place
///
/// La taille est demandée à la **mise en page**, jamais à la fenêtre : la racine porte la taille
/// voulue en taille préférée, et [Stage#sizeToScene()] fait suivre la fenêtre. C'est la voie que
/// `Modales.suivreLaCroissance` emprunte déjà, et que `ModalesTest` garde depuis #1940 : une fenêtre
/// ajustée par `sizeToScene` reste **ajustable**.
///
/// L'appel à `sizeToScene` a lieu **après** `show()`, et c'est nécessaire : sans surface native, la
/// fenêtre n'a rien à redimensionner et l'appel ne fait rien.
public final class FenetreDuBanc {

    private FenetreDuBanc() {}

    /// Pose la scène du scénario à la taille voulue, **sans afficher** : le scénario garde la main pour
    /// ce qui doit précéder l'affichage - typiquement ouvrir son écran, pour que le clip ne commence pas
    /// sur l'accueil (#4126).
    public static void poser(Stage stage, Parent racine, double largeur, double hauteur) {
        FenetreAjustable.poserHabillee(stage, racine, largeur, hauteur);
    }

    /// Affiche la fenêtre et la met à la taille de sa scène, en la laissant **ajustable** pour les
    /// classes de test qui hériteront de ce Stage.
    public static void afficher(Stage stage) {
        FenetreAjustable.afficher(stage);
    }
}

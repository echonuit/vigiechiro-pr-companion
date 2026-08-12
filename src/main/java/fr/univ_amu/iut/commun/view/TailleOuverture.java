package fr.univ_amu.iut.commun.view;

/// Taille à laquelle la fenêtre principale s'ouvre (#3452).
///
/// ## Pourquoi une décision, et pas deux constantes
///
/// L'accueil réclame **plus de hauteur qu'un portable courant n'en offre** : mesuré, son contenu fait
/// 818 px, auxquels s'ajoutent l'en-tête, le fil d'Ariane et la barre de statut. Un écran de 1366x768 ne
/// peut pas l'afficher entier.
///
/// Ouvrir à la taille voulue sans regarder l'écran donnerait une fenêtre dont le bas passe **sous la
/// barre des tâches**, hors d'atteinte - et sur certains systèmes, une fenêtre qu'on ne peut plus
/// redimensionner à la souris. Mieux vaut un accueil qui défile qu'une fenêtre qu'on ne peut pas saisir.
///
/// La décision est donc : **la taille voulue, bornée par l'écran**. Elle est pure pour être éprouvée sans
/// serveur d'affichage - `Screen.getPrimary()` n'existe pas en rendu headless.
public record TailleOuverture(double largeur, double hauteur) {

    /// Largeur d'ouverture visée : l'accueil mesure 946 px de contenu, plus une marge confortable.
    public static final double LARGEUR_VOULUE = 1100;

    /// Hauteur d'ouverture visée : 818 px de contenu, plus le chrome (en-tête, fil, barre de statut).
    public static final double HAUTEUR_VOULUE = 900;

    /// En deçà, l'application n'est plus utilisable : les tables n'affichent plus de lignes et les
    /// modales débordent. La fenêtre reste redimensionnable, mais pas jusqu'à l'absurde.
    public static final double LARGEUR_MINIMALE = 900;

    public static final double HAUTEUR_MINIMALE = 600;

    /// La taille voulue, **bornée** par l'espace utile de l'écran (hors barre des tâches).
    ///
    /// Un écran non mesurable - largeur ou hauteur nulle, ce que rend un environnement sans affichage -
    /// laisse passer la taille voulue : borner par zéro ouvrirait une fenêtre invisible.
    public static TailleOuverture bornee(double ecranLargeur, double ecranHauteur) {
        return new TailleOuverture(
                ecranLargeur > 0 ? Math.min(LARGEUR_VOULUE, ecranLargeur) : LARGEUR_VOULUE,
                ecranHauteur > 0 ? Math.min(HAUTEUR_VOULUE, ecranHauteur) : HAUTEUR_VOULUE);
    }
}

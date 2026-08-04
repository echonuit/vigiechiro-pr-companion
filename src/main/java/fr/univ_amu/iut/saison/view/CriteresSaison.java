package fr.univ_amu.iut.saison.view;

import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.saison.model.LigneSaison;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/// Les deux filtres de « Ma saison » (#3103, chantier #3092).
///
/// ## Pourquoi cet écran n'a pas la barre à puces des quatre autres
///
/// « Ma saison » filtre par deux `ComboBox` fixes : l'année et la campagne. Ce n'est **pas** un oubli
/// d'uniformisation. Une saison **est** une année et une campagne : ces deux contrôles disent la
/// structure du travail plutôt que de la subir, et les garder toujours visibles donne la lecture
/// immédiate « je suis sur telle saison » qu'une puce parmi d'autres ferait perdre. Cet écran est un
/// tableau de bord, pas une table exploratoire.
///
/// Ce qui manquait était ailleurs : sur un jeu conséquent, les deux questions qu'on pose à la liste
/// n'avaient pas de réponse directe. « Où en est ce lieu précis ? » et « qu'est-ce qu'il me reste à
/// faire ? ». D'où ces deux filtres-ci, qui **s'ajoutent** aux `ComboBox` sans rien remplacer.
public final class CriteresSaison {

    /// Clé du filtre de **recherche** de lieu.
    public static final String RECHERCHE = "recherche";

    /// Clé du filtre **« Reste à faire »**.
    public static final String RESTE_A_FAIRE = "reste_a_faire";

    private CriteresSaison() {}

    /// **Recherche de lieu** : le numéro de carré et le code du point, les deux colonnes d'identité de
    /// la table. Insensible à la casse et aux accents.
    ///
    /// Les autres colonnes ne s'y prêtent pas : ce sont des états de passage et une phrase d'action, que
    /// la case « Reste à faire » interroge mieux qu'une recherche libre.
    public static BiPredicate<LigneSaison, String> rechercheTexte() {
        return CriteresSaison::correspond;
    }

    /// **« Reste à faire »** : les points qui ne sont **pas** à jour.
    ///
    /// C'est la raison d'être de l'écran : un point à jour porte un « reste à faire » vide, ce que dit
    /// déjà [LigneSaison#aJour]. Le filtre ne redéfinit donc pas la règle, il la réutilise - sans quoi
    /// la colonne et le filtre pourraient un jour diverger.
    public static Predicate<LigneSaison> resteAFaire() {
        return ligne -> !ligne.aJour();
    }

    private static boolean correspond(LigneSaison ligne, String texte) {
        String aiguille = NormalisationTexte.normaliser(texte);
        return contient(ligne.numeroCarre(), aiguille) || contient(ligne.codePoint(), aiguille);
    }

    private static boolean contient(String champ, String aiguille) {
        return champ != null && NormalisationTexte.normaliser(champ).contains(aiguille);
    }
}

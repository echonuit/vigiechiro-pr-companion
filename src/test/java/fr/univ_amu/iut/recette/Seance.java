package fr.univ_amu.iut.recette;

/// Ce qu'un test peut savoir de la séance qui l'exécute (#3791).
///
/// ## Pourquoi un scénario a besoin de le savoir
///
/// Un scénario perceptif se joue **pour être regardé**. Il lui faut donc des respirations : un temps
/// d'arrêt avant le geste, pour que l'écran au repos serve de référence, et un après, pour laisser
/// voir si quelque chose se replace. Sans elles, le geste occupe une ou deux images et personne ne
/// peut trancher.
///
/// Ces respirations n'ont aucun sens hors d'une séance filmée : elles ne feraient qu'allonger
/// chaque build de quelques secondes pour rien. Le scénario les demande donc à cette classe plutôt
/// que de les prendre toujours.
///
/// ## Deux bancs, deux signaux, une seule question
///
/// Il y a **deux** manières de filmer ce dépôt, et elles ne se reconnaissent pas au même signe.
///
/// Le banc bash pose `recette.reperes`, la propriété qui dit où consigner les repères, et le profil
/// `recette-filmee` est seul à la poser. Le banc en Java pur (`recette/film/`) n'a aucun repère à
/// consigner, puisqu'il écrit un fichier par test : il pose `recette.film`, et rien d'autre.
///
/// Ne reconnaître que le premier signal a coûté un tournage entier. Les neuf clips perceptifs
/// tournés sous Windows par le banc Java n'ont **respiré nulle part** : chaque geste tenait en une
/// ou deux images, et le relecteur ne pouvait juger que ce qu'il savait déjà chercher. Rien ne
/// rougissait, et les clips existaient - c'est bien pourquoi le défaut se voit à l'oeil et pas au
/// verdict.
///
/// La question posée ici est « filme-t-on ? ». Elle n'a qu'une réponse, quel que soit le banc qui la
/// pose.
public final class Seance {

    /// Le signal du banc en Java pur. Sa présence suffit, comme pour l'extension elle-même.
    private static final String PROPRIETE_FILM = "recette.film";

    private Seance() {}

    /// Vrai lorsque la séance en cours est filmée, par l'un OU l'autre des deux bancs.
    public static boolean filmee() {
        return !System.getProperty(JournalDesReperes.PROPRIETE, "").isBlank()
                || System.getProperty(PROPRIETE_FILM) != null;
    }
}

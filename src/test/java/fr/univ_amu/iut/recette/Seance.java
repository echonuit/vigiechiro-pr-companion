package fr.univ_amu.iut.recette;

/// Ce qu'un test peut savoir de la séance qui l'exécute (#3791).
///
/// Un scénario perceptif se joue **pour être regardé**, et il lui faut des respirations : un temps
/// d'arrêt avant le geste, pour que l'écran au repos serve de référence, et un après, pour laisser voir
/// si quelque chose se replace. Sans elles le geste occupe une ou deux images et personne ne peut
/// trancher. Hors d'une séance filmée elles n'ont aucun sens et allongeraient chaque build pour rien :
/// le scénario les demande donc plutôt que de les prendre toujours.
///
/// **Deux bancs, deux signaux, une seule question.** Le banc bash pose `recette.reperes`, la propriété
/// qui dit où consigner les repères, et le profil `recette-filmee` est seul à la poser. Le banc en Java
/// pur n'a aucun repère à consigner, écrivant un fichier par test : il pose `recette.film`, et rien
/// d'autre.
///
/// Ne reconnaître que le premier signal a coûté un tournage entier : les neuf clips perceptifs tournés
/// sous Windows par le banc Java n'ont **respiré nulle part**, chaque geste tenant en une ou deux
/// images. Rien ne rougissait et les clips existaient, ce qui est bien pourquoi le défaut se voit à
/// l'œil et pas au verdict. La question posée ici est « filme-t-on ? », et elle n'a qu'une réponse.
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

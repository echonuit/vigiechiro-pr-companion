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
/// ⚠️ Ces respirations n'ont aucun sens hors d'une séance filmée : elles ne feraient qu'allonger
/// chaque build de quelques secondes pour rien. Le scénario les demande donc à cette classe plutôt
/// que de les prendre toujours.
///
/// Le signal employé est la propriété qui dit **où consigner les repères** : elle n'est posée que par
/// le profil `recette-filmee`. Un test qui ralentit quand on le filme, et seulement là.
public final class Seance {

    private Seance() {}

    /// Vrai lorsque la séance en cours est filmée.
    public static boolean filmee() {
        return !System.getProperty(JournalDesReperes.PROPRIETE, "").isBlank();
    }
}

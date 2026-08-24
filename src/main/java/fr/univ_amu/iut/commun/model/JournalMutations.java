package fr.univ_amu.iut.commun.model;

/// Port par lequel un service annonce qu'il vient de **valider une mutation structurelle** des
/// données (#3541) : un écran suit ainsi la **base** au lieu de la navigation. Un port et non la
/// propriété observable, parce que `model` ne dépend pas de JavaFX ; l'implémentation vit dans
/// `commun.viewmodel`, comme [Horloge].
///
/// **Structurelle** : qui peut changer l'inventaire affiché - sites, points, passages, observations
/// - et non toute écriture, un verdict n'y changeant rien (#3542). **Règle d'appel, la seule : tu
/// écris, tu signales, après validation.** La rafale se règle chez le lecteur (`RevisionDonnees`),
/// l'émetteur ignorant s'il sert un geste ou deux cent cinquante ; appelable depuis tout fil.
@FunctionalInterface
public interface JournalMutations {

    /// Annonce qu'une mutation structurelle vient d'être validée.
    void mutationStructurelleValidee();
}

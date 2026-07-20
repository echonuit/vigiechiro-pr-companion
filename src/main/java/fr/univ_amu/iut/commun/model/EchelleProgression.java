package fr.univ_amu.iut.commun.model;

/// **Où une exécution parallèle se situe dans un pipeline plus long** (#2039).
///
/// [ExecutionParallele] ne savait au départ traiter qu'une opération **autonome** : la fraction émise
/// valait `k / nombre d'éléments`, et le dernier élément terminé amenait donc toujours la barre à
/// 100 %. C'est juste quand l'opération *est* tout le travail.
///
/// L'import ne fonctionne pas ainsi. Copier les originaux puis les transformer, ce sont **deux phases**
/// d'un même geste : la copie doit s'arrêter à mi-course, et la transformation reprendre là où elle
/// s'est arrêtée. Le dénominateur n'est donc plus la liste traitée, mais le pipeline entier — et il
/// vient de l'appelant, seul à le connaître.
///
/// C'est ce découplage qui manquait pour que le découpage et la préparation des originaux puissent
/// revenir sur le socle plutôt que d'en réécrire une copie chacun.
///
/// @param etapesDejaFaites ce que les phases précédentes ont accompli, `0` pour la première
/// @param totalEtapes le dénominateur du pipeline entier, jamais nul
public record EchelleProgression(int etapesDejaFaites, int totalEtapes) {

    public EchelleProgression {
        if (totalEtapes < 1) {
            throw new IllegalArgumentException("totalEtapes doit valoir au moins 1, reçu " + totalEtapes);
        }
        if (etapesDejaFaites < 0) {
            throw new IllegalArgumentException("etapesDejaFaites ne peut être négatif, reçu " + etapesDejaFaites);
        }
        if (etapesDejaFaites > totalEtapes) {
            throw new IllegalArgumentException(
                    "etapesDejaFaites (" + etapesDejaFaites + ") dépasse totalEtapes (" + totalEtapes + ")");
        }
    }

    /// L'opération **est** tout le travail : elle part de zéro et son dernier élément terminé vaut
    /// 100 %. C'est le cas de la réactivation (#1779) et de la synchro (#1814).
    public static EchelleProgression autonome(int nombreDElements) {
        return new EchelleProgression(0, Math.max(1, nombreDElements));
    }

    /// La fraction globale une fois `faits` éléments terminés, dans `[0, 1]`.
    public double fraction(int faits) {
        return (double) (etapesDejaFaites + faits) / totalEtapes;
    }
}

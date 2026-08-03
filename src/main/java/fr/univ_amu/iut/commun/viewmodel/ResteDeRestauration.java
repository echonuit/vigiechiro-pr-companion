package fr.univ_amu.iut.commun.viewmodel;

import java.util.List;

/// Ce qu'une restauration de filtres (`GestionnaireFiltres.restaurer`) n'a **pas** su replacer (#3093).
///
/// Vit dans `viewmodel` et non dans `view` bien qu'il soit produit par le socle de filtres : le sens de
/// dépendance établi est **view vers viewmodel** (`GestionnaireFiltres` pilote déjà [Filtres]), et c'est
/// un ViewModel qui en tire un message.
///
/// Les deux causes sont distinguées parce qu'elles ne disent pas la même chose à qui lit le bandeau, et
/// n'appellent pas la même réaction :
///
/// - **une valeur perdue** (#3056) : le critère existe, mais la valeur mémorisée ne correspond plus à
///   rien d'offert. Le libellé a changé (« Z1 » devenu « 640380 · Z1 » en #2995) ou la valeur est absente
///   du jeu courant (une espèce qu'on n'a pas contactée cette fois-ci). C'est passager et lié aux
///   données ;
/// - **un critère inconnu** : le catalogue de l'écran d'arrivée n'a pas ce critère du tout. C'est
///   structurel, et cela arrive surtout au **transport** d'un écran à l'autre (#476), où Sons &
///   validation offre dix critères et l'analyse cinq.
///
/// Dans les deux cas, la conséquence est la même et c'est elle qui rend le silence inacceptable : comme
/// « rien de coché n'écarte rien », ce qui n'a pas été replacé ne filtre pas, et l'écran montre **plus**
/// que ce qu'il annonce.
///
/// Les critères inconnus sont rendus par leur **clé** et non par un libellé : l'écran d'arrivée ne
/// connaît précisément pas ce critère, donc n'a pas son intitulé. Leur donner un nom lisible partagé
/// relève du vocabulaire commun de clés (#3096).
///
/// @param valeursPerdues valeurs mémorisées qu'aucun critère n'a su replacer, dans l'ordre rencontré
/// @param criteresInconnus clés de critères absentes du catalogue de l'écran, dans l'ordre rencontré
public record ResteDeRestauration(List<String> valeursPerdues, List<String> criteresInconnus) {

    /// Restauration complète : rien n'a été laissé de côté.
    public static final ResteDeRestauration RIEN = new ResteDeRestauration(List.of(), List.of());

    public ResteDeRestauration {
        valeursPerdues = List.copyOf(valeursPerdues);
        criteresInconnus = List.copyOf(criteresInconnus);
    }

    /// Vrai quand la restauration a tout replacé : il n'y a alors **rien à signaler**, et le bandeau ne
    /// doit pas s'ouvrir pour dire que tout va bien.
    public boolean estVide() {
        return valeursPerdues.isEmpty() && criteresInconnus.isEmpty();
    }
}

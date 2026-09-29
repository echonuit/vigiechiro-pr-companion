package fr.univ_amu.iut.commun.outils;

import java.util.List;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

/// La POPULATION que les trois index partagent : tous les types du modèle, imbriqués compris.
///
/// Une classe à part parce que les trois index la lisent, et qu'une population recopiée trois fois
/// divergerait sans que rien ne le dise. C'est la leçon de #5564, où `getAllTypes()` écartait les
/// imbriqués d'un seul des deux index : la limite déclarée était fausse de 1 226 méthodes.
final class TypesDuModele {

    private TypesDuModele() {}

    /// TOUS les types, IMBRIQUÉS COMPRIS, et c'est la différence avec `getAllTypes()`.
    ///
    /// `getAllTypes()` ne rend que les types de premier niveau : 2 139 contre 2 960, mesuré le
    /// 2026-09-28. La différence porte **564 types imbriqués nommés**, et parmi eux 22 interfaces -
    /// `EcritureAtomique.Attente`, `PresenceFichiers.Balayeur`, `TransportVigieChiro.CorpsAEnvoyer`
    /// et les autres. Une interface imbriquée est un contrat comme une autre ; l'écarter aurait fait
    /// répondre « personne ne l'implémente » à une question dont la réponse existe.
    static List<? extends CtType<?>> tous(CtModel modele) {
        return modele.getElements(new TypeFilter<CtType<?>>(CtType.class));
    }
}

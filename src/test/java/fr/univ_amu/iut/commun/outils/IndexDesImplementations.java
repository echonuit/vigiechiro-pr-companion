package fr.univ_amu.iut.commun.outils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;

/// `index-implementations.json` : pour chaque contrat du dépôt, les types qui le tiennent (#5564).
final class IndexDesImplementations {

    private IndexDesImplementations() {}

    /// Pour chaque contrat DÉCLARÉ DANS LE MODÈLE, les types qui l'implémentent ou l'étendent.
    ///
    /// « Déclaré dans le modèle » est le filtre qui rend cet index utile, et c'est le même esprit que
    /// celui de `appelantsHorsDuFichier` : sans lui, chaque `Comparable`, `Runnable` ou `List` du JDK
    /// entrerait avec ses porteurs, et la réponse à « qui implémente ce contrat » se lirait dans du
    /// bruit. La question posée porte sur les contrats DU DÉPÔT.
    ///
    /// Les interfaces ET les classes abstraites, parce que la question est la même : un contrat est
    /// ce qu'un autre type promet de tenir. Une classe concrète étendue y figure aussi, et c'est
    /// voulu - la hiérarchie est ce qu'on interroge, pas la seule abstraction.
    static Map<String, List<String>> implementationsParContrat(CtModel modele) {
        Map<String, List<String>> parContrat = new TreeMap<>();
        List<? extends CtType<?>> tous = TypesDuModele.tous(modele);
        for (CtType<?> type : tous) {
            if (type.isInterface() || type.getModifiers().contains(ModifierKind.ABSTRACT)) {
                parContrat.putIfAbsent(type.getQualifiedName(), new ArrayList<>());
            }
        }
        for (CtType<?> type : tous) {
            for (String contrat : contratsDe(type)) {
                List<String> porteurs = parContrat.get(contrat);
                if (porteurs != null && !porteurs.contains(type.getQualifiedName())) {
                    porteurs.add(type.getQualifiedName());
                }
            }
        }
        parContrat.values().forEach(v -> v.sort(Comparator.naturalOrder()));
        return parContrat;
    }

    /// Les contrats qu'un type déclare tenir : ses interfaces directes et sa superclasse, SANS filtrer.
    ///
    /// Le filtre `getDeclaration() != null` a été écrit ici puis **retiré**, et la raison vaut d'être
    /// dite : la garde du dictionnaire fait déjà ce travail, et les deux en série rendaient la
    /// propriété intenable par mutation. Ni retirer ce filtre, ni retirer la garde ne faisait rougir
    /// le cas qui surveille les contrats hors du modèle - il aurait fallu muter les deux à la fois,
    /// et un témoin qui exige deux mutations simultanées ne prouve rien d'une seule.
    ///
    /// Une seule couche décide donc, et c'est `parContrat` : n'est un contrat que ce qui y a été
    /// pré-inscrit, c'est-à-dire une interface ou une classe abstraite DU MODÈLE.
    private static List<String> contratsDe(CtType<?> type) {
        List<String> contrats = new ArrayList<>();
        for (CtTypeReference<?> vue : type.getSuperInterfaces()) {
            contrats.add(vue.getQualifiedName());
        }
        CtTypeReference<?> mere = type.getSuperclass();
        if (mere != null) {
            contrats.add(mere.getQualifiedName());
        }
        return contrats;
    }
}

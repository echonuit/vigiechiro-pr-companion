package fr.univ_amu.iut.commun.outils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

/// `index-champs.json` : pour chaque champ, les types qui le LISENT hors de sa classe (#5565).
final class IndexDesChamps {

    private IndexDesChamps() {}

    /// Pour chaque champ, les types qui le LISENT hors de sa classe.
    ///
    /// **Les LECTURES seules**, limite déclarée et non oubli : confondre `CtFieldRead` et
    /// `CtFieldWrite` ferait passer un champ qu'un constructeur écrit et que personne ne lit pour
    /// « utilisé ailleurs », le faux négatif que cet index existe pour éviter. Les 4 347 écritures du
    /// corpus ne sont pas indexées.
    ///
    /// Un champ sans lecteur externe n'est PAS mort : ils sont 92 % du corpus. Les chiffres et leur
    /// lecture sont dans `scripts/_commun/champs.py`, que cet index alimente.
    static Map<String, List<String>> lecteursHorsDeLaClasse(CtModel modele) {
        Map<String, List<String>> parChamp = new TreeMap<>();
        for (CtType<?> type : TypesDuModele.tous(modele)) {
            for (CtField<?> champ : type.getFields()) {
                parChamp.putIfAbsent(cle(type, champ.getSimpleName()), new ArrayList<>());
            }
        }
        for (CtFieldRead<?> lecture : modele.getElements(new TypeFilter<CtFieldRead<?>>(CtFieldRead.class))) {
            if (lecture.getVariable() == null || lecture.getVariable().getDeclaration() == null) {
                continue;
            }
            CtType<?> porteur = lecture.getVariable().getDeclaration().getParent(CtType.class);
            CtType<?> lecteur = lecture.getParent(CtType.class);
            if (porteur == null || lecteur == null || porteur == lecteur) {
                continue;
            }
            List<String> vus = parChamp.get(cle(porteur, lecture.getVariable().getSimpleName()));
            if (vus != null && !vus.contains(lecteur.getQualifiedName())) {
                vus.add(lecteur.getQualifiedName());
            }
        }
        parChamp.values().forEach(v -> v.sort(Comparator.naturalOrder()));
        return parChamp;
    }

    /// `fr.X.Y#champ` : la même forme que la signature d'une méthode, sans les parenthèses - un champ
    /// n'a pas de surcharge, donc rien à désambiguïser au-delà de son porteur qualifié.
    private static String cle(CtType<?> porteur, String champ) {
        return porteur.getQualifiedName() + "#" + champ;
    }
}

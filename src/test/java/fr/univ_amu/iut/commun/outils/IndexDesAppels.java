package fr.univ_amu.iut.commun.outils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;

/// `index-appels.json` : pour chaque méthode, ses appelants RÉSOLUS hors de son fichier (#5473).
final class IndexDesAppels {

    private IndexDesAppels() {}

    /// Pour chaque méthode, ses appelants vivant dans un AUTRE fichier. Le filtre est ce qui
    /// distingue cet index : l'intra-fichier est déjà vu par `arbre.py` et par PMD.
    static Map<String, List<String>> appelantsHorsDuFichier(CtModel modele) {
        Map<String, List<String>> par_appelee = new TreeMap<>();
        for (CtType<?> type : TypesDuModele.tous(modele)) {
            for (CtMethod<?> methode : type.getMethods()) {
                par_appelee.putIfAbsent(signature(methode), new ArrayList<>());
            }
        }
        for (CtInvocation<?> appel : modele.getElements(new AppelsSeuls())) {
            CtExecutableReference<?> vise = appel.getExecutable();
            if (vise == null || vise.getDeclaration() == null) {
                continue;
            }
            CtExecutable<?> declaree = vise.getDeclaration();
            String appelee = signature(declaree);
            String appelant = ouVit(appel);
            if (appelant == null || appelant.equals(ouVit(declaree)) || !par_appelee.containsKey(appelee)) {
                continue;
            }
            List<String> vus = par_appelee.get(appelee);
            if (!vus.contains(appelant)) {
                vus.add(appelant);
            }
        }
        par_appelee.values().forEach(v -> v.sort(Comparator.naturalOrder()));
        return par_appelee;
    }

    /// `fr.X.Y#methode(int,String)` : le type QUALIFIÉ écarte les 1 155 homonymes du corpus, les
    /// paramètres écartent les 436 surcharges que `Type#nom` fusionnait.
    private static String signature(CtExecutable<?> executable) {
        String porteur = executable.getParent(CtType.class) == null
                ? "?"
                : executable.getParent(CtType.class).getQualifiedName();
        StringBuilder parametres = new StringBuilder();
        for (int i = 0; i < executable.getParameters().size(); i++) {
            String type = executable.getParameters().get(i).getType() == null
                    ? "?"
                    : executable.getParameters().get(i).getType().getSimpleName();
            parametres.append(i == 0 ? "" : ",").append(type);
        }
        return porteur + "#" + executable.getSimpleName() + "(" + parametres + ")";
    }

    private static String ouVit(spoon.reflect.declaration.CtElement element) {
        CtType<?> type = element instanceof CtType<?> t ? t : element.getParent(CtType.class);
        return type == null ? null : type.getQualifiedName();
    }

    /// Ne retenir que les invocations, sans filtrer sur autre chose : le tri se fait plus haut, où
    /// l'on sait ce qui a été résolu.
    private static final class AppelsSeuls implements spoon.reflect.visitor.Filter<CtInvocation<?>> {
        @Override
        public boolean matches(CtInvocation<?> element) {
            return true;
        }
    }
}

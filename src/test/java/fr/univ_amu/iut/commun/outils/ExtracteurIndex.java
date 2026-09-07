package fr.univ_amu.iut.commun.outils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;

/// Produit `target/index-appels.json` : pour chaque méthode, ses appelants RÉSOLUS hors de son
/// fichier. Ni `arbre.py` ni PMD ne répondent à cette question (#5473).
///
/// Ce que l'index NE porte PAS : les commentaires. Spoon classe un `///` du JEP 467 en `INLINE` et
/// en dégrade le texte ; ils restent chez `arbre.py`.
///
/// Sa limite, à ne pas lire comme une couverture : les classes ANONYMES n'y sont qu'en partie, 458
/// méthodes sur 623. Aucune méthode de classe nommée ne manque.
public final class ExtracteurIndex {

    /// Les racines de PAQUET, non les racines de source : viser `src/main/java` fait échouer Spoon
    /// sur « Ambiguous package name », à cause de `module-info.java`.
    private static final List<String> RACINES = List.of("src/main/java/fr", "src/test/java/fr");

    /// Le niveau de compliance se POSE : Spoon le déduit du JDK courant, et son JDT refuse `-25`
    /// avec « Unrecognized option ». Le laisser deviner casse au prochain JDK du runner.
    private static final int COMPLIANCE = 21;

    private ExtracteurIndex() {}

    public static void main(String[] args) throws IOException {
        Path sortie = Path.of(args.length > 0 ? args[0] : "target/index-appels.json");
        Map<String, List<String>> appelants = appelantsHorsDuFichier(modele());
        Files.createDirectories(sortie.getParent());
        Files.writeString(sortie, enJson(appelants), StandardCharsets.UTF_8);
        long sansAppelant = appelants.values().stream().filter(List::isEmpty).count();
        System.out.println("index écrit : " + sortie
                + "  méthodes=" + appelants.size()
                + "  avec appelant externe=" + (appelants.size() - sansAppelant)
                + "  sans=" + sansAppelant);
    }

    /// Le modèle des deux arbres, bâti une fois. UN modèle par processus : deux dans une même JVM
    /// lèvent `UnsatisfiedLinkError` sur les natifs JavaFX.
    static CtModel modele() {
        Launcher lanceur = new Launcher();
        RACINES.forEach(lanceur::addInputResource);
        lanceur.getEnvironment().setNoClasspath(true);
        lanceur.getEnvironment().setCommentEnabled(false);
        lanceur.getEnvironment().setComplianceLevel(COMPLIANCE);
        lanceur.buildModel();
        return lanceur.getModel();
    }

    /// Pour chaque méthode, ses appelants vivant dans un AUTRE fichier. Le filtre est ce qui
    /// distingue cet index : l'intra-fichier est déjà vu par `arbre.py` et par PMD.
    static Map<String, List<String>> appelantsHorsDuFichier(CtModel modele) {
        Map<String, List<String>> par_appelee = new TreeMap<>();
        for (CtType<?> type : modele.getAllTypes()) {
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

    private static String enJson(Map<String, List<String>> appelants) {
        StringBuilder json = new StringBuilder("{\n");
        int reste = appelants.size();
        for (Map.Entry<String, List<String>> e : appelants.entrySet()) {
            json.append("  \"").append(echappe(e.getKey())).append("\": [");
            for (int i = 0; i < e.getValue().size(); i++) {
                json.append(i == 0 ? "" : ", ")
                        .append('"')
                        .append(echappe(e.getValue().get(i)))
                        .append('"');
            }
            json.append(--reste == 0 ? "]\n" : "],\n");
        }
        return json.append("}\n").toString();
    }

    private static String echappe(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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

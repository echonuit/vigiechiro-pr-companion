package fr.univ_amu.iut.commun.outils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/// Produit les index de `target/index-*.json`, en UN passage sur UN modele :
///
/// - `index-appels.json` : pour chaque methode, ses appelants RESOLUS hors de son fichier (#5473) ;
/// - `index-implementations.json` : pour chaque contrat du depot, les types qui le tiennent (#5564).
///
/// Ni `arbre.py` ni PMD ne repondent a ces questions : le premier lit un fichier a la fois, le second
/// ne juge que des regles de conception.
///
/// La population vient de `tousLesTypes`, non de `getAllTypes()`, et le detail de ce que ce choix a
/// corrige vit dans #5564 : la limite declaree ici etait fausse de 1 226 methodes, et le defaut etait
/// asymetrique - 154 types imbriques figuraient comme APPELANTS sans qu aucune de leurs methodes soit
/// une cle. La soustraction ferme desormais :
///
///     14 828 (premier niveau) + 1 226 (imbriques NOMMES) + 626 (ANONYMES) = 16 680
///
public final class ExtracteurIndex {

    /// Les racines de PAQUET, non les racines de source : viser `src/main/java` fait échouer Spoon
    /// sur « Ambiguous package name », à cause de `module-info.java`.
    private static final List<String> RACINES = List.of("src/main/java/fr", "src/test/java/fr");

    /// Le niveau de compliance se POSE : Spoon le déduit du JDK courant, et son JDT refuse `-25`
    /// avec « Unrecognized option ». Le laisser deviner casse au prochain JDK du runner.
    private static final int COMPLIANCE = 21;

    private ExtracteurIndex() {}

    /// `args[0]` est le DOSSIER de sortie, non un fichier : il y en a deux depuis #5564, et le
    /// pluriel `target/index-*.json` que #5464 annonçait les attendait. Rien ne passait d'argument
    /// quand ce sens a changé - ni le pas de CI, ni la recette du refus de `index.py` - donc aucun
    /// appelant n'a été rompu, et c'est écrit ici plutôt que supposé.
    ///
    /// Deux fichiers et non un objet à deux clés : le refus de `index.py` est déjà PAR fichier, et
    /// #5565 pourrait ne pas être produit à chaque demande si son coût l'interdit. Un fichier unique
    /// rendrait alors les deux indisponibles, ce que rien ne justifie.
    public static void main(String[] args) throws IOException {
        Path ou = Path.of(args.length > 0 ? args[0] : "target");
        Files.createDirectories(ou);

        // UN modèle pour les deux index. Deux appels à `modele()` lèveraient `UnsatisfiedLinkError`
        // sur les natifs JavaFX, ce que #5464 a mesuré ; l'extracteur est un processus, pas une
        // bibliothèque qu'on appelle deux fois.
        CtModel modele = modele();

        Map<String, List<String>> appelants = appelantsHorsDuFichier(modele);
        ecrire(ou.resolve("index-appels.json"), appelants);
        long sansAppelant = appelants.values().stream().filter(List::isEmpty).count();
        System.out.println("index écrit : " + ou.resolve("index-appels.json")
                + "  méthodes=" + appelants.size()
                + "  avec appelant externe=" + (appelants.size() - sansAppelant)
                + "  sans=" + sansAppelant);

        Map<String, List<String>> implementations = implementationsParContrat(modele);
        ecrire(ou.resolve("index-implementations.json"), implementations);
        long sansImplementation =
                implementations.values().stream().filter(List::isEmpty).count();
        System.out.println("index écrit : " + ou.resolve("index-implementations.json")
                + "  contrats=" + implementations.size()
                + "  implémentés=" + (implementations.size() - sansImplementation)
                + "  sans=" + sansImplementation);

        Map<String, List<String>> champs = lecteursHorsDeLaClasse(modele);
        ecrire(ou.resolve("index-champs.json"), champs);
        long sansLecteur = champs.values().stream().filter(List::isEmpty).count();
        System.out.println("index écrit : " + ou.resolve("index-champs.json")
                + "  champs=" + champs.size()
                + "  lus ailleurs=" + (champs.size() - sansLecteur)
                + "  sans=" + sansLecteur);
    }

    /// Écriture ATOMIQUE : un fichier temporaire, puis un renommage. C'est ce qui remplace l'argument
    /// du fichier unique - « jamais d'index à moitié écrit » - sans coupler les deux contenus. Un
    /// lecteur voit l'index entier ou pas de fichier, jamais un JSON tronqué.
    private static void ecrire(Path sortie, Map<String, List<String>> contenu) throws IOException {
        Path provisoire = sortie.resolveSibling(sortie.getFileName() + ".partiel");
        Files.writeString(provisoire, enJson(contenu), StandardCharsets.UTF_8);
        Files.move(provisoire, sortie, StandardCopyOption.REPLACE_EXISTING);
    }

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
        List<? extends CtType<?>> tous = tousLesTypes(modele);
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

    /// Pour chaque champ, les types qui le LISENT hors de sa classe.
    ///
    /// **Les LECTURES seules, et c'est une limite déclarée plutôt qu'un oubli.** Spoon distingue
    /// `CtFieldRead` de `CtFieldWrite`, et la question de #5464 est « ce champ est-il **lu** ailleurs ».
    /// Les confondre ferait passer un champ qu'un constructeur écrit et que personne ne lit pour
    /// « utilisé ailleurs », soit le faux négatif exact que cet index existe pour éviter. Les 4 347
    /// écritures du corpus ne sont donc pas indexées, et cet index ne répond pas à « qui écrit ce
    /// champ ».
    ///
    /// Mesure du 2026-09-29, et elle a démenti la crainte qui avait fait de ce lot le plus risqué des
    /// trois : les **38 580** accès résolus du corpus s'effondrent à **3 173 arêtes**, la plupart étant
    /// intra-classe et se dédoublonnant par type lecteur. L'index pèse 775 Ko contre 2,1 Mo pour celui
    /// des appels, et son calcul coûte 2,5 s sur un modèle déjà bâti.
    ///
    /// Ce qu'il ne dit PAS, et c'est l'ADR 5532 appliquée ici : **8 195 champs sur 8 933 n'ont aucun
    /// lecteur externe**, soit 92 %. C'est l'état normal d'un état privé, pas un défaut, et un garde
    /// bâti là-dessus signalerait presque tout le corpus.
    static Map<String, List<String>> lecteursHorsDeLaClasse(CtModel modele) {
        Map<String, List<String>> parChamp = new TreeMap<>();
        for (CtType<?> type : tousLesTypes(modele)) {
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

    /// TOUS les types, IMBRIQUÉS COMPRIS, et c'est la différence avec `getAllTypes()`.
    ///
    /// `getAllTypes()` ne rend que les types de premier niveau : 2 139 contre 2 960, mesuré le
    /// 2026-09-28. La différence porte **564 types imbriqués nommés**, et parmi eux 22 interfaces -
    /// `EcritureAtomique.Attente`, `PresenceFichiers.Balayeur`, `TransportVigieChiro.CorpsAEnvoyer`
    /// et les autres. Une interface imbriquée est un contrat comme une autre ; l'écarter aurait fait
    /// répondre « personne ne l'implémente » à une question dont la réponse existe.
    private static List<? extends CtType<?>> tousLesTypes(CtModel modele) {
        return modele.getElements(new TypeFilter<CtType<?>>(CtType.class));
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
        for (CtType<?> type : tousLesTypes(modele)) {
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

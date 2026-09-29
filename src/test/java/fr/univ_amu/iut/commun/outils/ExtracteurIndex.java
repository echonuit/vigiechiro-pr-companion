package fr.univ_amu.iut.commun.outils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import spoon.Launcher;
import spoon.reflect.CtModel;

/// Produit les index de `target/index-*.json`, en UN passage sur UN modele :
///
/// - `index-appels.json` : pour chaque methode, ses appelants RESOLUS hors de son fichier (#5473) ;
/// - `index-implementations.json` : pour chaque contrat du depot, les types qui le tiennent (#5564).
///
/// Ni `arbre.py` ni PMD ne repondent a ces questions : le premier lit un fichier a la fois, le second
/// ne juge que des regles de conception.
///
/// La population vient de `TypesDuModele.tous`, non de `getAllTypes()`, et le detail de ce que ce
/// choix a corrige vit dans #5564 : la limite declaree ici etait fausse de 1 226 methodes, et le
/// defaut etait asymetrique - 154 types imbriques figuraient comme APPELANTS sans qu aucune de leurs
/// methodes soit une cle. La soustraction fermait ainsi, AU 2026-09-28 :
///
///     14 828 (premier niveau) + 1 226 (imbriques NOMMES) + 626 (ANONYMES) = 16 680
///
/// La date n est pas un ornement. Ce total suit la population, et il a bouge de huit le 2026-09-29
/// quand #5565 a decoupe cette classe en quatre : une fermeture arithmetique sans date se lit comme
/// un invariant, et devient fausse au premier fichier ajoute sans que rien ne rougisse.
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

        Map<String, List<String>> appelants = IndexDesAppels.appelantsHorsDuFichier(modele);
        ecrire(ou.resolve("index-appels.json"), appelants);
        long sansAppelant = appelants.values().stream().filter(List::isEmpty).count();
        System.out.println("index écrit : " + ou.resolve("index-appels.json")
                + "  méthodes=" + appelants.size()
                + "  avec appelant externe=" + (appelants.size() - sansAppelant)
                + "  sans=" + sansAppelant);

        Map<String, List<String>> implementations = IndexDesImplementations.implementationsParContrat(modele);
        ecrire(ou.resolve("index-implementations.json"), implementations);
        long sansImplementation =
                implementations.values().stream().filter(List::isEmpty).count();
        System.out.println("index écrit : " + ou.resolve("index-implementations.json")
                + "  contrats=" + implementations.size()
                + "  implémentés=" + (implementations.size() - sansImplementation)
                + "  sans=" + sansImplementation);

        Map<String, List<String>> champs = IndexDesChamps.lecteursHorsDeLaClasse(modele);
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
}

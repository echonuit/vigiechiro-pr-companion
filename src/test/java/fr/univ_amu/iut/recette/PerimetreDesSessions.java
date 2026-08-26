package fr.univ_amu.iut.recette;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/// Dit sur quelles sessions le décompte de recette porte, et sur lesquelles il ne porte pas (#3884).
///
/// ## Le défaut que ceci corrige
///
/// [CorrespondanceRecetteTest] annonçait « 51 cas déclarés, 33 assertés, 15 non couverts ». La
/// phrase se lit comme un état de **la recette**. Elle ne portait que sur **deux sessions sur
/// douze** : les dix autres numérotent leurs cas autrement (`22.`, `23.` dans S6) et la regex n'en
/// voit aucun. Rien dans la sortie ne le disait.
///
/// Le chiffre a induit en erreur trois fois, dont une dans le bilan de #3667, qui affirmait « S1 est
/// couvert de bout en bout » alors que `S1-33` n'avait pas de test. **Un compte sans périmètre se
/// recopie**, et il vieillit sans prévenir.
///
/// ## Pourquoi une liste d'admises, et pas seulement un affichage
///
/// Afficher les sessions muettes aurait suffi à informer un lecteur attentif. Mais **une sortie ne
/// rougit pas** : rien n'aurait empêché une onzième session d'arriver muette, ni signalé qu'une
/// session convertie l'est enfin.
///
/// Le garde confronte donc deux lectures de la même chose - ce que le dossier rend, et ce que le
/// code admet - dans les **deux** sens :
///
/// | Situation | Verdict |
/// |---|---|
/// | session muette, et admise comme telle | rien à dire |
/// | session muette, **non admise** | rouge : elle n'est pas lue et rien ne le dit |
/// | session admise muette qui **rend des cas** | rouge : elle est lue, retirez-la de la liste |
/// | session admise qui **n'existe plus** | rouge : la liste a dérivé |
///
/// Les deux derniers sens sont ce qui distingue ce garde d'un inventaire. Sans eux, la liste des
/// admises deviendrait à son tour une prose qui dérive - c'est-à-dire #3885, reproduit à l'intérieur
/// du remède.
///
/// ## Ce que ce garde ne demande pas
///
/// Que les dix sessions soient converties. C'est un travail à part, session par session, avec des
/// renvois à ne pas casser. Le garde n'exige pas la couverture : **il interdit le silence.**
///
/// @param lues les sessions dont au moins un cas a été lu, associées à ce nombre
/// @param muettes les sessions dont aucun cas n'a été lu, admises ou non
/// @param silencesNonDeclares les muettes qu'aucune admission ne couvre
/// @param admissionsPerimees les admissions qui ne décrivent plus le dossier, parce que la session
///     rend des cas ou parce qu'elle n'existe plus
public record PerimetreDesSessions(
        SortedMap<String, Integer> lues,
        SortedSet<String> muettes,
        SortedSet<String> silencesNonDeclares,
        SortedSet<String> admissionsPerimees) {

    public PerimetreDesSessions {
        lues = Collections.unmodifiableSortedMap(new TreeMap<>(lues));
        muettes = Collections.unmodifiableSortedSet(new TreeSet<>(muettes));
        silencesNonDeclares = Collections.unmodifiableSortedSet(new TreeSet<>(silencesNonDeclares));
        admissionsPerimees = Collections.unmodifiableSortedSet(new TreeSet<>(admissionsPerimees));
    }

    /// Confronte ce que le dossier rend à ce que le code admet.
    ///
    /// `casParFichier` porte **toutes** les sessions balayées, y compris celles dont le compte
    /// est zéro. Ne lui passer que les sessions fructueuses rendrait les muettes invisibles, ce qui
    /// est précisément le défaut d'origine : le silence ne se déduit pas de ce qui parle.
    ///
    /// @param casParFichier pour chaque session balayée, le nombre de cas que la regex y a lus
    /// @param muettesAdmises les sessions dont le silence est connu et assumé
    /// @return le périmètre, et les deux façons dont il peut avoir dérivé
    public static PerimetreDesSessions analyser(Map<String, Integer> casParFichier, Set<String> muettesAdmises) {

        SortedMap<String, Integer> lues = new TreeMap<>();
        SortedSet<String> muettes = new TreeSet<>();

        casParFichier.forEach((fichier, cas) -> {
            if (cas > 0) {
                lues.put(fichier, cas);
            } else {
                muettes.add(fichier);
            }
        });

        SortedSet<String> silencesNonDeclares = new TreeSet<>(muettes);
        silencesNonDeclares.removeAll(muettesAdmises);

        SortedSet<String> admissionsPerimees = new TreeSet<>(muettesAdmises);
        admissionsPerimees.removeAll(muettes);

        return new PerimetreDesSessions(lues, muettes, silencesNonDeclares, admissionsPerimees);
    }

    /// Le nombre de cas lus, toutes sessions confondues.
    public int casLus() {
        return lues.values().stream().mapToInt(Integer::intValue).sum();
    }

    /// La phrase qui accompagne tout décompte, pour qu'il ne se lise jamais plus large qu'il ne
    /// porte.
    public String assiette() {
        return "%d session(s) lue(s) sur %d".formatted(lues.size(), lues.size() + muettes.size());
    }
}

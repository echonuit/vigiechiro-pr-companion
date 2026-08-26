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
/// [CorrespondanceRecetteTest] annonçait « 51 cas déclarés, 33 assertés, 15 non couverts », phrase qui
/// se lit comme un état de **la recette**. Elle ne portait que sur deux sessions sur douze, les dix
/// autres numérotant leurs cas autrement, et rien dans la sortie ne le disait. Le chiffre a induit en
/// erreur trois fois, dont le bilan de #3667 affirmant « S1 est couvert de bout en bout » quand `S1-33`
/// n'avait pas de test : **un compte sans périmètre se recopie**.
///
/// Afficher les sessions muettes aurait informé un lecteur attentif, mais **une sortie ne rougit pas**.
/// Le garde confronte donc deux lectures de la même chose, ce que le dossier rend et ce que le code
/// admet, dans les **deux** sens :
///
/// | Situation | Verdict |
/// |---|---|
/// | session muette, et admise comme telle | rien à dire |
/// | session muette, **non admise** | rouge : elle n'est pas lue et rien ne le dit |
/// | session admise muette qui **rend des cas** | rouge : elle est lue, retirez-la de la liste |
/// | session admise qui **n'existe plus** | rouge : la liste a dérivé |
///
/// Les deux derniers sens distinguent ce garde d'un inventaire : sans eux, la liste des admises
/// deviendrait à son tour une prose qui dérive, soit #3885 reproduit à l'intérieur du remède. Il n'exige
/// pas que les dix sessions soient converties, ni la couverture : **il interdit le silence.**
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

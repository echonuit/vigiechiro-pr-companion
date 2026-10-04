package fr.univ_amu.iut.sites.model;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Le code d'un nouveau point d'écoute : `Z` suivi du **premier numéro libre** du site (#5688).
///
/// C'est la règle du portail Vigie-Chiro pour un point libre. Les points systématiques, `A1` à `H2`,
/// nommés d'après la maille où ils tombent, relèvent de #5608 : ce calcul ne les propose jamais.
public final class CodePointLibre {

    private static final Pattern CODE_Z = Pattern.compile("Z(\\d+)");

    private CodePointLibre() {}

    /// Le premier `Z<n>` (n à partir de 1) qu'aucun code du site ne porte, casse ignorée.
    public static String suivant(Collection<String> codesDuSite) {
        Set<Integer> pris = codesDuSite.stream()
                .map(code -> CODE_Z.matcher(code.trim().toUpperCase(Locale.ROOT)))
                .filter(Matcher::matches)
                .map(trouve -> Integer.parseInt(trouve.group(1)))
                .collect(Collectors.toSet());
        int numero = 1;
        while (pris.contains(numero)) {
            numero++;
        }
        return "Z" + numero;
    }
}

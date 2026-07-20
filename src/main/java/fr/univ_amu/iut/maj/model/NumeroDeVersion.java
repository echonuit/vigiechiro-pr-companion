package fr.univ_amu.iut.maj.model;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Un numéro de version **comparable**, au sens de [SemVer](https://semver.org) (#2109).
///
/// Comparer des versions comme des chaînes se trompe dès le premier passage à deux chiffres :
/// `"2.9.0"` est lexicographiquement **supérieur** à `"2.10.0"`, ce qui ferait taire la notification
/// exactement quand elle devient utile. D'où ce type plutôt qu'une comparaison de `String`.
///
/// Volontairement **partiel** : seuls majeur, mineur et correctif sont lus, et le suffixe de
/// pré-version (`-rc.1`) rend la version **non comparable** plutôt que d'inventer un ordre. Le dépôt
/// n'en publie pas ; le jour où il en publierait, mieux vaut que la notification s'abstienne que
/// qu'elle propose une pré-version à un naturaliste.
public record NumeroDeVersion(int majeur, int mineur, int correctif) implements Comparable<NumeroDeVersion> {

    /// `2.22.0`, éventuellement préfixé d'un `v` (les tags du dépôt le portent), et rien d'autre.
    private static final Pattern FORME = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)$");

    /// Lit un numéro, ou rend vide si la chaîne n'est pas exactement de cette forme.
    ///
    /// Rend vide - et non une exception - parce que l'entrée vient de deux sources qu'on ne maîtrise
    /// pas : le manifeste d'un artefact (qui vaut `1.0-SNAPSHOT` en développement) et le nom d'un tag
    /// distant. Une version illisible n'est pas une anomalie, c'est un cas à traiter en se taisant.
    public static Optional<NumeroDeVersion> lire(String texte) {
        if (texte == null) {
            return Optional.empty();
        }
        Matcher correspondance = FORME.matcher(texte.strip());
        if (!correspondance.matches()) {
            return Optional.empty();
        }
        return Optional.of(new NumeroDeVersion(
                Integer.parseInt(correspondance.group(1)),
                Integer.parseInt(correspondance.group(2)),
                Integer.parseInt(correspondance.group(3))));
    }

    @Override
    public int compareTo(NumeroDeVersion autre) {
        int parMajeur = Integer.compare(majeur, autre.majeur);
        if (parMajeur != 0) {
            return parMajeur;
        }
        int parMineur = Integer.compare(mineur, autre.mineur);
        return parMineur != 0 ? parMineur : Integer.compare(correctif, autre.correctif);
    }

    @Override
    public String toString() {
        return majeur + "." + mineur + "." + correctif;
    }
}

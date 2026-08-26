package fr.univ_amu.iut.commun.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// **Le nommage des tranches, et l'arbitrage de leurs collisions**, en un seul endroit.
///
/// Deux chemins produisent les tranches d'une nuit : l'**import** (découpe puis réconciliation) et la
/// **réactivation depuis les bruts** (régénération). La réactivation n'accepte une tranche régénérée que
/// parce que la transformation est déterministe (R11) : mêmes octets en entrée, mêmes octets en sortie,
/// donc l'empreinte capturée à l'import doit se retrouver. Cette preuve ne vaut que si les deux chemins
/// aboutissent **au même fichier, sous le même nom**.
///
/// Or le nom n'est pas une décoration : c'est la **clé de jointure** avec l'`observations.csv`. Il était
/// arbitré d'un seul côté, et la réactivation perdait silencieusement toute tranche ayant perdu une
/// collision. Le nommage vit donc ici, dans `commun`, visible des deux features et défini une fois.
///
/// La classe est **pure** : aucune E/S, aucun fichier. Elle dit quel nom porte quoi ; déplacer ou copier
/// regarde l'appelant. C'est ce qui permet à la réactivation de rejouer l'arbitrage d'une nuit entière
/// sans régénérer la nuit entière (l'arbitrage ne manipule que des chaînes).
public final class NommageSequences {

    /// Durée d'une tranche, en secondes **réelles** (au rythme d'acquisition).
    public static final int DUREE_SEQUENCE_SECONDES = 5;

    /// Suffixe `_NNN` juste avant l'extension, à incrémenter en cas de collision.
    private static final Pattern SUFFIXE_SEQUENCE = Pattern.compile("_(\\d{3})(\\.[^.]+)$");

    private NommageSequences() {}

    /// Ce qu'un original **attend** de l'arbitrage : son nom R6, et combien de tranches il produit.
    ///
    /// @param nomOriginal nom R6 de l'original (base du nommage R8 de ses tranches)
    /// @param nombreTranches nombre de tranches produites, cf. [#nombreTranches(double)]
    public record TranchesAttendues(String nomOriginal, int nombreTranches) {

        public TranchesAttendues {
            Objects.requireNonNull(nomOriginal, "nomOriginal");
        }
    }

    /// Nom **souhaité** de la tranche d'index `index` : l'horodatage de l'original décalé de
    /// `index × 5 s`, suffixe `_000` (R8). C'est le nom avant tout arbitrage : deux originaux qui se
    /// chevauchent sur la grille de 5 s peuvent souhaiter le même.
    public static String nomSouhaite(Prefixe prefixe, String nomOriginal, int index) {
        Objects.requireNonNull(prefixe, "prefixe");
        Objects.requireNonNull(nomOriginal, "nomOriginal");
        return prefixe.nommerSequence(nomOriginal, index, index * DUREE_SEQUENCE_SECONDES);
    }

    /// Nombre de tranches d'un original de durée `D` (R10) : `ceil(D / 5)`.
    public static int nombreTranches(double dureeSecondes) {
        return (int) Math.ceil(dureeSecondes / DUREE_SEQUENCE_SECONDES);
    }

    /// **Rejoue l'arbitrage d'une nuit entière** et rend, pour chaque original, les noms définitifs de ses
    /// tranches **dans l'ordre des index**.
    ///
    /// Règle (inchangée, validée sur les données réelles Car640380) : le **plus ancien enregistrement
    /// l'emporte** et garde son `_000` - c'est ce que porte l'`observations.csv`, donc la jointure
    /// observation ↔ audio reste correcte. Le perdant n'est pas perdu : il passe en `_001`, `_002`…
    ///
    /// L'ordre chronologique se lit dans les **noms horodatés** des originaux. Trier sur le nom R6 ou sur
    /// le nom d'enregistreur donne le même ordre, le préfixe de session étant commun à tous.
    ///
    /// L'entrée doit être **tous les originaux de la nuit**, pas seulement ceux qu'on a sous la main :
    /// un original absent ne réserve pas ses noms, et l'arbitrage rendrait alors des noms différents de
    /// ceux que l'import a écrits. Côté réactivation, la liste vient donc de la **base**, jamais du
    /// dossier désigné par l'utilisateur.
    public static Map<String, List<String>> arbitrer(Prefixe prefixe, List<TranchesAttendues> originaux) {
        Objects.requireNonNull(prefixe, "prefixe");
        Objects.requireNonNull(originaux, "originaux");
        List<TranchesAttendues> chronologique = originaux.stream()
                .sorted((a, b) -> a.nomOriginal().compareTo(b.nomOriginal()))
                .toList();
        Set<String> pris = new HashSet<>();
        Map<String, List<String>> parOriginal = new LinkedHashMap<>();
        for (TranchesAttendues attendues : chronologique) {
            List<String> noms = new ArrayList<>();
            for (int index = 0; index < attendues.nombreTranches(); index++) {
                noms.add(premierNomLibre(nomSouhaite(prefixe, attendues.nomOriginal(), index), pris));
            }
            parOriginal.put(attendues.nomOriginal(), List.copyOf(noms));
        }
        return Map.copyOf(parOriginal);
    }

    /// Premier nom libre à partir de `nom` : `nom` s'il est disponible, sinon son suffixe `_000` incrémenté
    /// en `_001`, `_002`… jusqu'à un nom non pris. Le nom retenu est marqué pris.
    public static String premierNomLibre(String nom, Set<String> pris) {
        Objects.requireNonNull(nom, "nom");
        Objects.requireNonNull(pris, "pris");
        String candidat = nom;
        int index = 0;
        while (!pris.add(candidat)) {
            index++;
            candidat = avecIndexSequence(nom, index);
        }
        return candidat;
    }

    /// Remplace le suffixe `_NNN` final de `nom` par `_index` (3 chiffres). Ex. `…_205342_000.wav`, index 1
    /// → `…_205342_001.wav`.
    private static String avecIndexSequence(String nom, int index) {
        String suffixe = String.format(Locale.ROOT, "_%03d", index);
        Matcher marqueur = SUFFIXE_SEQUENCE.matcher(nom);
        if (marqueur.find()) {
            return nom.substring(0, marqueur.start()) + suffixe + marqueur.group(2);
        }
        return nom + suffixe;
    }
}

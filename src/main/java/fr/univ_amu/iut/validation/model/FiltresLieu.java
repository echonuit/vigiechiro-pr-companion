package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.LieuQualifie;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/// Restreindre des observations à un ou plusieurs **lieux**, en ligne de commande (#2971). Pendant
/// CLI de la puce « Lieu » de l'écran, avec trois écarts assumés.
///
/// **Les trois niveaux se comparent** : commune, carré - dont le nom convivial n'est que la seconde
/// étiquette (#3157) - et point, qualifié par son carré (« 640380 · A1 », #2992). C'est la sortie
/// qui désambiguïse, pas le critère qui se restreint (#3350). **La correspondance est partielle**,
/// parce qu'on tape à l'aveugle : `--lieu aix` trouve « Aix-en-Provence », `--lieu 640380` comme
/// `--lieu vallon` retiennent le même carré, que le refus liste comme l'écran le montre (#3159).
/// **Ne rien retenir est un refus** au sens de l'ADR 2635, pas une archive vide en code 0.
public final class FiltresLieu {

    private FiltresLieu() {}

    /// Les lignes dont **l'une** des dimensions correspond à **l'un** des lieux demandés (appartenance,
    /// comme cocher plusieurs cases). `lieux` vide n'écarte rien.
    ///
    /// @throws RegleMetierException si aucune ligne ne correspond, avec les lieux réellement présents
    public static List<LigneObservationAudio> parLieu(List<LigneObservationAudio> lignes, List<String> lieux) {
        return parLieu(lignes, lieux, FiltresLieu::dimensions, FiltresLieu::dimensionsNommees);
    }

    /// Le même filtre, sur **n'importe quelle ligne** qui sait dire ses dimensions de lieu (#3059).
    ///
    /// La règle - « la ligne passe si l'**une** de ses dimensions correspond à l'**un** des lieux
    /// demandés » - ne dépend pas du type filtré : c'est exactement celle de
    /// [fr.univ_amu.iut.commun.view.CritereListe#multipleParmi] côté écran. Elle était écrite deux fois,
    /// une par surface ; elle l'est désormais une fois par surface, et non une fois par type de ligne.
    ///
    /// @param dimensions ce qu'on lit sur une ligne pour la comparer (valeurs nulles ou vides ignorées)
    public static <T> List<T> parLieu(List<T> lignes, List<String> lieux, Function<T, List<String>> dimensions) {
        return parLieu(lignes, lieux, dimensions, dimensions);
    }

    /// Le même filtre, quand ce qu'on **compare** et ce qu'on **nomme** au refus diffèrent (#3350).
    ///
    /// Les deux se sont séparés le jour où le **point** est devenu comparable. Mesure faite sur quinze
    /// carrés : le refus listait alors 31 entrées au lieu de 15, et sa borne de douze ne montrait plus
    /// que **six carrés** - les points avaient évincé la moitié de ce qui sert à corriger une faute de
    /// frappe. Un refus qui grossit en disant moins est un refus qui s'use.
    ///
    /// Le refus nomme donc les niveaux qui **discriminent** - commune et carré - et le message le dit,
    /// pour ne pas laisser croire à une liste exhaustive de ce qui est acceptable.
    ///
    /// @param dimensionsComparees ce qui fait passer une ligne
    /// @param dimensionsNommees ce que le refus énumère
    public static <T> List<T> parLieu(
            List<T> lignes,
            List<String> lieux,
            Function<T, List<String>> dimensionsComparees,
            Function<T, List<String>> dimensionsNommees) {
        Function<T, List<String>> dimensions = dimensionsComparees;
        if (lieux == null || lieux.isEmpty()) {
            return lignes;
        }
        List<String> demandes = lieux.stream()
                .map(NormalisationTexte::normaliser)
                .filter(valeur -> !valeur.isBlank())
                .toList();
        if (demandes.isEmpty()) {
            return lignes;
        }
        List<T> retenues = lignes.stream()
                .filter(ligne -> correspond(dimensions.apply(ligne), demandes))
                .toList();
        if (retenues.isEmpty()) {
            // Le message nomme les lieux présents **dans l'ensemble reçu**, et le dit. Sur
            // `lister-observations`, cet ensemble a déjà subi les autres filtres : annoncer les lieux de
            // toute la base y serait trompeur (« Ahetze est disponible » alors qu'il n'a aucune ligne à
            // revoir). Ainsi formulé, le refus reste vrai dans les deux commandes.
            throw new RegleMetierException("Aucune observation pour " + citer(lieux)
                    + " parmi celles retenues. Lieux présents (communes et carrés) : "
                    + resumer(presents(lignes, dimensionsNommees)) + ".");
        }
        return retenues;
    }

    /// Vrai si l'une des dimensions de la ligne **contient** l'un des lieux demandés, une fois les deux
    /// normalisés (casse et accents ignorés).
    private static boolean correspond(List<String> valeurs, List<String> demandes) {
        return valeurs.stream()
                .map(NormalisationTexte::normaliser)
                .anyMatch(valeur -> demandes.stream().anyMatch(valeur::contains));
    }

    /// Les dimensions comparables d'une ligne : la **commune**, le **carré** écrit comme l'écran
    /// l'affiche (« 640380 · Vallon », #3157), et le **point qualifié par son carré** (« 640380 · A1 »,
    /// #3350).
    ///
    /// Le nom du site n'est pas une dimension de plus : c'est l'autre étiquette du carré, et les deux
    /// tiennent dans une valeur. La correspondance étant partielle, `--lieu 640380` et `--lieu vallon`
    /// continuent l'un et l'autre de retenir ce carré ; ce qui change est ce que le **refus** nomme.
    private static List<String> dimensions(LigneObservationAudio ligne) {
        return Stream.of(
                        ligne.commune(),
                        LieuQualifie.qualifier(ligne.numeroCarre(), ligne.nomSite()),
                        LieuQualifie.qualifier(ligne.numeroCarre(), ligne.codePoint()))
                .filter(valeur -> valeur != null && !valeur.isBlank())
                .toList();
    }

    /// Ce que le **refus** énumère : la commune et le carré, sans le point (#3350). Cf. la surcharge à
    /// quatre paramètres pour la mesure qui a séparé les deux listes.
    private static List<String> dimensionsNommees(LigneObservationAudio ligne) {
        return Stream.of(ligne.commune(), LieuQualifie.qualifier(ligne.numeroCarre(), ligne.nomSite()))
                .filter(valeur -> valeur != null && !valeur.isBlank())
                .toList();
    }

    /// Les lieux réellement présents, sans doublon et triés : ce que le refus doit dire.
    private static <T> List<String> presents(List<T> lignes, Function<T, List<String>> dimensions) {
        return lignes.stream()
                .flatMap(ligne -> dimensions.apply(ligne).stream())
                .filter(valeur -> valeur != null && !valeur.isBlank())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    /// Les lieux demandés, cités tels que l'utilisateur les a tapés.
    private static String citer(List<String> lieux) {
        return lieux.size() == 1 ? "« " + lieux.get(0) + " »" : "ces lieux (" + String.join(", ", lieux) + ")";
    }

    /// La liste des lieux disponibles, **bornée** : un refus qui déverserait deux cents communes ne se
    /// lirait pas, et le but est de faire voir la faute de frappe, pas d'inventorier la base.
    private static String resumer(List<String> lieux) {
        if (lieux.isEmpty()) {
            return "aucun";
        }
        if (lieux.size() <= 12) {
            return String.join(", ", lieux);
        }
        return String.join(", ", lieux.subList(0, 12)) + "… (" + lieux.size() + " en tout)";
    }
}

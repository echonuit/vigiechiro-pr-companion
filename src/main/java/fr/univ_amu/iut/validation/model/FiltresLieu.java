package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.LieuQualifie;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/// Restreindre des observations à un ou plusieurs **lieux**, en ligne de commande (#2971).
///
/// Pendant CLI de la puce « Lieu » de l'écran, avec **trois écarts assumés** que la conception a
/// arbitrés parce qu'ils ne se déduisent pas de l'IHM.
///
/// ## Les trois niveaux, parce que la sortie désambiguïse (#3350)
///
/// Le domaine a **trois** niveaux : la commune, le carré (dont le nom convivial n'est que la seconde
/// étiquette, #3157) et le point. Les trois se comparent.
///
/// Ce n'était pas le cas : le point avait été écarté au motif qu'un code seul (« A1 ») désigne autant
/// de lieux qu'il y a de carrés - le schéma pose `UNIQUE(site_id, code)` - et qu'une ligne de commande
/// n'a pas, contrairement à l'écran, de liste sous les yeux pour lever l'ambiguïté.
///
/// **L'inventaire a démenti la prémisse.** Toutes les sorties concernées portent le carré **et** le
/// point : `lister-passages` sur chaque ligne, les CSV d'`exporter-sons` (colonnes « Carré », « Point »)
/// et d'`exporter-activite` (idem), `solde-saison` en colonne. Un `--lieu A1` qui remonte les A1 de
/// plusieurs carrés se lit donc sans ambiguïté, sur la sortie elle-même.
///
/// La règle qui en sort tient en une phrase : **c'est la sortie qui désambiguïse, pas le critère qui se
/// restreint.** Corollaire pour la suite - une commande qui offre `--lieu` doit montrer le lieu ; c'est
/// la thèse de l'[ADR 3151] appliquée à la ligne de commande, et `lister-observations` y manquait.
///
/// Le point se compare **qualifié par son carré** (« 640380 · A1 », #2992), comme
/// `ListerPassages#dimensionsDuLieu` le faisait déjà : la correspondance étant partielle, `--lieu A1`
/// et `--lieu 640380` retiennent l'un et l'autre ce qu'il faut.
///
/// ## Le carré se compare qualifié, mais se tape comme on veut
///
/// Ce que le **refus** nomme doit se recopier tel quel dans la commande suivante ; il liste donc les
/// carrés comme l'écran les montre, « 640380 · Vallon » (#3159). Personne n'est pour autant obligé de
/// taper un point médian : la correspondance étant partielle, `--lieu 640380` et `--lieu vallon`
/// retiennent ce même carré, comme avant.
///
/// ## La correspondance est partielle
///
/// À l'écran on **coche dans une liste fermée** : la valeur est toujours exacte. En ligne de commande on
/// tape à l'aveugle, sans rien pour rappeler l'orthographe ni les accents. `--lieu aix` trouve donc
/// « Aix-en-Provence ». C'est déjà le comportement de `--campagne` côté multisite, pour la même raison.
///
/// ## Ne rien retenir est un refus, pas un résultat
///
/// Une valeur qui ne correspond à rien **arrête** la commande en nommant les lieux disponibles, plutôt
/// que de rendre un ensemble vide. Une archive vide en code 0 est un succès qui ne contient rien : un
/// script enchaînerait sans voir la faute de frappe, et l'expert recevrait un ZIP creux. C'est la forme
/// que demande l'ADR 2635, un refus dit ce qui manque, et c'est déjà la règle de
/// [SelectionObservations] pour un lot vide.
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

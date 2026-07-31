package fr.univ_amu.iut.validation.model;

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
/// ## Le point n'en fait pas partie
///
/// La puce couvre commune, carré, **point** et site. Ici, le point est écarté : le schéma pose
/// `UNIQUE(site_id, code)`, donc un code seul (« A1 », « Z1 ») désigne autant de lieux qu'il y a de
/// carrés. L'écran s'en tire en l'affichant **qualifié** (« 640380 · A1 », #2992), ce qui suppose une
/// liste sous les yeux ; une ligne de commande n'en a pas, et `--lieu A1` rouvrirait le défaut sans que
/// rien ne le montre. Reproduire la forme qualifiée imposerait par ailleurs un **point médian dans une
/// valeur d'option**, à échapper dans chaque script.
///
/// Le point restera atteignable par un croisement `--carre` / `--point`, sur le modèle de l'écran
/// Activité où deux critères en **conjonction** désignent un point précis.
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
public final class FiltreLieu {

    private FiltreLieu() {}

    /// Les lignes dont **l'une** des dimensions correspond à **l'un** des lieux demandés (appartenance,
    /// comme cocher plusieurs cases). `lieux` vide n'écarte rien.
    ///
    /// @throws RegleMetierException si aucune ligne ne correspond, avec les lieux réellement présents
    public static List<LigneObservationAudio> appliquer(List<LigneObservationAudio> lignes, List<String> lieux) {
        return appliquer(lignes, lieux, FiltreLieu::dimensions);
    }

    /// Le même filtre, sur **n'importe quelle ligne** qui sait dire ses dimensions de lieu (#3059).
    ///
    /// La règle - « la ligne passe si l'**une** de ses dimensions correspond à l'**un** des lieux
    /// demandés » - ne dépend pas du type filtré : c'est exactement celle de
    /// [fr.univ_amu.iut.commun.view.CritereListe#multipleParmi] côté écran. Elle était écrite deux fois,
    /// une par surface ; elle l'est désormais une fois par surface, et non une fois par type de ligne.
    ///
    /// @param dimensions ce qu'on lit sur une ligne pour la comparer (valeurs nulles ou vides ignorées)
    public static <T> List<T> appliquer(List<T> lignes, List<String> lieux, Function<T, List<String>> dimensions) {
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
                    + " parmi celles retenues. Lieux présents : " + resumer(presents(lignes, dimensions)) + ".");
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

    /// Les dimensions comparables d'une ligne : commune, carré, site. **Sans le point**, cf. l'en-tête.
    private static List<String> dimensions(LigneObservationAudio ligne) {
        return Stream.of(ligne.commune(), ligne.numeroCarre(), ligne.nomSite())
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

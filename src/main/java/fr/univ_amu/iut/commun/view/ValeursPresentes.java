package fr.univ_amu.iut.commun.view;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// Les **valeurs réellement présentes** d'une dimension, telles qu'une puce les offre (#3097).
///
/// Les quatre catalogues de critères écrivaient cette méthode à l'identique, au type de ligne près.
/// Elle applique trois règles que chacune des copies portait sans qu'aucun test ne les garde :
///
/// - les valeurs **absentes** sont écartées : une ligne sans commune renseignée ne doit pas produire
///   une entrée vide dans le menu, dont on ne saurait ni ce qu'elle désigne ni pourquoi la cocher ;
/// - les **doublons** sont fondus : un lieu proposé deux fois se remarque ;
/// - le reste est **trié** : une liste dans le désordre se cherche.
///
/// Générique sur le type de ligne : c'est tout ce qui distinguait les quatre copies.
public final class ValeursPresentes {

    private ValeursPresentes() {}

    /// Les valeurs distinctes, non nulles et triées que `dimension` lit sur `lignes`.
    public static <T> List<String> de(List<T> lignes, Function<T, String> dimension) {
        return lignes.stream()
                .map(dimension)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}

package fr.univ_amu.iut.commun.view;

import java.util.Map;

/// État **complet** d'une vue mémorisée (#623 + #994) : la combinaison de **filtres** ET la **disposition
/// des colonnes** (une entrée par table de la vue, cf. [AdaptateurColonnes]). C'est la forme stockée dans
/// `saved_filter_view.descriptor_json` depuis #994 ; la (dé)sérialisation et la **rétro-compatibilité** avec
/// les blobs antérieurs (filtres seuls) sont assurées par [DescripteurVueJson].
///
/// `colonnes` peut être **vide** : vue sans sélecteur de colonnes, ou vue enregistrée avant #994.
///
/// @param filtres état des filtres (texte + puces), cf. [DescripteurFiltre]
/// @param colonnes disposition des colonnes par table (clé stable → descripteur), éventuellement vide
public record DescripteurVue(DescripteurFiltre filtres, Map<String, DescripteurColonnes> colonnes) {

    public DescripteurVue {
        colonnes = Map.copyOf(colonnes);
    }

    /// Une vue **sans** disposition de colonnes (vue sans sélecteur, ou format hérité #623).
    public static DescripteurVue sansColonnes(DescripteurFiltre filtres) {
        return new DescripteurVue(filtres, Map.of());
    }
}

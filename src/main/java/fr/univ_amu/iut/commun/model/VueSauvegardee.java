package fr.univ_amu.iut.commun.model;

/// Une **vue mémorisée** générique (#623) : un **état de filtres** (descripteur sérialisé en JSON,
/// cf. `commun.view.DescripteurFiltreJson`) enregistré sous un **nom**, pour une **feature/écran** donné
/// (`feature` : `audio` / `analyse` / `multisite`).
///
/// Généralise la `SavedView` du multisite à **toutes** les vues tabulaires : la colonne `feature` permet à
/// chaque écran de ne lister que **ses** vues. Table `saved_filter_view` (migration V11) ; le descripteur
/// est **opaque** côté base (stocké tel quel). `id` vaut `null` avant insertion.
///
/// @param id clé auto-générée (`null` avant insertion)
/// @param feature écran/table propriétaire de la vue (`audio`, `analyse`, `multisite`)
/// @param nom nom donné à la vue par l'utilisateur
/// @param descripteurJson état de filtres sérialisé (JSON du `DescripteurFiltre`)
public record VueSauvegardee(Long id, String feature, String nom, String descripteurJson) {}

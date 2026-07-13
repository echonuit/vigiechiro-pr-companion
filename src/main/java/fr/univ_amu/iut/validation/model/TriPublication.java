package fr.univ_amu.iut.validation.model;

import java.util.List;

/// Tri des observations **revues** d'un passage au regard de la publication (#723) : ce qui partira,
/// et ce qui est écarté par cause. Produit par [PublicationCorrections#trier] **sans aucun réseau** :
/// il sert d'aperçu à la confirmation IHM (« N corrections vont être publiées… ») avant d'écrire quoi
/// que ce soit sur la plateforme, puis de plan d'envoi à la publication elle-même.
///
/// @param publiables observations prêtes à partir (taxon + certitude déclarés, ancrage présent,
///     objectid du taxon connu), dans l'ordre d'envoi
/// @param sansCertitude revues mais sans certitude déclarée (« à compléter avant publication »)
/// @param sansAncrage revues mais sans ancrage plateforme (import CSV, ou antérieur à #1139)
/// @param horsReferentiel taxon observateur sans objectid VigieChiro (hors référentiel)
public record TriPublication(List<Observation> publiables, int sansCertitude, int sansAncrage, int horsReferentiel) {

    /// Nombre d'observations écartées avant envoi.
    public int ecartees() {
        return sansCertitude + sansAncrage + horsReferentiel;
    }
}

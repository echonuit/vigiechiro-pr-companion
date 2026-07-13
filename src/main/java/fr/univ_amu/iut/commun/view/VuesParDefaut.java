package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.VueSauvegardee;
import java.util.List;

/// Fabrique des **vues par défaut** d'un écran à vues mémorisées (#623) : une [VueSauvegardee] à `id`
/// **nul** (jamais persistée → lecture seule), dont le descripteur est sérialisé exactement comme
/// [GestionnaireFiltres#decrire()] le produirait, pour que rejouer la vue laisse un état « non
/// modifié ». Factorise le helper copié à l'identique dans les catalogues de critères des écrans à
/// vues (analyse, multisite, audio) (#1257).
public final class VuesParDefaut {

    private VuesParDefaut() {
        // Fabrique statique : jamais instanciée.
    }

    /// Une vue par défaut de `feature` : aucun critère = vue « Tout », sans filtre.
    public static VueSauvegardee vue(String feature, String nom, DescripteurCritere... criteres) {
        String descripteur = DescripteurFiltreJson.serialiser(new DescripteurFiltre("", List.of(criteres)));
        return new VueSauvegardee(null, feature, nom, descripteur);
    }
}

package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import java.util.ArrayList;
import java.util.List;

/// La ligne de **réglages** propre à la courbe d'activité, estampillée sur son export (#2352).
///
/// Les deux lignes communes à tout export — identité et provenance — vivent dans
/// [fr.univ_amu.iut.commun.view.LegendeExport] : elles ne doivent rien à cet écran.
///
/// Une image de graphe sans son contexte devient **inexploitable dès qu'elle quitte l'application** : rien
/// n'y dit de quel carré, de quel point ni de quelle nuit elle parle, ni quels filtres étaient actifs quand
/// on l'a produite. Deux courbes de forme voisine peuvent alors être confondues, ou une courbe filtrée être
/// lue comme un total. La légende répond à ces questions **dans l'image**, pas dans le nom du fichier (qu'un
/// copier-coller perd).
///
/// Fonction **pure** (aucun nœud JavaFX, aucune E/S), donc testable seule : la vue se contente de rendre les
/// lignes produites ici.
public final class LegendeExportActivite {

    private LegendeExportActivite() {}

    /// Ligne de **réglages** : la largeur de tranche et les filtres actifs, en clair. Sans filtre, on le
    /// **dit** (« aucun filtre ») plutôt que de laisser un silence qu'on lirait comme un total non filtré ou
    /// comme une information manquante.
    public static String reglages(int trancheMinutes, DescripteurFiltre filtres) {
        return "Tranche " + trancheMinutes + " min · Filtres : " + resumerFiltres(filtres);
    }

    private static String resumerFiltres(DescripteurFiltre filtres) {
        if (filtres == null) {
            return "aucun filtre";
        }
        List<String> parties = new ArrayList<>();
        if (!filtres.texte().isBlank()) {
            parties.add("recherche « " + filtres.texte().trim() + " »");
        }
        for (DescripteurCritere critere : filtres.criteres()) {
            parties.add(decrireCritere(critere));
        }
        return parties.isEmpty() ? "aucun filtre" : String.join(", ", parties);
    }

    /// Un critère en clair : `nuit = 2026-06-21`, ou le seul nom du critère s'il n'a pas de valeur (critère
    /// booléen, ou puce ajoutée sans choix encore fait).
    private static String decrireCritere(DescripteurCritere critere) {
        if (critere.valeurs().isEmpty()) {
            return critere.nom();
        }
        return critere.nom() + " = " + String.join(" / ", critere.valeurs());
    }
}

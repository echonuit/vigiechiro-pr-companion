package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/// Compose la **légende de contexte** estampillée sur l'export image de la courbe d'activité (#2352).
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

    /// Ligne d'**identité** : de quoi parle la courbe — carré, point, passage —, ou la portée transverse
    /// quand aucun passage n'est en contexte (`null`).
    public static String identite(ContextePassage contexte) {
        if (contexte == null) {
            return "Tous les passages";
        }
        List<String> segments = new ArrayList<>();
        segments.add("Carré " + contexte.site().numeroCarre());
        if (contexte.site().codePoint() != null) {
            segments.add("Point " + contexte.site().codePoint());
        }
        if (contexte.numeroPassage() > 0) {
            segments.add("Passage N° " + contexte.numeroPassage());
        }
        return String.join(" · ", segments);
    }

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

    /// Ligne de **provenance** : ce qui a produit l'image et quand. Sans elle, impossible de savoir si une
    /// image trouvée dans un dossier reflète encore l'état des données.
    public static String provenance(String version, LocalDate date) {
        return "VigieChiro Companion " + version + " · exporté le " + date;
    }
}

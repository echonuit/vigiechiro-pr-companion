package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/// Les deux lignes de contexte que **toute** image exportée porte : d'où elle vient, et ce qui l'a
/// produite.
///
/// Une image de graphe sans ces mentions devient **inexploitable dès qu'elle quitte l'application** :
/// rien n'y dit de quel carré, de quel point ni de quelle nuit elle parle, ni avec quelle version elle a
/// été produite. Deux graphes de forme voisine se confondent alors, et une courbe datée de six mois se
/// lit comme une courbe d'hier.
///
/// Fonctions **pures**, sans nœud JavaFX : la vue ne fait que rendre les lignes produites ici. Ce que
/// chaque écran ajoute de spécifique — réglages, filtres actifs — lui appartient (cf.
/// [fr.univ_amu.iut.analyse.view.LegendeExportActivite]).
public final class LegendeExport {

    private LegendeExport() {}

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

    /// Ligne de **provenance** : ce qui a produit l'image et quand. Sans elle, impossible de savoir si une
    /// image trouvée dans un dossier reflète encore l'état des données.
    public static String provenance(String version, LocalDate date) {
        return "VigieChiro Companion " + version + " · exporté le " + date;
    }
}

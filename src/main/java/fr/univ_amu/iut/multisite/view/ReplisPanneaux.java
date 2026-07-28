package fr.univ_amu.iut.multisite.view;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.SplitPane;

/// Repli des deux panneaux de « Carte & passages » (#347) : chacun se replie entièrement pour donner
/// toute la largeur à l'autre, et se rouvre à sa place, diviseur restauré.
///
/// Extrait d'[MultisiteController] : c'est le **seul** groupe de cet écran qui ne parle pas de
/// passages. Ce sont des mécaniques de `SplitPane` - retirer un nœud, le réinsérer à son index
/// canonique, mémoriser la position du diviseur, tenir l'état des deux poignées - et rien de tout cela
/// n'a besoin de savoir ce que le tableau contient.
///
/// L'extraction a été **demandée par le portail qualité** : le contrôleur était à son plafond de
/// lignes et le traitement en lot (#2357) l'a fait déborder. Le seuil disait quelque chose de vrai.
final class ReplisPanneaux {

    private static final String CHEVRON_GAUCHE = "fas-chevron-left";
    private static final String CHEVRON_DROITE = "fas-chevron-right";

    private final SplitPane split;
    private final Node zoneCarte;
    private final Node panneauTableau;
    private final Button poigneeCarte;
    private final Button poigneeTableau;

    /// Position du diviseur avant le dernier repli, pour la restaurer à la réouverture.
    private double derniereDivision = 0.42;

    ReplisPanneaux(SplitPane split, Node zoneCarte, Node panneauTableau, Button poigneeCarte, Button poigneeTableau) {
        this.split = split;
        this.zoneCarte = zoneCarte;
        this.panneauTableau = panneauTableau;
        this.poigneeCarte = poigneeCarte;
        this.poigneeTableau = poigneeTableau;
    }

    /// Replie (ou rouvre) la **carte** : le tableau prend alors toute la largeur.
    void basculerCarte() {
        basculer(zoneCarte, 0);
    }

    /// Replie (ou rouvre) le **tableau** : la carte prend alors toute la largeur.
    void basculerTableau() {
        basculer(panneauTableau, split.getItems().size());
    }

    private void basculer(Node panneau, int indexDeRetour) {
        if (estVisible(panneau)) {
            replier(panneau);
        } else {
            rouvrir(panneau, indexDeRetour);
        }
        majPoignees();
    }

    private boolean estVisible(Node panneau) {
        return split.getItems().contains(panneau);
    }

    /// Retire un panneau du `SplitPane` (repli complet), après avoir mémorisé la position du diviseur.
    private void replier(Node panneau) {
        if (split.getDividerPositions().length > 0) {
            derniereDivision = split.getDividerPositions()[0];
        }
        split.getItems().remove(panneau);
    }

    /// Réinsère un panneau à sa place canonique (carte en 0, tableau en fin) et restaure le diviseur.
    private void rouvrir(Node panneau, int index) {
        if (!split.getItems().contains(panneau)) {
            split.getItems().add(Math.min(index, split.getItems().size()), panneau);
            split.setDividerPositions(derniereDivision);
        }
    }

    /// Replie le tableau (#338) pour donner toute la largeur à la carte : c'est le but du clic « Voir
    /// sur la carte ». Sans effet s'il est déjà replié.
    void degagerLaCarte() {
        if (estVisible(panneauTableau)) {
            replier(panneauTableau);
            majPoignees();
        }
    }

    /// Met à jour libellé, info-bulle, texte accessible (#163) et état activé des deux poignées.
    ///
    /// La poignée d'un panneau **déjà seul** est désactivée : on ne peut pas tout replier. Celle du
    /// panneau replié invite à le rouvrir.
    void majPoignees() {
        boolean carteVisible = estVisible(zoneCarte);
        boolean tableauVisible = estVisible(panneauTableau);

        StyleControlesCarte.poignee(
                poigneeCarte,
                "Carte",
                carteVisible ? CHEVRON_GAUCHE : CHEVRON_DROITE,
                ContentDisplay.LEFT,
                carteVisible ? "Masquer la carte" : "Afficher la carte",
                tableauVisible);
        StyleControlesCarte.poignee(
                poigneeTableau,
                "Tableau",
                tableauVisible ? CHEVRON_DROITE : CHEVRON_GAUCHE,
                ContentDisplay.RIGHT,
                tableauVisible ? "Masquer le tableau" : "Afficher le tableau",
                carteVisible);
    }
}

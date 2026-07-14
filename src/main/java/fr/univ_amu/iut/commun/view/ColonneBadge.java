package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.viewmodel.ClasseBadge;
import java.util.function.Function;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;

/// Badge de statut réutilisable (#691) pour une **colonne de table** : une cellule qui affiche un libellé
/// dans une **pastille** dont la classe CSS (donc la couleur) est **dérivée de la donnée** de la ligne.
///
/// Choix « composant plutôt que convention » : une seule cellule badge, plutôt que du texte coloré
/// réimplémenté par écran (multisite/analyse affichaient un statut en texte brut, sites avait sa propre
/// cellule). Les classes `.badge*` et leurs couleurs vivent dans `design.css` / `palette.css` ; la couleur
/// n'est jamais stockée, seulement dérivée.
public final class ColonneBadge {

    /// Classe CSS de base de la pastille, et **préfixe** de toutes les classes sémantiques
    /// (`badge-succes`, `badge-statut-…`) : c'est elle qu'on retire avant d'en appliquer une autre,
    /// les cellules étant réutilisées en défilement.
    private static final String BADGE = "badge";

    private ColonneBadge() {}

    /// Fabrique une cellule badge générique : le texte vient de la `cellValueFactory` de la colonne, la
    /// classe sémantique est calculée à partir de l'**item de la ligne** par `classe`. Retire toute classe
    /// `badge*` précédente avant d'appliquer la nouvelle (réutilisation des cellules en défilement).
    public static <S> TableCell<S, String> cellule(Function<S, String> classe) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String valeur, boolean vide) {
                super.updateItem(valeur, vide);
                getStyleClass().removeIf(c -> c.startsWith(BADGE));
                if (vide
                        || valeur == null
                        || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    setText(valeur);
                    getStyleClass().addAll(BADGE, classe.apply(getTableRow().getItem()));
                }
            }
        };
    }

    /// Cellule badge **qui s'explique** : même dérivation de couleur que [#cellule(Function)], plus une
    /// **infobulle** calculée à partir de l'item de la ligne.
    ///
    /// Utile quand le badge nomme un état dont l'utilisateur ne peut pas deviner ce qu'il autorise ou
    /// demande (#801, même intention qu'`IndicateurBlocage`) — et, pour un état **observé** (#1338), quand
    /// il faut dire **de quand l'information date** : un relevé n'est pas une vérité.
    ///
    /// Une infobulle `null` ou vide n'installe rien (pas de bulle vide au survol).
    public static <S> TableCell<S, String> cellule(Function<S, String> classe, Function<S, String> infobulle) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String valeur, boolean vide) {
                super.updateItem(valeur, vide);
                getStyleClass().removeIf(c -> c.startsWith(BADGE));
                setTooltip(null);
                if (vide
                        || valeur == null
                        || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    S item = getTableRow().getItem();
                    setText(valeur);
                    getStyleClass().addAll(BADGE, classe.apply(item));
                    String texte = infobulle.apply(item);
                    if (texte != null && !texte.isBlank()) {
                        setTooltip(new Tooltip(texte));
                    }
                }
            }
        };
    }

    /// Classe CSS du badge de **statut workflow** d'un passage (`badge-statut-…`), pour l'usage en cellule
    /// de table. Délègue à [ClasseBadge] (source unique partagée avec les viewmodels de feature).
    public static String classe(StatutWorkflow statut) {
        return ClasseBadge.pour(statut);
    }

    /// Classe CSS du badge de **verdict** d'un passage (`badge-verdict-…`) ; « À vérifier » par défaut
    /// lorsqu'aucun verdict n'est encore saisi. Délègue à [ClasseBadge].
    public static String classe(Verdict verdict) {
        return ClasseBadge.pour(verdict);
    }
}

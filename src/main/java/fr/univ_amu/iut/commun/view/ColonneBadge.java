package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.Locale;
import java.util.function.Function;
import javafx.scene.control.TableCell;

/// Badge de statut réutilisable (#691) pour une **colonne de table** : une cellule qui affiche un libellé
/// dans une **pastille** dont la classe CSS (donc la couleur) est **dérivée de la donnée** de la ligne.
///
/// Choix « composant plutôt que convention » : une seule cellule badge, plutôt que du texte coloré
/// réimplémenté par écran (multisite/analyse affichaient un statut en texte brut, sites avait sa propre
/// cellule). Les classes `.badge*` et leurs couleurs vivent dans `design.css` / `palette.css` ; la couleur
/// n'est jamais stockée, seulement dérivée.
public final class ColonneBadge {

    private ColonneBadge() {}

    /// Fabrique une cellule badge générique : le texte vient de la `cellValueFactory` de la colonne, la
    /// classe sémantique est calculée à partir de l'**item de la ligne** par `classe`. Retire toute classe
    /// `badge*` précédente avant d'appliquer la nouvelle (réutilisation des cellules en défilement).
    public static <S> TableCell<S, String> cellule(Function<S, String> classe) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String valeur, boolean vide) {
                super.updateItem(valeur, vide);
                getStyleClass().removeIf(c -> c.startsWith("badge"));
                if (vide
                        || valeur == null
                        || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    setText(valeur);
                    getStyleClass().addAll("badge", classe.apply(getTableRow().getItem()));
                }
            }
        };
    }

    /// Classe CSS du badge de **statut workflow** d'un passage (`badge-statut-…`).
    public static String classe(StatutWorkflow statut) {
        return "badge-statut-" + statut.name().toLowerCase(Locale.ROOT);
    }

    /// Classe CSS du badge de **verdict** d'un passage (`badge-verdict-…`) ; « À vérifier » par défaut
    /// lorsqu'aucun verdict n'est encore saisi.
    public static String classe(Verdict verdict) {
        Verdict effectif = verdict == null ? Verdict.A_VERIFIER : verdict;
        return "badge-verdict-" + effectif.name().toLowerCase(Locale.ROOT);
    }

    /// Classe CSS du badge de **statut de revue** d'une observation audio (`badge-observation-…`) : le
    /// statut de validation d'une séquence (À revoir / Validée / Corrigée), distinct du workflow d'un
    /// passage. Unifie la colonne « Statut » de l'écran Sons & validation avec les autres tables (#686).
    public static String classe(StatutObservation statut) {
        return "badge-observation-" + statut.name().toLowerCase(Locale.ROOT);
    }
}

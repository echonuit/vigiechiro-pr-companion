package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.util.function.Supplier;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

/// Cellules personnalisées de la table de la vue audio, sorties du [SonsValidationController] pour
/// l'alléger (cohésion, seuil PMD) : une cellule texte à **infobulle** (le nom de fichier transformé est
/// long) et les cellules-**indicateurs** (référence, commentaire) rendues par une **icône Ikonli colorée**
/// (les emojis ⭐/💬 ne s'affichaient pas dans toutes les polices et manquaient de contraste).
final class CellulesAudio {

    /// Icônes FontAwesome 5 (Ikonli) des indicateurs, réutilisées pour l'en-tête et la cellule.
    static final String ICONE_REFERENCE = "fas-star";
    static final String ICONE_COMMENTAIRE = "fas-comment-dots";

    /// Classes CSS colorant les icônes (`-fx-icon-color` dans `sons-validation.css`) : étoile dorée,
    /// commentaire bleu.
    static final String STYLE_REFERENCE = "icone-reference";
    static final String STYLE_COMMENTAIRE = "icone-commentaire";

    private CellulesAudio() {}

    /// `true` si la chaîne porte une valeur affichable (non nulle, non blanche).
    static boolean estRenseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }

    /// Une icône Ikonli colorée par sa classe CSS (partagée entre en-tête de colonne et cellules).
    static FontIcon icone(String literal, String classeCss) {
        FontIcon icone = new FontIcon(literal);
        icone.getStyleClass().add(classeCss);
        return icone;
    }

    /// Configure une colonne-**indicateur** : en-tête réduit à l'**icône colorée** (texte vidé + graphique),
    /// un `id` stable pour la retrouver, le tri **désactivé** (trier une icône n'a pas de sens et donnait une
    /// colonne « vide » triable déroutante) et sa cellule dédiée (le fournisseur rend une cellule **neuve par
    /// ligne**). La classe CSS de l'icône est dérivée du littéral pour rester cohérente avec la cellule.
    static void configurerColonne(
            TableColumn<LigneObservationAudio, String> colonne,
            String id,
            String iconeLiteral,
            Supplier<TableCell<LigneObservationAudio, String>> fournisseurCellule) {
        String classeCss = ICONE_REFERENCE.equals(iconeLiteral) ? STYLE_REFERENCE : STYLE_COMMENTAIRE;
        colonne.setText("");
        colonne.setGraphic(icone(iconeLiteral, classeCss));
        colonne.setId(id);
        colonne.setSortable(false);
        colonne.setCellFactory(c -> fournisseurCellule.get());
    }

    /// Configure les **deux colonnes-indicateurs** (référence ⭐ et commentaire 💬) via
    /// [#configurerColonne] : icône colorée en en-tête, cellule dédiée, non triables.
    static void configurerIndicateurs(
            TableColumn<LigneObservationAudio, String> colReference,
            TableColumn<LigneObservationAudio, String> colCommentaire) {
        configurerColonne(colReference, "colReference", ICONE_REFERENCE, CellulesAudio::reference);
        configurerColonne(colCommentaire, "colCommentaire", ICONE_COMMENTAIRE, CellulesAudio::commentaire);
    }

    /// Cellule texte qui **élide** un contenu long et en expose la valeur complète via une infobulle au
    /// survol. Ni infobulle ni décoration pour un contenu vide ou le tiret « — ».
    static TableCell<LigneObservationAudio, String> avecInfobulle() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String valeur, boolean vide) {
                super.updateItem(valeur, vide);
                if (vide || !estRenseigne(valeur) || "—".equals(valeur)) {
                    setText(vide ? null : valeur);
                    setTooltip(null);
                } else {
                    setText(valeur);
                    setTooltip(new Tooltip(valeur));
                }
            }
        };
    }

    /// Cellule de la colonne « référence » : **étoile dorée** si l'observation est archivée en référence,
    /// vide sinon (infobulle explicative).
    static TableCell<LigneObservationAudio, String> reference() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String valeur, boolean vide) {
                super.updateItem(valeur, vide);
                LigneObservationAudio ligne =
                        getTableRow() == null ? null : getTableRow().getItem();
                if (vide || ligne == null || !ligne.reference()) {
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    setGraphic(icone(ICONE_REFERENCE, STYLE_REFERENCE));
                    setTooltip(new Tooltip("Son de référence"));
                }
            }
        };
    }

    /// Cellule de la colonne « commentaire » : **icône bleue** si un commentaire est présent, avec le
    /// **texte complet** (lu sur la ligne) en infobulle ; vide sinon.
    static TableCell<LigneObservationAudio, String> commentaire() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String valeur, boolean vide) {
                super.updateItem(valeur, vide);
                LigneObservationAudio ligne =
                        getTableRow() == null ? null : getTableRow().getItem();
                if (vide || ligne == null || !estRenseigne(ligne.commentaire())) {
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    setGraphic(icone(ICONE_COMMENTAIRE, STYLE_COMMENTAIRE));
                    setTooltip(new Tooltip(ligne.commentaire()));
                }
            }
        };
    }
}

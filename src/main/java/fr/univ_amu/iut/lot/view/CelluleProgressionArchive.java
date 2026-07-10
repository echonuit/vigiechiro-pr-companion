package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.lot.viewmodel.EtatArchive;
import fr.univ_amu.iut.lot.viewmodel.LigneArchive;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.javafx.FontIcon;

/// Cellule de la colonne « Progression » de la table de suivi du dépôt (#820) : rend l'archive selon son
/// état — **barre de progression vive** tant qu'elle se compresse (« en cours »), sinon une **icône +
/// libellé** (horloge « en attente », coche « terminée », croix « échec » avec la raison en infobulle).
///
/// Réagit **en place** aux changements d'état et de fraction de la ligne (la compression étant parallèle
/// #814, une même ligne évolue pendant qu'elle est affichée). La **couleur** vient des classes CSS d'état
/// posées sur la ligne par [TableSuiviArchives] (`.ligne-archive.etat-…`), pas de la cellule.
final class CelluleProgressionArchive extends TableCell<LigneArchive, LigneArchive> {

    private final ProgressBar barre = new ProgressBar(0);
    private final FontIcon icone = new FontIcon();
    private final Label libelle = new Label();
    private final HBox contenu = new HBox(6, icone, libelle);
    private final ChangeListener<Object> auChangement = (obs, avant, apres) -> rendre();
    private LigneArchive ligne;

    CelluleProgressionArchive() {
        contenu.setAlignment(Pos.CENTER_LEFT);
        libelle.getStyleClass().add("libelle-etat-archive");
        icone.getStyleClass().add("icone-etat-archive");
        barre.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(barre, Priority.ALWAYS);
    }

    @Override
    protected void updateItem(LigneArchive item, boolean vide) {
        super.updateItem(item, vide);
        if (ligne != null) {
            ligne.etatProperty().removeListener(auChangement);
            ligne.fractionProperty().removeListener(auChangement);
        }
        ligne = vide ? null : item;
        if (ligne == null) {
            setGraphic(null);
            setTooltip(null);
            return;
        }
        ligne.etatProperty().addListener(auChangement);
        ligne.fractionProperty().addListener(auChangement);
        rendre();
    }

    private void rendre() {
        if (ligne == null) {
            return;
        }
        EtatArchive etat = ligne.etatProperty().get();
        if (etat == EtatArchive.EN_COURS) {
            barre.setProgress(ligne.fractionProperty().get());
            contenu.getChildren().setAll(barre);
            setTooltip(null);
        } else {
            icone.setIconLiteral(iconePour(etat));
            libelle.setText(etat.libelle());
            contenu.getChildren().setAll(icone, libelle);
            setTooltip(
                    etat == EtatArchive.ECHEC
                            ? new Tooltip(ligne.raisonEchecProperty().get())
                            : null);
        }
        setGraphic(contenu);
    }

    /// Icône FontAwesome5 (Ikonli) selon l'état ; « en cours » n'atteint pas cette branche (barre affichée).
    private static String iconePour(EtatArchive etat) {
        return switch (etat) {
            case TERMINEE -> "fas-check-circle";
            case ECHEC -> "fas-times-circle";
            default -> "fas-clock"; // EN_ATTENTE
        };
    }
}

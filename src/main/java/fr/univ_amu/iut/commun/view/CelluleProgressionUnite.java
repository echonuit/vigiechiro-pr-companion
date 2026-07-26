package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.EtatUnite;
import fr.univ_amu.iut.commun.viewmodel.LigneSuivi;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.javafx.FontIcon;

/// Cellule de la colonne « Progression » d'une table de suivi par unité : rend l'unité selon son état —
/// **barre de progression vive** tant qu'elle se traite (« en cours »), sinon une **icône + libellé**
/// (horloge « en attente », coche « terminée », croix « échec » avec la raison en infobulle).
///
/// Réagit **en place** aux changements d'état et de fraction de la ligne (le travail amont pouvant être
/// parallèle #814, une même ligne évolue pendant qu'elle est affichée). La **couleur** vient des classes
/// CSS d'état posées sur la ligne par [TableSuivi] (`.ligne-suivi.etat-…`, dans `design.css`), pas de la
/// cellule.
final class CelluleProgressionUnite<L extends LigneSuivi> extends TableCell<L, L> {

    private final ProgressBar barre = new ProgressBar(0);
    private final FontIcon icone = new FontIcon();
    private final Label libelle = new Label();

    /// Mention discrète de reprise réseau (#2354), affichée à côté de la barre pendant qu'une coupure
    /// momentanée est réessayée. Vide (donc absente) en temps normal.
    private final Label reprise = new Label();

    private final HBox contenu = new HBox(6, icone, libelle);
    private final ChangeListener<Object> auChangement = (obs, avant, apres) -> rendre();
    private LigneSuivi ligne;

    CelluleProgressionUnite() {
        contenu.setAlignment(Pos.CENTER_LEFT);
        libelle.getStyleClass().add("libelle-etat-unite");
        icone.getStyleClass().add("icone-etat-unite");
        reprise.getStyleClass().add("reprise-unite");
        barre.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(barre, Priority.ALWAYS);
    }

    @Override
    protected void updateItem(L item, boolean vide) {
        super.updateItem(item, vide);
        if (ligne != null) {
            ligne.etatProperty().removeListener(auChangement);
            ligne.fractionProperty().removeListener(auChangement);
            ligne.messageRepriseProperty().removeListener(auChangement);
        }
        ligne = vide ? null : item;
        if (ligne == null) {
            setGraphic(null);
            setTooltip(null);
            return;
        }
        ligne.etatProperty().addListener(auChangement);
        ligne.fractionProperty().addListener(auChangement);
        ligne.messageRepriseProperty().addListener(auChangement);
        rendre();
    }

    private void rendre() {
        if (ligne == null) {
            return;
        }
        EtatUnite etat = ligne.etatProperty().get();
        if (etat == EtatUnite.EN_COURS) {
            barre.setProgress(ligne.fractionProperty().get());
            String note = ligne.messageRepriseProperty().get();
            if (note == null || note.isEmpty()) {
                contenu.getChildren().setAll(barre);
            } else {
                // Reprise réseau en cours (#2354) : la barre reste, la mention discrète s'affiche à côté.
                reprise.setText(note);
                contenu.getChildren().setAll(barre, reprise);
            }
            setTooltip(null);
        } else {
            icone.setIconLiteral(iconePour(etat));
            libelle.setText(etat.libelle());
            contenu.getChildren().setAll(icone, libelle);
            setTooltip(
                    etat == EtatUnite.ECHEC
                            ? new Tooltip(ligne.raisonEchecProperty().get())
                            : null);
        }
        setGraphic(contenu);
    }

    /// Icône FontAwesome5 (Ikonli) selon l'état ; « en cours » n'atteint pas cette branche (barre affichée).
    private static String iconePour(EtatUnite etat) {
        return switch (etat) {
            case TERMINEE -> "fas-check-circle";
            case ECHEC -> "fas-times-circle";
            default -> "fas-clock"; // EN_ATTENTE
        };
    }
}

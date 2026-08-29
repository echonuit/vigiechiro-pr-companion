package fr.univ_amu.iut.qualification.view;

import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import java.util.Locale;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

/// Rendu et câblage du **verdict par fichier** (#1524, lot 6) dans M-Qualification : les trois
/// boutons « Bon / Mauvais / Inexploitable » qui jugent la **séquence courante**, et le badge de la
/// colonne « Verdict » de la liste de sélection.
///
/// Tenu hors du contrôleur, sur le patron déjà en service de [Feux]. Aucune logique métier : le
/// verdict est enregistré par [SelectionEcouteViewModel#marquerVerdictCourante], ce câblage ne fait
/// que relier les contrôles au ViewModel et refléter son état en retour.
final class VerdictParFichier {

    private VerdictParFichier() {}

    /// Câble les trois boutons (juger la séquence courante) **et** la colonne badge en une passe.
    static void lier(
            SelectionEcouteViewModel selectionVm,
            Button bon,
            Button mauvais,
            Button inexploitable,
            TableColumn<SequenceEnSelection, VerdictFichier> colVerdict) {
        lierBoutons(selectionVm, bon, mauvais, inexploitable);
        lierColonne(colVerdict);
    }

    /// Chaque bouton enregistre le verdict par fichier de la séquence courante ; en retour, le bouton
    /// du verdict courant est mis en évidence (`verdict-fichier-actif`) et les trois sont désactivés
    /// tant qu'aucune séquence n'est sélectionnée (rien à juger).
    private static void lierBoutons(
            SelectionEcouteViewModel selectionVm, Button bon, Button mauvais, Button inexploitable) {
        bon.setOnAction(evenement -> selectionVm.marquerVerdictCourante(VerdictFichier.BON));
        mauvais.setOnAction(evenement -> selectionVm.marquerVerdictCourante(VerdictFichier.MAUVAIS));
        inexploitable.setOnAction(evenement -> selectionVm.marquerVerdictCourante(VerdictFichier.INEXPLOITABLE));
        var courante = selectionVm.sequenceCouranteProperty();
        courante.addListener((obs, ancien, nouveau) -> refleterEtat(nouveau, bon, mauvais, inexploitable));
        refleterEtat(courante.get(), bon, mauvais, inexploitable);
    }

    private static void refleterEtat(SequenceEnSelection courante, Button bon, Button mauvais, Button inexploitable) {
        boolean aucune = courante == null;
        bon.setDisable(aucune);
        mauvais.setDisable(aucune);
        inexploitable.setDisable(aucune);
        VerdictFichier verdict = aucune ? null : courante.verdict();
        marquerActif(bon, verdict == VerdictFichier.BON);
        marquerActif(mauvais, verdict == VerdictFichier.MAUVAIS);
        marquerActif(inexploitable, verdict == VerdictFichier.INEXPLOITABLE);
    }

    private static void marquerActif(Button bouton, boolean actif) {
        bouton.getStyleClass().remove("verdict-fichier-actif");
        if (actif) {
            bouton.getStyleClass().add("verdict-fichier-actif");
        }
    }

    /// La colonne « Verdict » affiche un badge coloré selon le verdict par fichier de la ligne
    /// ([VerdictFichier]) ; `NON_JUGE` reste discret (aucune écoute encore rendue).
    /// Ce que la colonne d'avis affiche : le verdict du relecteur **et** son pseudo, ou rien.
    ///
    /// Rien, et non un badge vide : une séquence que personne n'a relue ne doit pas se lire comme un
    /// jugement. La distinction « pas relu » contre « relu et non jugé » n'existe pas en base, où
    /// [fr.univ_amu.iut.qualification.model.dao.SelectionDao] efface le pseudo avec le verdict
    /// (#4624) ; celle qui reste est « un avis existe » contre « aucun ».
    ///
    /// @param ligne la ligne de la sélection
    /// @return le libellé de l'avis, vide s'il n'y en a pas
    static String libelleAvis(SequenceEnSelection ligne) {
        return ligne.porteUnAvisDeRelecteur()
                ? ligne.verdictRelecteur().libelle() + " · " + ligne.pseudoRelecteur()
                : "";
    }

    /// Câble la colonne d'avis : la cellule reçoit la **ligne entière**, car le verdict seul ne dit
    /// pas qui l'a posé.
    static void lierColonneAvis(TableColumn<SequenceEnSelection, SequenceEnSelection> colAvis) {
        colAvis.setCellValueFactory(cellule -> new ReadOnlyObjectWrapper<>(cellule.getValue()));
        colAvis.setCellFactory(colonne -> new BadgeAvis());
    }

    private static final class BadgeAvis extends TableCell<SequenceEnSelection, SequenceEnSelection> {
        @Override
        protected void updateItem(SequenceEnSelection ligne, boolean vide) {
            super.updateItem(ligne, vide);
            if (vide || ligne == null || !ligne.porteUnAvisDeRelecteur()) {
                setGraphic(null);
                return;
            }
            Label badge = new Label(libelleAvis(ligne));
            badge.getStyleClass().addAll("badge-verdict", "badge-verdict-" + suffixe(ligne.verdictRelecteur()));
            setGraphic(badge);
        }
    }

    private static void lierColonne(TableColumn<SequenceEnSelection, VerdictFichier> colVerdict) {
        colVerdict.setCellValueFactory(
                cellule -> new ReadOnlyObjectWrapper<>(cellule.getValue().verdict()));
        colVerdict.setCellFactory(colonne -> new BadgeVerdict());
    }

    /// Cellule badge : un [Label] stylé par le suffixe du verdict (`badge-verdict-bon`, …), aligné sur
    /// la palette des feux du pré-check.
    private static final class BadgeVerdict extends TableCell<SequenceEnSelection, VerdictFichier> {
        @Override
        protected void updateItem(VerdictFichier verdict, boolean vide) {
            super.updateItem(verdict, vide);
            if (vide || verdict == null) {
                setGraphic(null);
                return;
            }
            Label badge = new Label(verdict.libelle());
            badge.getStyleClass().addAll("badge-verdict", "badge-verdict-" + suffixe(verdict));
            setGraphic(badge);
        }
    }

    private static String suffixe(VerdictFichier verdict) {
        return verdict.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}

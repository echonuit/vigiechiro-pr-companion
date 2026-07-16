package fr.univ_amu.iut.qualification.view;

import fr.univ_amu.iut.commun.model.Verdict;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;

/// Rendu de la puce « Verdict proposé » de M-Qualification (#1524, lot 6a) : affiche le verdict final
/// **dérivé** des verdicts par fichier de la sélection ([Verdict], proposé seulement), coloré selon sa
/// valeur (OK / Douteux / À jeter) et masqué tant qu'aucune séquence n'est jugée ([Verdict#A_VERIFIER]).
///
/// Extrait de [QualificationController] pour garder le contrôleur sous le plafond de taille (PMD
/// `NcssCount`), sur le patron de [Feux]. Purement présentation : la dérivation vit dans le ViewModel.
final class VerdictPropose {

    private VerdictPropose() {}

    /// Lie la puce au verdict proposé et à l'état de surcharge : texte (« Proposé : X », suffixé
    /// « (surchargé) » quand le choix diffère du proposé), visibilité (masquée si rien n'est jugé) et
    /// classe de couleur, appliqués à l'état courant puis à chaque changement.
    static void lier(Label puce, ObservableValue<Verdict> propose, ObservableValue<Boolean> surcharge) {
        puce.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> texte(propose.getValue(), Boolean.TRUE.equals(surcharge.getValue())),
                        propose,
                        surcharge));
        var visible = Bindings.createBooleanBinding(() -> estDecisif(propose.getValue()), propose);
        puce.visibleProperty().bind(visible);
        puce.managedProperty().bind(visible);
        appliquerClasse(puce, propose.getValue());
        propose.addListener((obs, ancien, nouveau) -> appliquerClasse(puce, nouveau));
    }

    private static boolean estDecisif(Verdict verdict) {
        return verdict != null && verdict != Verdict.A_VERIFIER;
    }

    private static String texte(Verdict verdict, boolean surcharge) {
        if (!estDecisif(verdict)) {
            return "";
        }
        return "Proposé : " + verdict.libelle() + (surcharge ? " (surchargé)" : "");
    }

    private static void appliquerClasse(Label puce, Verdict verdict) {
        puce.getStyleClass().removeAll("propose-ok", "propose-douteux", "propose-jeter");
        if (verdict == Verdict.OK) {
            puce.getStyleClass().add("propose-ok");
        } else if (verdict == Verdict.DOUTEUX) {
            puce.getStyleClass().add("propose-douteux");
        } else if (verdict == Verdict.A_JETER) {
            puce.getStyleClass().add("propose-jeter");
        }
    }
}

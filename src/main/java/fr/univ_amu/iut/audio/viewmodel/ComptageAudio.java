package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;

/// Compteurs de progression de la revue audio regroupés en un **seul value object** : total, validées
/// (R15) et corrigées (R16), plus le libellé d'avancement. Pendant de
/// `validation.viewmodel.ComptageRevue`, mais pour le record unifié [LigneObservationAudio].
///
/// Exposé tel quel par le [AudioViewModel] (une propriété au lieu de quatre), ce qui sort le calcul du
/// ViewModel et y regroupe une donnée cohésive plutôt que des compteurs épars.
///
/// @param total nombre total d'observations de la source
/// @param validees nombre d'observations validées
/// @param corrigees nombre d'observations corrigées
public record ComptageAudio(int total, int validees, int corrigees) {

    /// Comptage neutre (aucune observation chargée).
    public static final ComptageAudio VIDE = new ComptageAudio(0, 0, 0);

    /// Compte les lignes par statut de revue.
    public static ComptageAudio de(List<LigneObservationAudio> lignes) {
        int validees = 0;
        int corrigees = 0;
        for (LigneObservationAudio ligne : lignes) {
            if (ligne.statut() == StatutObservation.VALIDEE) {
                validees++;
            } else if (ligne.statut() == StatutObservation.CORRIGEE) {
                corrigees++;
            }
        }
        return new ComptageAudio(lignes.size(), validees, corrigees);
    }

    /// Avancement « N / T revues », vide tant qu'aucune observation n'est chargée.
    public String progression() {
        return total == 0 ? "" : (validees + corrigees) + " / " + total + " revues";
    }
}

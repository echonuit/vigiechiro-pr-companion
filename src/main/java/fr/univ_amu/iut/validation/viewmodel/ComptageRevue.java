package fr.univ_amu.iut.validation.viewmodel;

import fr.univ_amu.iut.validation.model.ObservationStatut;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;

/// Compteurs dérivés de l'avancement de la revue (total, validées, corrigées) et libellé de
/// progression, calculés sur la **liste complète** des observations d'un passage.
///
/// Sorti du [ValidationViewModel] pour alléger sa responsabilité (même esprit que
/// [FormatObservation]) : le calcul agrégé vit ici, le ViewModel se contente de pousser les valeurs
/// dans ses propriétés observables.
///
/// @param total nombre total d'observations
/// @param validees nombre d'observations validées (R15)
/// @param corrigees nombre d'observations corrigées (R16)
public record ComptageRevue(int total, int validees, int corrigees) {

    /// Compte les observations par statut de revue.
    public static ComptageRevue de(List<ObservationStatut> observations) {
        int validees = compter(observations, StatutObservation.VALIDEE);
        int corrigees = compter(observations, StatutObservation.CORRIGEE);
        return new ComptageRevue(observations.size(), validees, corrigees);
    }

    /// Avancement de la revue (`N / T revues`), vide tant qu'aucune observation n'est chargée.
    public String progression() {
        return total == 0 ? "" : (validees + corrigees) + " / " + total + " revues";
    }

    private static int compter(List<ObservationStatut> observations, StatutObservation statut) {
        return (int) observations.stream().filter(o -> o.statut() == statut).count();
    }
}

package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import java.util.function.Predicate;

/// Compteur des observations d'**espèces à enjeu** dans le sous-ensemble affiché (#2353) : combien il y
/// en a, et surtout **combien restent à revoir**.
///
/// Compteur **séparé** de [ComptageAudio], et non une composante de plus : il ne répond pas à la même
/// question. « 80 / 142 revues » dit où en est la revue ; « 12 à enjeu, 11 à revoir » dit ce qu'il reste
/// à regarder **en priorité**. Sur une nuit à quelques milliers de contacts, la seconde est une
/// information de pilotage, pas une décoration : elle survit quand la première n'est qu'un pourcentage.
///
/// @param total observations d'espèces prioritaires dans le sous-ensemble affiché
/// @param aRevoir celles qui n'ont encore été ni validées ni corrigées
public record ComptageEnjeu(int total, int aRevoir) {

    /// Aucune espèce à enjeu dans le sous-ensemble : le cas le plus courant sur un jeu de bruit.
    public static final ComptageEnjeu AUCUN = new ComptageEnjeu(0, 0);

    /// Compte les lignes dont le taxon retenu est prioritaire, et celles d'entre elles qui restent à
    /// revoir. Le prédicat vient de la vue, qui tient le référentiel.
    public static ComptageEnjeu de(List<LigneObservationAudio> lignes, Predicate<LigneObservationAudio> aEnjeu) {
        int total = 0;
        int aRevoir = 0;
        for (LigneObservationAudio ligne : lignes) {
            if (aEnjeu.test(ligne)) {
                total++;
                if (ligne.statut() != StatutObservation.VALIDEE && ligne.statut() != StatutObservation.CORRIGEE) {
                    aRevoir++;
                }
            }
        }
        return new ComptageEnjeu(total, aRevoir);
    }

    /// Libellé de barre de statut, **vide** s'il n'y a aucune espèce à enjeu : un « 0 à enjeu » permanent
    /// occuperait la place sans rien apprendre, et finirait par ne plus être lu.
    ///
    /// Dit ce qui **reste**, parce que c'est ce sur quoi on agit. Quand il ne reste rien, il le dit
    /// plutôt que d'afficher un « 0 à revoir » qu'il faudrait interpréter.
    public String libelle() {
        if (total == 0) {
            return "";
        }
        return aRevoir == 0 ? total + " à enjeu, toutes revues" : total + " à enjeu, " + aRevoir + " à revoir";
    }
}

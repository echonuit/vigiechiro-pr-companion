package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.DisponibiliteAudio;

/// Règles **pures** d'activation de la réactivation d'un passage (#1302), avec le **motif** de son
/// blocage. Extraites de [PassageViewModel] pour qu'il garde la seule responsabilité de porter l'état
/// observable de l'écran (PMD GodClass).
///
/// Le gating est **en amont** (#789) : on grise avec une explication plutôt que de laisser
/// l'utilisateur découvrir le refus après avoir confirmé.
final class GatingReactivation {

    private GatingReactivation() {}

    /// Réactiver n'a de sens que s'il **manque** de l'audio à un passage qui en a (séquences
    /// persistées) : audio absent (`ABSENTE`) ou disque incomplet (`PARTIELLE`).
    static boolean reactivationPossible(DetailPassage detail) {
        return detail.decompteAudio().total() > 0
                && detail.decompteAudio().disponibilite() != DisponibiliteAudio.COMPLETE;
    }

    /// Motif du blocage de la réactivation ; chaîne vide quand elle est possible.
    static String motifReactivation(DetailPassage detail) {
        if (detail.decompteAudio().total() == 0) {
            return "Rien à réactiver : ce passage n'a aucune séquence importée localement.";
        }
        if (detail.decompteAudio().disponibilite() == DisponibiliteAudio.COMPLETE) {
            return "Rien à réactiver : l'audio de ce passage est déjà sur le disque.";
        }
        return "";
    }
}

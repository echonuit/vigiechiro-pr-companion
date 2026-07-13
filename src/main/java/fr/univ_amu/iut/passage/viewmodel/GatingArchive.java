package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.DisponibiliteAudio;

/// Règles **pures** d'activation des actions d'archive de M-Passage : archiver (#1300) et réactiver
/// (#1302), chacune avec le **motif** de son blocage. Extraites de [PassageViewModel] pour qu'il
/// garde la seule responsabilité de porter l'état observable de l'écran (PMD GodClass).
///
/// Le gating est **en amont** (#789) : on grise avec une explication plutôt que de laisser
/// l'utilisateur découvrir le refus après avoir confirmé.
final class GatingArchive {

    private GatingArchive() {}

    /// Archiver n'a de sens que sur un passage **déposé** (l'audio est nécessaire jusqu'à l'analyse
    /// par la plateforme) qui **conserve encore** de l'audio.
    static boolean archivagePossible(DetailPassage detail) {
        return detail.statut() == StatutWorkflow.DEPOSE && audioConserve(detail);
    }

    /// Motif du blocage de l'archivage ; chaîne vide quand il est possible.
    static String motifArchivage(DetailPassage detail) {
        if (detail.statut() != StatutWorkflow.DEPOSE) {
            return "Archivage impossible : le passage n'est pas encore déposé. L'audio est nécessaire"
                    + " jusqu'au dépôt et à l'analyse par la plateforme.";
        }
        if (!audioConserve(detail)) {
            return "Déjà archivé : l'audio de ce passage n'est plus conservé localement."
                    + " Réimportez les fichiers d'origine pour le réactiver.";
        }
        return "";
    }

    /// Réactiver n'a de sens que s'il **manque** de l'audio à un passage qui en a (séquences
    /// persistées) : passage archivé (`ABSENTE`) ou disque incomplet (`PARTIELLE`).
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

    /// Le passage conserve-t-il de l'audio (bruts ou séquences) sur le disque ?
    private static boolean audioConserve(DetailPassage detail) {
        return detail.volumeSequencesOctets() > 0 || detail.volumeOriginauxOctets() > 0;
    }
}

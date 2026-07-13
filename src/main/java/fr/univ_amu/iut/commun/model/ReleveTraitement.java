package fr.univ_amu.iut.commun.model;

import fr.univ_amu.iut.commun.api.Traitement;
import java.util.Objects;

/// **Ce que la plateforme disait du traitement d'une nuit, la dernière fois qu'on le lui a demandé**
/// (table `participation_traitement`, #1262).
///
/// Un relevé, pas une vérité : l'analyse Tadarida appartient au serveur, qui peut la faire avancer (ou
/// la recommencer) sans nous prévenir. On garde donc l'état **avec la date à laquelle on l'a lu**, ce qui
/// permet de l'afficher hors connexion et à la réouverture de l'application — et de dire honnêtement
/// « dernier état connu le … » plutôt que de laisser croire à une information fraîche.
///
/// Le passage est la clé : une nuit, une participation. Chaque rafraîchissement **écrase** le relevé
/// précédent (on ne tient pas d'historique : la question posée est « où en est-on ? », pas « par où
/// est-on passé ? »).
///
/// @param idPassage passage relevé (clé)
/// @param participationId participation VigieChiro correspondante, telle que connue au moment du relevé
/// @param traitement état rapporté par le serveur, jamais `null` ([Traitement#absent()] si la
///     participation n'avait jamais été calculée)
/// @param releveLe horodatage ISO de **notre lecture**, jamais `null`
public record ReleveTraitement(Long idPassage, String participationId, Traitement traitement, String releveLe) {

    public ReleveTraitement {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(participationId, "participationId");
        Objects.requireNonNull(traitement, "traitement");
        Objects.requireNonNull(releveLe, "releveLe");
    }
}

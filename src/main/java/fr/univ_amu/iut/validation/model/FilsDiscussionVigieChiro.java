package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.MessageVigieChiro;
import fr.univ_amu.iut.commun.api.ObservationVigieChiro;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.validation.model.dao.MessageObservationDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Écrit les **fils de discussion** d'une participation VigieChiro dans la base, une fois ses
/// observations importées (#1417). Collaborateur interne de [ServiceValidation], sur le modèle de
/// [PreservationValidations] : l'import garde son cœur commun, et le fil — qui n'existe que côté
/// VigieChiro — reste à côté.
///
/// **Pourquoi à part, et après.** Le fil est un 1-N : il ne peut pas voyager dans une
/// [LigneObservation] (projection à plat, partagée avec le CSV Tadarida, qui n'a pas de fil). Et il ne
/// peut pas être écrit *pendant* l'insertion des observations : celle-ci se fait en lot, sans récupérer
/// les clés générées. On relit donc les observations fraîchement insérées et on les rapproche des
/// données serveur par leur **ancrage plateforme** (`_id` de la donnée + indice brut, V21/#1139) — le
/// même couple qui sert de cible au `PATCH` des corrections.
///
/// Rien n'est écrit quand aucune observation n'a de message, ce qui est le cas le plus courant : pas de
/// transaction ouverte pour rien.
final class FilsDiscussionVigieChiro {

    private final ObservationDao observationDao;
    private final MessageObservationDao messageDao;
    private final UniteDeTravail uniteDeTravail;

    FilsDiscussionVigieChiro(
            ObservationDao observationDao, MessageObservationDao messageDao, UniteDeTravail uniteDeTravail) {
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
        this.messageDao = Objects.requireNonNull(messageDao, "messageDao");
        this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
    }

    /// Enregistre les fils des observations du jeu de résultats `idResultats`, tels que le serveur les a
    /// renvoyés. Le fil local est **remplacé**, jamais fusionné : c'est un reflet du serveur, qui fait
    /// foi (un message n'a pas d'identité stable côté serveur — l'ancrage est positionnel, l'ajout se
    /// fait par `$push` — donc rien ne permettrait de rapprocher deux versions d'un même fil autrement
    /// qu'en inventant une identité).
    ///
    /// @return le nombre d'**observations** dont un fil a été écrit. C'est ce compte, et non celui des
    ///     messages, qui sert l'annonce faite à l'observateur (#1867) : il lui dit **où regarder**. Le
    ///     compte des messages exigerait de savoir lesquels sont les siens, donc de connaître le profil
    ///     connecté - une dépendance que ce collaborateur n'a pas, pour une information moins utile.
    int enregistrer(Long idResultats, List<DonneeVigieChiro> donnees) {
        Map<Ancrage, List<MessageVigieChiro>> filsServeur = filsParAncrage(donnees);
        if (filsServeur.isEmpty()) {
            return 0;
        }
        List<Observation> inserees = observationDao.findByResults(idResultats);
        int[] ecrits = {0};
        uniteDeTravail.executer(connexion -> {
            for (Observation observation : inserees) {
                List<MessageVigieChiro> fil =
                        filsServeur.get(new Ancrage(observation.idDonneeVigieChiro(), observation.indiceVigieChiro()));
                if (fil != null && !fil.isEmpty()) {
                    messageDao.remplacerFil(connexion, observation.id(), enMessages(observation.id(), fil));
                    ecrits[0]++;
                }
            }
        });
        return ecrits[0];
    }

    /// Fils **non vides** du serveur, indexés par leur ancrage plateforme. Les observations sans message
    /// (l'immense majorité) n'apparaissent pas : la carte est vide dès qu'aucun fil n'a été ouvert, ce qui
    /// permet à [#enregistrer] de ne rien faire du tout.
    private static Map<Ancrage, List<MessageVigieChiro>> filsParAncrage(List<DonneeVigieChiro> donnees) {
        Map<Ancrage, List<MessageVigieChiro>> fils = new HashMap<>();
        for (DonneeVigieChiro donnee : donnees) {
            for (ObservationVigieChiro observation : donnee.observations()) {
                if (!observation.messages().isEmpty()) {
                    fils.put(new Ancrage(donnee.id(), observation.indiceServeur()), observation.messages());
                }
            }
        }
        return fils;
    }

    /// Convertit un fil serveur en messages persistables. Le **rang** est la position dans le tableau du
    /// serveur : l'ajout s'y faisant par `$push`, cet ordre **est** l'ordre chronologique — plus fiable
    /// qu'un tri sur des dates que le serveur ne garantit pas toutes.
    private static List<MessageObservation> enMessages(Long idObservation, List<MessageVigieChiro> fil) {
        List<MessageObservation> messages = new ArrayList<>(fil.size());
        for (int rang = 0; rang < fil.size(); rang++) {
            MessageVigieChiro message = fil.get(rang);
            messages.add(new MessageObservation(
                    null, idObservation, rang, message.auteur(), message.texte(), message.date()));
        }
        return messages;
    }

    /// Ancrage plateforme d'une observation (`_id` de la donnée + indice brut, #1139) : la clé de
    /// rapprochement entre ce que le serveur a envoyé et ce que la base vient d'insérer.
    private record Ancrage(String idDonnee, Integer indice) {}
}

package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;

/// Projection de lecture pour l'écran **M-Passage** : agrège un passage et sa session
/// d'enregistrement (volumes, durée enregistrée, nombre de séquences).
///
/// **Sans jointure vers `sites`** : le carré et le code du point sont fournis à la vue par le
/// contexte de navigation (depuis M-Site-detail), pour éviter un cycle `passage → sites`. Produite
/// par [ServicePassage#detailPassage(Long)] : immuable, sans dépendance JavaFX.
///
/// @param numeroPassage n° du passage dans l'année
/// @param annee année du protocole
/// @param dateEnregistrement date de la nuit (ISO `AAAA-MM-JJ`)
/// @param heureDebut heure de début théorique (`HH:MM:SS`)
/// @param heureFin heure de fin théorique (`HH:MM:SS`)
/// @param idEnregistreur n° de série de l'enregistreur (Passive Recorder)
/// @param statut statut workflow courant
/// @param verdict verdict de vérification, ou `null` tant qu'aucun n'est posé
/// @param deposeLe date de dépôt, ou `null` si non déposé
/// @param volumeOriginauxOctets volume des enregistrements bruts (0 si inconnu)
/// @param volumeSequencesOctets volume des séquences transformées (0 si inconnu)
/// @param nombreSequences nombre de séquences d'écoute de la session
/// @param dureeEnregistreeSecondes durée enregistrée cumulée des séquences (secondes)
/// @param decompteAudio disponibilité **observée** de l'audio sur disque (#1298), pour gater l'écoute
///     et la réactivation (#1302) sans refaire un balayage dans la couche IHM
/// @param meteo relevé météo optionnel du passage (température début/fin, vent, couverture nuageuse ;
/// #106 étendu) — jamais `null`, mais chacune de ses grandeurs peut l'être
public record DetailPassage(
        int numeroPassage,
        int annee,
        String dateEnregistrement,
        String heureDebut,
        String heureFin,
        String idEnregistreur,
        StatutWorkflow statut,
        Verdict verdict,
        String deposeLe,
        long volumeOriginauxOctets,
        long volumeSequencesOctets,
        int nombreSequences,
        double dureeEnregistreeSecondes,
        MeteoReleve meteo,
        DecompteAudio decompteAudio) {}

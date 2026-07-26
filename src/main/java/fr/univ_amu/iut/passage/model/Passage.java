package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;

/// Passage : une nuit complète d'enregistrement sur un point d'écoute, avec un enregistreur, lors
/// d'un n° de passage donné dans une année (C5, table `passage`). **Entité centrale** du modèle :
/// la session d'enregistrement, la sélection d'écoute et les résultats d'identification gravitent
/// autour de lui.
///
/// Le « quadruplet » `(point, année, n° de passage)` est unique (R5, contrainte
/// `UNIQUE(point_id, year, passage_number)`) : un point ne peut pas avoir deux passages de même
/// numéro dans la même année.
///
/// Les champs [#parametresAcquisition] et [#donneesMeteo] sont des structures sérialisées en
/// `TEXT` JSON (Fe, gain, météo…) : le modèle les transporte tels quels, leur interprétation
/// relève d'une couche service.
///
/// @param id clé technique, `null` avant insertion
/// @param numeroPassage n° de passage dans l'année (typiquement 1 ou 2, R3)
/// @param annee année sur 4 chiffres (ex. 2026)
/// @param dateEnregistrement date du soir où l'enregistrement démarre (ISO `AAAA-MM-JJ`)
/// @param heureDebut heure de début lue du journal (ISO `HH:MM:SS`)
/// @param heureFin heure de fin lue du journal (ISO `HH:MM:SS`)
/// @param parametresAcquisition paramètres d'acquisition sérialisés en JSON (optionnel)
/// @param statutWorkflow statut d'avancement dans le workflow d'import → dépôt
/// @param verdictVerification verdict de vérification (`null` tant que non vérifié)
/// @param commentaire commentaire de session (optionnel)
/// @param donneesMeteo données météo sérialisées en JSON (optionnel)
/// @param deposeLe date/heure de dépôt sur Vigie-Chiro (ISO, `null` tant que non déposé)
/// @param idPoint identifiant du point d'écoute (FK → `listening_point.id`)
/// @param idEnregistreur n° de série de l'enregistreur (FK → `recorder.serial_number`)
/// @param idCampagne identifiant de la campagne de rattachement (FK → `campagne.id`), `null` si le
///     passage n'est rattaché à aucune campagne (le rattachement est facultatif)
public record Passage(
        Long id,
        int numeroPassage,
        int annee,
        String dateEnregistrement,
        String heureDebut,
        String heureFin,
        String parametresAcquisition,
        StatutWorkflow statutWorkflow,
        Verdict verdictVerification,
        String commentaire,
        String donneesMeteo,
        String deposeLe,
        Long idPoint,
        String idEnregistreur,
        Long idCampagne) {}

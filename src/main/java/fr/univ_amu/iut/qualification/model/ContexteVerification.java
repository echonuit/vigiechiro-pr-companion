package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;

/// Contexte d'en-tête d'un passage à vérifier (bandeau de M-Qualification) : identité (carré du
/// site, code du point, n° de passage, année, date, plage horaire), volumétrie de la nuit
/// (séquences totales, durée audible) et état courant (statut workflow, verdict éventuel).
///
/// Projection de lecture pure produite par [ServiceQualification#chargerContexte] : immuable, sans
/// dépendance JavaFX (consommée par la couche viewmodel).
///
/// @param numeroCarre numéro de carré du site (6 chiffres), `?` si le site est introuvable
/// @param codePoint code du point d'écoute (R2), `?` si le point est introuvable
/// @param nomSite nom convivial du site (peut être vide), pour le contexte de navigation
/// @param numeroPassage numéro du passage dans l'année
/// @param annee année de campagne
/// @param date date d'enregistrement (`AAAA-MM-JJ`)
/// @param heureDebut heure de début déclarée
/// @param heureFin heure de fin déclarée
/// @param sequencesTotales nombre total de séquences d'écoute de la nuit
/// @param dureeAudibleSecondes durée audible cumulée des séquences (secondes)
/// @param statut statut workflow courant du passage
/// @param verdict verdict de vérification (`null` tant qu'aucun verdict n'est posé)
public record ContexteVerification(
    String numeroCarre,
    String codePoint,
    String nomSite,
    int numeroPassage,
    int annee,
    String date,
    String heureDebut,
    String heureFin,
    int sequencesTotales,
    double dureeAudibleSecondes,
    StatutWorkflow statut,
    Verdict verdict) {}

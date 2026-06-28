package fr.univ_amu.iut.validation.model;

/// Projection « **une observation d'une espèce, située dans son passage** » (#analyse, détail transverse).
/// Là où [EspeceAgregee] compte les détections d'une espèce, ce record en restitue le **détail ligne à
/// ligne** : chaque observation de l'espèce retenue, à travers tous les passages de l'utilisateur, avec
/// son contexte (passage / carré / point / date), les deux taxons (proposition Tadarida vs saisie
/// observateur) et le [StatutObservation] dérivé.
///
/// L'espèce retenue est, comme partout (#323), le taxon **validé** s'il existe sinon la proposition
/// Tadarida (`COALESCE(taxon_observer, taxon_tadarida)`). Les clés `idPassage` / `numeroCarre` /
/// `codePoint` / `nomSite` permettent d'**ouvrir le passage** (contrat socle `OuvrirPassage`) ;
/// `idObservation` / `idSequence` serviront à l'écoute audio et à la validation transverses (PR-D).
///
/// @param idObservation clé de l'observation (cible des actions de validation)
/// @param idSequence séquence d'écoute associée (cible de l'écoute audio)
/// @param idPassage passage où l'observation a été relevée (cible de navigation)
/// @param numeroPassage n° de passage dans l'année
/// @param annee année du passage
/// @param dateEnregistrement date d'enregistrement du passage (texte)
/// @param numeroCarre n° de carré du site du passage
/// @param codePoint code du point d'écoute du passage
/// @param nomSite nom convivial du site, ou `null`
/// @param taxonTadarida proposition automatique Tadarida (code, jamais nul)
/// @param probTadarida probabilité associée à la proposition Tadarida, ou `null`
/// @param taxonObservateur taxon saisi par l'observateur, ou `null` (non touchée)
/// @param probObservateur probabilité saisie par l'observateur, ou `null`
/// @param statut statut de revue dérivé (validée / corrigée / non touchée)
public record ObservationEspece(
        long idObservation,
        long idSequence,
        long idPassage,
        int numeroPassage,
        int annee,
        String dateEnregistrement,
        String numeroCarre,
        String codePoint,
        String nomSite,
        String taxonTadarida,
        Double probTadarida,
        String taxonObservateur,
        Double probObservateur,
        StatutObservation statut) {}

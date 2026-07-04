package fr.univ_amu.iut.validation.model;

/// Projection **unifiée** d'une observation pour la **vue audio** (#audio) : tout ce qu'il faut pour
/// l'écouter, la situer, la valider/corriger et l'archiver, **quelle que soit la source** (un passage, un
/// lot de passages, une espèce à travers les passages, ou le corpus de référence).
///
/// Superset des projections existantes (`ObservationStatut` de la validation n'a pas le contexte passage ;
/// `ObservationEspece` de l'analyse n'a pas `reference`/commentaire/fréquence). Introduite **standalone** :
/// les autres records restent inchangés. L'espèce retenue suit la convention habituelle
/// (`COALESCE(taxon_observer, taxon_tadarida)`) ; le `statut` est dérivé en SQL comme ailleurs.
///
/// @param idObservation clé de l'observation (cible de valider/corriger/marquer-référence)
/// @param idSequence séquence d'écoute associée (cible de l'écoute audio)
/// @param idPassage passage d'où vient l'observation (situer la ligne, « ouvrir le passage »)
/// @param numeroPassage n° de passage dans l'année
/// @param dateEnregistrement date d'enregistrement du passage (texte)
/// @param numeroCarre n° de carré du site du passage
/// @param codePoint code du point d'écoute du passage
/// @param nomSite nom convivial du site, ou `null`
/// @param taxonTadarida proposition automatique Tadarida (code, jamais nul)
/// @param probTadarida probabilité de la proposition Tadarida, ou `null`
/// @param taxonObservateur taxon saisi par l'observateur, ou `null` (non touchée)
/// @param probObservateur probabilité saisie par l'observateur, ou `null`
/// @param statut statut de revue dérivé (validée / corrigée / non touchée)
/// @param reference `true` si l'observation est dans le corpus de référence (`is_reference`)
/// @param commentaire commentaire libre de l'observateur, ou `null`
/// @param frequenceHz fréquence médiane en Hz, ou `null`
/// @param nomEspece nom vernaculaire FR de l'espèce **retenue** (`COALESCE(observateur, tadarida)`), ou
///     `null` si le taxon n'a pas de nom vernaculaire (souche hors référentiel) — la vue affiche alors le code
/// @param nomTadarida nom vernaculaire FR de la **proposition Tadarida** (`taxon_tadarida`), ou `null`
///     (souche hors référentiel) — la vue affiche alors le code
/// @param nomFichier nom de fichier de la séquence d'écoute (`listening_sequence.file_name`), pour relier
///     la ligne à l'enregistrement écouté
/// @param debutS début du cri dans la séquence en secondes (timeline **transformée** ×10), ou `null`
/// @param finS fin du cri dans la séquence en secondes (timeline **transformée** ×10), ou `null` — la durée
///     **réelle** du cri se déduit de `(finS − debutS)` divisé par le facteur d'expansion (cf. formatage)
public record LigneObservationAudio(
        long idObservation,
        long idSequence,
        long idPassage,
        int numeroPassage,
        String dateEnregistrement,
        String numeroCarre,
        String codePoint,
        String nomSite,
        String taxonTadarida,
        Double probTadarida,
        String taxonObservateur,
        Double probObservateur,
        StatutObservation statut,
        boolean reference,
        String commentaire,
        Integer frequenceHz,
        String nomEspece,
        String nomTadarida,
        String nomFichier,
        Double debutS,
        Double finS) {}

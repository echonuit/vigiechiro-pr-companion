package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.ModeValidation;

/// Observation : une ligne du fichier de résultats Tadarida, soumise à validation (C13, table
/// `observation`). Une séquence d'écoute peut générer plusieurs observations (1 ligne par espèce
/// détectée, avec timing début/fin).
///
/// Point remarquable du MCD : l'observation porte **trois** clés étrangères distinctes vers
/// [Taxon] :
///
/// - `taxonTadarida` (FK `taxon_tadarida`) : **obligatoire**, proposition de Tadarida ;
/// - `taxonAutreTadarida` (FK `taxon_other_tadarida`) : optionnel, 2e proposition ;
/// - `taxonObservateur` (FK `taxon_observer`) : optionnel, saisi en validation.
///
/// Le `modeValidation` est mappé via [ModeValidation] (colonne `validation_mode` ; `null` →
/// [ModeValidation#NON_VALIDE]). Les colonnes numériques optionnelles (`REAL` / `INTEGER`
/// nullable) sont des types wrapper, `null` si absentes.
///
/// @param id clé technique, `null` avant insertion
/// @param idSequence séquence d'écoute source (FK → `listening_sequence.id`, obligatoire)
/// @param debutS temps de début dans la séquence en secondes (optionnel)
/// @param finS temps de fin dans la séquence en secondes (optionnel)
/// @param frequenceMedianeKHz fréquence médiane en kHz (métrique Tadarida, optionnelle au schéma)
/// @param taxonTadarida code du taxon proposé par Tadarida (FK → `taxon.code`, obligatoire)
/// @param probTadarida probabilité Tadarida dans `[0,1]` (optionnelle)
/// @param taxonAutreTadarida code de la 2e proposition Tadarida (FK → `taxon.code`, optionnel)
/// @param taxonObservateur code saisi par l'observateur (FK → `taxon.code`, optionnel)
/// @param probObservateur probabilité saisie par l'observateur dans `[0,1]` (optionnelle)
/// @param commentaire commentaire libre de l'observateur (optionnel)
/// @param reference marquée comme référence dans la bibliothèque de sons (défaut `false`)
/// @param modeValidation mode de validation (R24 : manuel / auto / non validé)
/// @param idResultats résultats d'identification agrégateurs (FK → `identification_results.id`)
public record Observation(
        Long id,
        Long idSequence,
        Double debutS,
        Double finS,
        Integer frequenceMedianeKHz,
        String taxonTadarida,
        Double probTadarida,
        String taxonAutreTadarida,
        String taxonObservateur,
        Double probObservateur,
        String commentaire,
        boolean reference,
        ModeValidation modeValidation,
        Long idResultats) {}

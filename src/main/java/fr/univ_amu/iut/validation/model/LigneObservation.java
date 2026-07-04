package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.ModeValidation;

/// Projection d'une **ligne** du CSV Tadarida, avant persistance (résultat de
/// `ParserCsvTadarida`). C'est l'image fidèle d'une ligne, indépendante de la base : elle porte
/// le **nom de la séquence** source (colonne `nom du fichier`) plutôt qu'une clé technique
/// `idSequence`, car le parseur ne connaît pas encore la base.
///
/// Le service `ServiceValidation` convertit ensuite chaque [LigneObservation] en une
/// [Observation] complète une fois la séquence (`listening_sequence`) et les résultats
/// (`identification_results`) résolus en base. Inversement, l'export `ExportVuCsv` reconstitue
/// des [LigneObservation] à partir des [Observation] relues (le nom de séquence est restitué
/// depuis la `SequenceDao`).
///
/// Conventions de nullité (alignées sur les colonnes nullable du schéma) :
///
/// - `taxonAutreTadarida` : conservé **tel quel**, y compris quand Tadarida propose une liste de
///   candidats séparés par des virgules (ex. `"Tetvir, Pippip, Phogri"`) ; `null` si la colonne
///   est vide. (La persistance en [Observation] ne retient qu'un code FK unique : la conversion
///   incombe au service.)
/// - `taxonObservateur` / `probObservateur` : `null` tant que l'observateur n'a pas tranché (cas
///   d'un fichier Brut ou d'une ligne non touchée, R17).
/// - `frequenceMedianeKHz` : [Integer] (la colonne `median_freq_khz` est `INTEGER`) ; un éventuel
///   `"153.0"` du CSV est arrondi à l'entier le plus proche.
///
/// @param nomSequence nom de fichier de la séquence d'écoute source (sans clé technique)
/// @param debutS temps de début dans la séquence en secondes (optionnel)
/// @param finS temps de fin dans la séquence en secondes (optionnel)
/// @param frequenceMedianeKHz fréquence médiane (métrique Tadarida, optionnelle)
/// @param taxonTadarida code du taxon proposé par Tadarida (obligatoire)
/// @param probTadarida probabilité Tadarida dans `[0,1]` (optionnelle)
/// @param taxonAutreTadarida 2e proposition Tadarida, brute (optionnelle, parfois multi-valuée)
/// @param taxonObservateur code saisi par l'observateur (optionnel, R15/R16)
/// @param probObservateur probabilité saisie par l'observateur (optionnelle)
/// @param modeValidation mode de validation (R24 : manuel / auto / non validé)
public record LigneObservation(
        String nomSequence,
        Double debutS,
        Double finS,
        Integer frequenceMedianeKHz,
        String taxonTadarida,
        Double probTadarida,
        String taxonAutreTadarida,
        String taxonObservateur,
        Double probObservateur,
        ModeValidation modeValidation) {}

package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.ModeValidation;

/// Projection d'une ligne du CSV Tadarida avant persistance (résultat de `ParserCsvTadarida`) :
/// l'image fidèle d'une ligne, indépendante de la base. Elle porte le **nom de la séquence** et non
/// une clé technique, le parseur ne connaissant pas encore la base ; `ServiceValidation` la convertit
/// en [Observation], `ExportVuCsv` fait l'inverse.
///
/// Nullité alignée sur les colonnes nullable du schéma, et détaillée par les étiquettes ci-dessous.
/// `taxonAutreTadarida` est conservé **tel quel**, plusieurs candidats compris : [Observation] n'en
/// retient qu'un, la conversion incombe au service. `frequenceMedianeKHz` est un [Integer], un
/// `"153.0"` du CSV étant arrondi. L'ancrage plateforme (#1139) ne vient que de l'import VigieChiro.
///
/// @param nomSequence nom de fichier de la séquence d'écoute source (sans clé technique)
/// @param debutS temps de début dans la séquence en secondes (optionnel)
/// @param finS temps de fin dans la séquence en secondes (optionnel)
/// @param frequenceMedianeKHz fréquence médiane (métrique Tadarida, optionnelle)
/// @param taxonTadarida code du taxon proposé par Tadarida (obligatoire)
/// @param probTadarida probabilité Tadarida dans `[0,1]` (optionnelle)
/// @param taxonAutreTadarida 2e proposition Tadarida, brute (optionnelle, parfois multi-valuée)
/// @param taxonObservateur code saisi par l'observateur (optionnel, R15/R16)
/// @param probObservateur probabilité numérique observateur (optionnelle, héritage `_Vu`)
/// @param modeValidation mode de validation (R24 : manuel / auto / non validé)
/// @param idDonneeVigieChiro `_id` Eve de la donnée serveur source (optionnel, import VigieChiro)
/// @param indiceVigieChiro indice brut dans le tableau `observations` serveur (optionnel)
/// @param certitudeObservateur certitude déclarée par l'observateur (optionnelle)
/// @param taxonValidateur code **tranché par le validateur** du MNHN (#1417) : renseigné uniquement
///     par l'import VigieChiro, `null` pour un CSV (Tadarida ne connaît pas les validateurs)
/// @param certitudeValidateur certitude déclarée par le validateur (#1417), même domaine fermé que
///     celle de l'observateur ; `null` hors import VigieChiro
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
        ModeValidation modeValidation,
        String idDonneeVigieChiro,
        Integer indiceVigieChiro,
        Certitude certitudeObservateur,
        String taxonValidateur,
        Certitude certitudeValidateur) {}

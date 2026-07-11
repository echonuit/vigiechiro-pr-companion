package fr.univ_amu.iut.importation.model;

import java.nio.file.Path;

/// Une séquence d'écoute produite par [TransformationAudio] à partir d'un enregistrement original
/// (R10). C'est le résultat « brut moteur » : `ServiceImport` le convertit ensuite en entité
/// [fr.univ_amu.iut.passage.model.SequenceDEcoute] pour la persistance.
///
/// @param index index (≥ 0) de la séquence dans l'original source (R8 : suffixe `_000`, `_001`…)
/// @param nomFichier nom de fichier de la séquence (nom de l'original + suffixe `_NNN`, R8)
/// @param chemin chemin du fichier écrit dans `transformes/` (R22)
/// @param frequenceSortieHz fréquence d'échantillonnage de sortie (source / 10), ex. 38400
/// @param dureeSecondes durée **réelle** de la séquence, au rythme d'acquisition (≈ 5 s, la dernière peut
/// être plus courte) ; rejouée au rythme de sortie (Fe/10), elle dure ×10 à l'écoute (#1051)
/// @param offsetSourceSecondes position de la séquence dans le signal source, **avant** le ×10
/// @param octets taille du fichier écrit, en octets
public record SequenceProduite(
        int index,
        String nomFichier,
        Path chemin,
        int frequenceSortieHz,
        double dureeSecondes,
        double offsetSourceSecondes,
        long octets) {}

package fr.univ_amu.iut.importation.model;

import java.nio.file.Path;
import java.util.List;

/// Résultat de la transformation d'**un** enregistrement original : ses métadonnées (durée,
/// fréquence source, empreinte) et la liste des [SequenceProduite] obtenues par expansion ×10 et
/// découpage en tranches de 5 s (R10/R11).
///
/// @param nomOriginal nom de fichier de l'enregistrement original source
/// @param cheminOriginal chemin de l'original (dans `bruts/`) qui a été transformé
/// @param frequenceSourceHz fréquence d'échantillonnage de l'original (ex. 384000)
/// @param frequenceSortieHz fréquence d'échantillonnage des séquences (source / 10, ex. 38400)
/// @param dureeSourceSecondes durée du signal original, en secondes
/// @param sha256 empreinte SHA-256 hexadécimale de l'original (intégrité bit-à-bit)
/// @param tailleSourceOctets taille du fichier original, en octets (#1299 : pré-contrôle rapide
/// avant le [#sha256] intégral quand une réactivation repart des bruts)
/// @param sequences séquences produites, dans l'ordre des index
public record TransformationOriginal(
        String nomOriginal,
        Path cheminOriginal,
        int frequenceSourceHz,
        int frequenceSortieHz,
        double dureeSourceSecondes,
        String sha256,
        long tailleSourceOctets,
        List<SequenceProduite> sequences) {

    public TransformationOriginal {
        sequences = List.copyOf(sequences);
    }
}

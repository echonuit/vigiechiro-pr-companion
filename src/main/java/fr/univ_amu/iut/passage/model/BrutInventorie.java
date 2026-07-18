package fr.univ_amu.iut.passage.model;

import java.nio.file.Path;

/// Un enregistrement **brut** retrouvé dans le dossier désigné, prêt à être ré-intégré à un passage
/// reconstruit (#1649, EPIC #1653).
///
/// Un passage reconstruit (#1305) ne connaît que le **nom** de ses séquences ; il n'a jamais eu
/// l'inventaire de ses originaux. Pour repartir des bruts, il faut donc reconstituer, pour chaque
/// fichier trouvé, le **nom R6** que ses tranches porteront (celui que la base connaît) et **où** le
/// fichier se trouve réellement (la copie que l'utilisateur a gardée, hors du workspace).
///
/// @param source chemin du fichier brut **tel qu'il est**, dans le dossier désigné par l'utilisateur
/// @param nomOriginal nom R6 de l'original (`Car…-2026-Pass1-…-<enregistreur>.wav`) : c'est lui qui
///     nommera les tranches régénérées, donc qui permettra de les rebrancher sur les séquences existantes
/// @param nombreTrames nombre de trames du signal, lu dans l'**en-tête seul** (#1934), ou `null` si
///     l'en-tête n'a pas pu être lu. Il donne la durée, donc le nombre de tranches, donc les noms que
///     l'arbitrage des collisions doit réserver pour ce brut. Il faut le connaître pour **tous** les bruts
///     de la nuit : arbitrer sur un inventaire incomplet décalerait les noms, ce qui est pire que de ne
///     pas arbitrer du tout.
public record BrutInventorie(Path source, String nomOriginal, Long nombreTrames) {

    /// Constructeur de **compatibilité** (sans durée) : préserve les appels antérieurs à #1934. Un brut
    /// dont on ignore la durée désactive l'arbitrage pour toute sa nuit.
    public BrutInventorie(Path source, String nomOriginal) {
        this(source, nomOriginal, null);
    }
}

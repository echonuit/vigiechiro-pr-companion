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
public record BrutInventorie(Path source, String nomOriginal) {}

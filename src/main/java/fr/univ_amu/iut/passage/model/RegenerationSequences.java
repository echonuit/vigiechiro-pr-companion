package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.nio.file.Path;
import java.util.List;

/// Port de **régénération des séquences d'écoute à partir d'un brut** (#1406, EPIC #1297).
///
/// La réactivation d'un passage archivé (#1302) rebranche les **séquences** retrouvées. Mais un
/// utilisateur garde plus volontiers ses **bruts** (la copie de sa carte SD) que les transformés, qui
/// sont un produit dérivé. Il fallait donc savoir si l'on pouvait les **régénérer** - et surtout si
/// l'on pouvait ensuite leur **faire confiance**.
///
/// La réponse tient en deux faits :
///
/// 1. la transformation est **déterministe** (R11, `TransformationAudioTest.determinisme_hash_stable`) :
///    mêmes octets en entrée, mêmes octets en sortie ;
/// 2. tout ce qu'il faut pour la rejouer à l'identique est **en base** : le nom R6 de l'original et sa
///    **fréquence d'acquisition** (`original_recording.sampling_rate_hz` = la vraie `Fe`, celle du log,
///    pas celle de l'en-tête), et le nommage des tranches ne dépend que du nom de l'original.
///
/// Il n'y a donc **aucun nouveau mécanisme de confiance à inventer** : on régénère, et les fichiers
/// régénérés passent la **même cascade** que n'importe quel candidat (#1309). Si le code de
/// transformation n'a pas changé, leur empreinte correspond à celle capturée à l'archivage → identité
/// **certaine**. S'il a changé, la cascade descend d'un cran (structure + cris) au lieu d'accorder une
/// confiance aveugle. C'est exactement ce qu'on veut : la reproductibilité est une **preuve**, pas un
/// prérequis.
///
/// Le port vit dans `passage` et son implémentation dans `importation`, qui dépend déjà de `passage`
/// (l'inverse serait un cycle - `ArchitectureTest`). Il est **optionnel** : la feature « Importation »
/// est désactivable, et sans elle la voie « bruts » se refuse en le disant.
@FunctionalInterface
public interface RegenerationSequences {

    /// Régénère, dans `dossierSortie`, les séquences d'un brut, **avec les noms exacts** qu'elles
    /// portaient à l'import (ce sont ces noms que la base connaît, et que Tadarida a analysés).
    ///
    /// @param brut le WAV d'origine retrouvé par l'utilisateur
    /// @param nomOriginal nom R6 de l'original **tel qu'en base** : c'est lui qui nomme les tranches
    /// @param prefixe préfixe de la session (relu du nom de son dossier)
    /// @param frequenceAcquisitionHz la vraie fréquence d'acquisition (`Fe`), telle que persistée à
    ///     l'import : c'est elle qui pilote le découpage à 5 s **réelles**, pas l'en-tête du WAV
    /// @param dossierSortie dossier **temporaire** où écrire les tranches (elles ne rejoindront leur
    ///     place définitive qu'une fois **vérifiées**)
    /// @return les chemins des tranches produites
    List<Path> regenerer(
            Path brut, String nomOriginal, Prefixe prefixe, int frequenceAcquisitionHz, Path dossierSortie);
}

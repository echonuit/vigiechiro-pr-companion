package fr.univ_amu.iut.lot.model;

/// Une archive **planifiée** avant compression (#820) : ce que l'on connaît dès l'établissement du plan
/// (avant toute écriture), pour pré-remplir la table de dépôt d'une ligne « en attente » par ZIP à
/// produire. La taille définitive n'est connue qu'après compression ([ArchiveDepot]) ; on n'expose ici
/// qu'une **estimation** (WAV source × ratio de compression).
///
/// @param numero numéro croissant de l'archive (1, 2, …), tel qu'il nommera `<préfixe>-N.zip`
/// @param nombreFichiers nombre de séquences que l'archive contiendra
/// @param tailleEstimeeOctets taille compressée **estimée** de l'archive, en octets
public record ArchivePlanifiee(int numero, int nombreFichiers, long tailleEstimeeOctets) {}

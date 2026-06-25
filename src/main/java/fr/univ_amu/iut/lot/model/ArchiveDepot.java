package fr.univ_amu.iut.lot.model;

import java.nio.file.Path;

/// Une **archive ZIP de dépôt** produite pour la plateforme Tadarida / Vigie-Chiro (#110). Un lot
/// volumineux est scindé en plusieurs archives nommées `<préfixe>-1.zip`, `<préfixe>-2.zip`, … chacune
/// sous le plafond de taille de la plateforme.
///
/// @param chemin chemin de l'archive `.zip` écrite sur disque
/// @param numero numéro croissant de l'archive dans le lot (1, 2, …)
/// @param tailleOctets taille de l'archive produite, en octets
/// @param nombreFichiers nombre de fichiers (séquences) inclus dans l'archive
public record ArchiveDepot(Path chemin, int numero, long tailleOctets, int nombreFichiers) {}

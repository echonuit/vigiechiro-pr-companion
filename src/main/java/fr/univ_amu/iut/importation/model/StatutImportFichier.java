package fr.univ_amu.iut.importation.model;

/// Disposition d'un fichier dans le **rapport d'import** (#155) : ce qu'il est advenu de chaque fichier
/// de la source lors de l'import résilient.
public enum StatutImportFichier {
    /// Enregistrement original copié, renommé et transformé avec succès.
    IMPORTE,
    /// Fichier non pertinent pour l'import (ni WAV, ni journal, ni relevé) : laissé de côté sans erreur.
    IGNORE,
    /// Fichier qui aurait dû être importé mais a été écarté (illisible, format invalide…) : la raison
    /// est consignée. L'import se poursuit sur les autres fichiers (résilience, #155).
    REJETE
}

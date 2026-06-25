package fr.univ_amu.iut.importation.model;

/// Signale qu'un enregistrement original **source** est illisible ou de format invalide (en-tête WAV
/// corrompu, fréquence non conforme R10…). C'est une erreur **récupérable** (#155) : l'import **rejette**
/// ce fichier (en le consignant au rapport) et poursuit sur les autres.
///
/// À ne pas confondre avec une erreur **d'écriture du workspace** (disque plein, permission refusée),
/// qui reste **fatale** (remontée en [java.io.UncheckedIOException], avec nettoyage de la session) : on
/// ne masque pas un problème opérationnel de sortie en simple « fichier rejeté ».
public final class OriginalIllisibleException extends RuntimeException {

    public OriginalIllisibleException(String message, Throwable cause) {
        super(message, cause);
    }

    public OriginalIllisibleException(String message) {
        super(message);
    }
}

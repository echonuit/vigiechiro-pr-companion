package fr.univ_amu.iut.importation.model;

/// Signale qu'un enregistrement original **source** est **déjà ralenti** (déjà expansé ×10) : sa
/// fréquence d'échantillonnage est trop basse pour un ultrason brut (cf. [DetectionRalenti]). Le
/// transformer le ré-expanserait ×10 (double expansion). Comme [OriginalIllisibleException], c'est une
/// erreur **récupérable** (#155) : l'import **rejette** ce fichier (consigné au rapport) et poursuit sur
/// les autres. À distinguer d'un problème d'écriture du workspace, qui reste fatal.
public final class OriginalDejaRalentiException extends RuntimeException {

    public OriginalDejaRalentiException(String message) {
        super(message);
    }
}

package fr.univ_amu.iut.importation.model;

/// Détecte un enregistrement **déjà ralenti** (déjà expansé ×10), c'est-à-dire un WAV dont la fréquence
/// d'échantillonnage est trop basse pour un ultrason **brut** Vigie-Chiro : ce n'est pas une vraie
/// source. Importer un tel fichier le ferait ré-expanser ×10 par [TransformationAudio], soit une
/// **double expansion** (fréquences 10× trop basses à l'affichage). L'import doit donc le **rejeter**.
///
/// Deux cas :
/// - **avec log** : la fréquence d'acquisition déclarée par l'enregistreur (`Fe…kHz`, cf.
///   [AnalyseurLogPR]) fait foi ; un en-tête WAV **inférieur** signale un fichier déjà ralenti
///   (typiquement `enTête × 10 == Fe`) ;
/// - **sans log** (mode dégradé) : on retombe sur un seuil absolu — un ultrason brut est échantillonné
///   à au moins {@link #FREQUENCE_ACQUISITION_MIN_HZ} ; en dessous, la source a déjà été ralentie.
///
/// Calcul **pur** (sans I/O), testable unitairement.
public final class DetectionRalenti {

    /// Fréquence d'acquisition minimale plausible d'un ultrason brut Vigie-Chiro, en Hz. Les
    /// enregistreurs échantillonnent à au moins 192 kHz (souvent 256/384 kHz) ; on prend une marge
    /// basse à 96 kHz : sous ce seuil, le fichier n'est pas un brut (il a été ralenti / rééchantillonné).
    public static final int FREQUENCE_ACQUISITION_MIN_HZ = 96_000;

    private DetectionRalenti() {}

    /// `true` si un WAV d'en-tête `frequenceEnTeteHz` est **déjà ralenti** au regard de la fréquence
    /// d'acquisition du log `frequenceLogHz` (`null` en mode dégradé → seuil absolu).
    public static boolean estDejaRalenti(int frequenceEnTeteHz, Integer frequenceLogHz) {
        if (frequenceLogHz != null && frequenceLogHz > 0) {
            return frequenceEnTeteHz < frequenceLogHz;
        }
        return frequenceEnTeteHz < FREQUENCE_ACQUISITION_MIN_HZ;
    }
}

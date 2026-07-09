package fr.univ_amu.iut.importation.model;

/// Résout la **vraie fréquence d'acquisition** (Hz) d'un enregistrement, indépendamment de ce que
/// porte son en-tête WAV. C'est cette fréquence — et non l'en-tête — qui pilote toute l'arithmétique
/// de [TransformationAudio] (fréquence de sortie, découpage à 5 s réelles).
///
/// **Pourquoi l'en-tête ne suffit pas.** L'enregistreur Passive Recorder Vigie-Chiro écrit ses bruts
/// en **expansion temporelle ×10 native** : il échantillonne à `Fe` (ex. 384 000 Hz, déclaré au log
/// `Fe…kHz`) mais estampille le WAV à **`Fe/10`** (ex. 38 400 Hz), échantillons conservés. Les vraies
/// fréquences valent alors ×10 l'affichage. Se fier à l'en-tête (38 400) et le ré-expanser ÷10 donnerait
/// une **double expansion** (fréquences 10× trop basses). On s'appuie donc sur le **log** quand il est
/// présent.
///
/// Résolution (calcul **pur**, sans I/O, testable unitairement) :
/// - **log présent** : il fait foi (`Fe_reelle = log`) ;
/// - **sans log, en-tête ≥ [#SEUIL_ACQUISITION_HZ]** : l'en-tête est déjà une fréquence d'acquisition
///   plausible (brut « direct » / plein spectre) → `Fe_reelle = en-tête` ;
/// - **sans log, en-tête plus bas** : l'en-tête est déjà expansé ×10 (repli sur la réalité du PR) →
///   `Fe_reelle = en-tête × [TransformationAudio#FACTEUR_EXPANSION]`.
public final class FrequenceAcquisition {

    /// Fréquence d'acquisition minimale plausible d'un ultrason **brut direct** Vigie-Chiro, en Hz. Les
    /// enregistreurs échantillonnent à au moins 192 kHz (souvent 256/384 kHz) ; marge basse à 96 kHz.
    /// **Sert seulement en l'absence de log** : sous ce seuil, un en-tête est interprété comme déjà
    /// expansé ×10 (Fe/10), au-dessus comme une acquisition directe.
    public static final int SEUIL_ACQUISITION_HZ = 96_000;

    private FrequenceAcquisition() {}

    /// Vraie fréquence d'acquisition (Hz) : `frequenceLogHz` s'il est fourni (`> 0`), sinon déduite de
    /// l'en-tête `frequenceEnTeteHz` selon [#SEUIL_ACQUISITION_HZ].
    public static int reelle(int frequenceEnTeteHz, Integer frequenceLogHz) {
        if (frequenceLogHz != null && frequenceLogHz > 0) {
            return frequenceLogHz;
        }
        if (frequenceEnTeteHz >= SEUIL_ACQUISITION_HZ) {
            return frequenceEnTeteHz;
        }
        return frequenceEnTeteHz * TransformationAudio.FACTEUR_EXPANSION;
    }
}

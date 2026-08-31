package fr.univ_amu.iut.importation.model;

import java.util.Locale;

/// Les motifs de réveil que le firmware du Passive Recorder écrit dans son `LogPR`.
///
/// `TeensyRecorder.ino` en émet **quatre**, et quatre seulement, tous suivis d'un compteur :
///
/// | Ligne | Ce qu'elle dit |
/// |---|---|
/// | `Wakeup by ALARM... Cpt N` | le réveil programmé de la nuit |
/// | `Wakeup by PINPUSH... Cpt N` | l'observateur a appuyé sur une touche, quelle qu'elle soit |
/// | `Wakeup by unknow... Cpt N` | la cause est inconnue |
/// | `Wakeup by unknow ISR... Cpt N` | une interruption inconnue |
///
/// La distinction qui compte n'est pas « programmé ou non », c'est **voulu ou subi**. Le firmware la
/// fait lui-même : sur un réveil programmé ou inconnu il repasse en veille sans attendre, sur une
/// touche il sort de la boucle pour laisser l'observateur agir.
///
/// Le code ne connaissait que `ALARM` et traitait tout le reste en anomalie, si bien qu'un appui sur
/// une touche se signalait comme un défaut et fabriquait une nuit de plus (#4981).
final class MotifDeReveil {

    private MotifDeReveil() {}

    /// `true` si la ligne annonce un réveil, quel qu'en soit le motif.
    static boolean estUnReveil(String message) {
        return message.contains("Wakeup");
    }

    /// `true` si ce réveil a été **voulu** : programmé par l'alarme, ou provoqué par une touche.
    ///
    /// Les deux `unknow` en sont exclus : le firmware les traite comme un appui, mais c'est une
    /// décision de conduite et non de cause. Une origine inconnue reste ce que l'observateur doit
    /// savoir.
    static boolean estVoulu(String message) {
        String bas = message.toLowerCase(Locale.ROOT);
        return bas.contains("alarm") || bas.contains("pinpush");
    }
}

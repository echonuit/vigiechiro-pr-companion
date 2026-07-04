package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Cache paresseux des grandeurs acoustiques (#500) : mémorisation par observation + formatage kHz.
class MetriquesAcoustiquesAudioTest {

    @Test
    @DisplayName("Non calculé : « — » pour FME et fréquence terminale")
    void non_calcule_affiche_tiret() {
        MetriquesAcoustiquesAudio metriques = new MetriquesAcoustiquesAudio();
        assertThat(metriques.fmeColonne(1L)).isEqualTo("—");
        assertThat(metriques.frequenceTerminaleColonne(1L)).isEqualTo("—");
    }

    @Test
    @DisplayName("Mémorisé : FME/terminale affichées en kHz (Hz ÷ 1000, arrondi)")
    void memorise_puis_affiche_en_khz() {
        MetriquesAcoustiquesAudio metriques = new MetriquesAcoustiquesAudio();
        assertThat(metriques.memoriser(7L, 52_000, 45_400)).isTrue();
        assertThat(metriques.fmeColonne(7L)).isEqualTo("52 kHz");
        assertThat(metriques.frequenceTerminaleColonne(7L)).isEqualTo("45 kHz");
    }

    @Test
    @DisplayName("Les deux grandeurs indéterminées (NaN) : rien n'est mémorisé")
    void nan_les_deux_non_memorise() {
        MetriquesAcoustiquesAudio metriques = new MetriquesAcoustiquesAudio();
        assertThat(metriques.memoriser(7L, Double.NaN, Double.NaN)).isFalse();
        assertThat(metriques.fmeColonne(7L)).isEqualTo("—");
    }

    @Test
    @DisplayName("Une seule grandeur déterminée : mémorisée, l'autre reste « — »")
    void une_seule_grandeur_determinee() {
        MetriquesAcoustiquesAudio metriques = new MetriquesAcoustiquesAudio();
        assertThat(metriques.memoriser(7L, 52_000, Double.NaN)).isTrue();
        assertThat(metriques.fmeColonne(7L)).isEqualTo("52 kHz");
        assertThat(metriques.frequenceTerminaleColonne(7L)).isEqualTo("—");
    }

    @Test
    @DisplayName("Fusion : une lecture tardive complète l'autre grandeur sans effacer la première")
    void fusion_ne_perd_pas_une_grandeur_deja_captee() {
        MetriquesAcoustiquesAudio metriques = new MetriquesAcoustiquesAudio();
        // 1re capture : FME seule (fréq. terminale pas encore stabilisée).
        assertThat(metriques.memoriser(7L, 52_000, Double.NaN)).isTrue();
        // 2e capture : fréq. terminale arrive, FME relue NaN transitoirement → on garde la FME captée.
        assertThat(metriques.memoriser(7L, Double.NaN, 45_000)).isTrue();
        assertThat(metriques.fmeColonne(7L)).isEqualTo("52 kHz");
        assertThat(metriques.frequenceTerminaleColonne(7L)).isEqualTo("45 kHz");
        // 3e capture identique : rien de nouveau → pas de rafraîchissement.
        assertThat(metriques.memoriser(7L, 52_000, 45_000)).isFalse();
    }
}

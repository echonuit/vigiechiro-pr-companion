package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.DescripteurReglage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Onglet « Audio » de l'écran Réglages (#1006) : ses descripteurs pointent les clés/défauts partagés
/// avec les options du menu ☰ de la vue audio (`LecteurAudio`, qui référence les mêmes constantes) —
/// impossible de dériver. On vérifie clés, défauts et types.
class OngletReglagesAudioTest {

    @Test
    @DisplayName("l'onglet Audio déclare les 4 préférences (lecture auto/boucle, daltonien, inclure-mode)")
    void declare_les_preferences_audio() {
        OngletReglagesAudio onglet = new OngletReglagesAudio();

        assertThat(onglet.idFeature()).isEqualTo("audio");
        assertThat(onglet.reglages())
                .extracting(DescripteurReglage::cle)
                .containsExactly(
                        OngletReglagesAudio.CLE_LECTURE_AUTO,
                        OngletReglagesAudio.CLE_BOUCLE,
                        OngletReglagesAudio.CLE_DALTONIEN,
                        OngletReglagesAudio.CLE_INCLURE_MODE);

        assertThat(onglet.reglages())
                .allSatisfy(descripteur -> assertThat(descripteur).isInstanceOf(DescripteurReglage.Booleen.class))
                .extracting(descripteur -> ((DescripteurReglage.Booleen) descripteur).defaut())
                .containsExactly(
                        OngletReglagesAudio.DEFAUT_LECTURE_AUTO,
                        OngletReglagesAudio.DEFAUT_BOUCLE,
                        OngletReglagesAudio.DEFAUT_DALTONIEN,
                        OngletReglagesAudio.DEFAUT_INCLURE_MODE);
    }
}

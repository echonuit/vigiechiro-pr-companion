package fr.univ_amu.iut.lot.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le registre qui dit si un téléversement tourne pour un passage (#5599).
///
/// La génération le consulte pour ne pas écrire dans le dossier `depot/` pendant que la source du
/// téléversement y produit ses archives. Il doit se lever **quoi qu'il arrive** au téléversement :
/// un registre resté armé après un plantage bloquerait la génération à tort, le coincement même que
/// #5599 corrige.
class TeleversementsEnCoursTest {

    private final TeleversementsEnCours registre = new TeleversementsEnCours();

    @Test
    @DisplayName("un passage inscrit est en cours, puis ne l'est plus après le retrait")
    void inscrit_pendant_puis_retire() {
        try (TeleversementsEnCours.Inscription _ = registre.inscrire(42L)) {
            assertThat(registre.enCours(42L)).isTrue();
            assertThat(registre.enCours(43L))
                    .as("un autre passage n'est pas concerné")
                    .isFalse();
        }
        assertThat(registre.enCours(42L)).isFalse();
    }

    @Test
    @DisplayName("une exception pendant le téléversement retire quand même l'inscription")
    void retire_apres_une_exception() {
        assertThatThrownBy(() -> {
                    try (TeleversementsEnCours.Inscription _ = registre.inscrire(42L)) {
                        throw new IllegalStateException("réseau coupé");
                    }
                })
                .isInstanceOf(IllegalStateException.class);

        assertThat(registre.enCours(42L)).isFalse();
    }

    @Test
    @DisplayName("deux téléversements du même passage : le passage reste en cours jusqu'au second retrait")
    void deux_inscriptions_du_meme_passage() {
        TeleversementsEnCours.Inscription premiere = registre.inscrire(42L);
        TeleversementsEnCours.Inscription seconde = registre.inscrire(42L);

        premiere.close();
        assertThat(registre.enCours(42L))
                .as("un ensemble simple aurait levé la garde pendant que le second tourne encore")
                .isTrue();

        seconde.close();
        assertThat(registre.enCours(42L)).isFalse();
    }

    @Test
    @DisplayName("fermer deux fois la même inscription ne retire pas celle d'un autre")
    void fermer_deux_fois_est_sans_effet() {
        TeleversementsEnCours.Inscription premiere = registre.inscrire(42L);
        TeleversementsEnCours.Inscription seconde = registre.inscrire(42L);

        premiere.close();
        premiere.close();

        assertThat(registre.enCours(42L)).isTrue();
        seconde.close();
        assertThat(registre.enCours(42L)).isFalse();
    }
}

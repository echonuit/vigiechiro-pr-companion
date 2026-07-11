package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de [DescripteurVueJson] : aller-retour du descripteur **composite** (filtres + colonnes) et
/// **rétro-compatibilité** avec les blobs antérieurs à #994 (filtres seuls).
class DescripteurVueJsonTest {

    private static final DescripteurFiltre FILTRES =
            new DescripteurFiltre("pip", List.of(new DescripteurCritere("statut", List.of("VALIDEE"))));

    @Test
    @DisplayName("Aller-retour : filtres + colonnes sérialisés puis relus sont identiques")
    void aller_retour_filtres_et_colonnes() {
        DescripteurVue vue = new DescripteurVue(
                FILTRES,
                Map.of(
                        "principale",
                        new DescripteurColonnes(List.of(
                                new DescripteurColonnes.EtatColonne("Carré", true),
                                new DescripteurColonnes.EtatColonne("Date", false)))));

        String json = DescripteurVueJson.serialiser(vue);

        assertThat(json).contains("filtres", "colonnes");
        assertThat(DescripteurVueJson.interpreter(json)).isEqualTo(vue);
    }

    @Test
    @DisplayName("Rétro-compat : un blob hérité (filtres seuls) est lu comme une vue sans colonnes")
    void format_herite_sans_colonnes() {
        String blobHerite = "{\"texte\":\"pip\",\"criteres\":[{\"nom\":\"statut\",\"valeurs\":[\"VALIDEE\"]}]}";

        DescripteurVue vue = DescripteurVueJson.interpreter(blobHerite);

        assertThat(vue.filtres()).isEqualTo(FILTRES);
        assertThat(vue.colonnes()).isEmpty();
    }

    @Test
    @DisplayName("Une vue sans colonnes se sérialise avec une section colonnes vide et se relit à l'identique")
    void vue_sans_colonnes_aller_retour() {
        DescripteurVue vue = DescripteurVue.sansColonnes(FILTRES);

        assertThat(DescripteurVueJson.interpreter(DescripteurVueJson.serialiser(vue)))
                .isEqualTo(vue);
    }
}

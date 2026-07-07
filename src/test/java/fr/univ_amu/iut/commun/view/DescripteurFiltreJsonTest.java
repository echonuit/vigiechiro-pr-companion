package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests d'aller-retour de [DescripteurFiltreJson] (Gson) : la (dé)sérialisation d'un descripteur de
/// filtres est fidèle et déterministe, y compris avec plusieurs critères et valeurs multiples.
class DescripteurFiltreJsonTest {

    @Test
    @DisplayName("Aller-retour : un descripteur sérialisé puis relu est identique")
    void aller_retour_fidele() {
        DescripteurFiltre descripteur = new DescripteurFiltre(
                "pipistrelle",
                List.of(
                        new DescripteurCritere("statut", List.of("VALIDEE")),
                        new DescripteurCritere("heure", List.of("21", "6"))));

        String json = DescripteurFiltreJson.serialiser(descripteur);

        assertThat(DescripteurFiltreJson.interpreter(json)).isEqualTo(descripteur);
    }

    @Test
    @DisplayName("Sérialisation déterministe : ordre des critères et des valeurs préservé")
    void serialisation_deterministe() {
        DescripteurFiltre descripteur =
                new DescripteurFiltre("x", List.of(new DescripteurCritere("statut", List.of("VALIDEE"))));

        assertThat(DescripteurFiltreJson.serialiser(descripteur))
                .isEqualTo("{\"texte\":\"x\",\"criteres\":[{\"nom\":\"statut\",\"valeurs\":[\"VALIDEE\"]}]}");
    }

    @Test
    @DisplayName("Descripteur sans critère : aller-retour et texte préservés")
    void descripteur_vide() {
        DescripteurFiltre descripteur = new DescripteurFiltre("", List.of());

        assertThat(DescripteurFiltreJson.interpreter(DescripteurFiltreJson.serialiser(descripteur)))
                .isEqualTo(descripteur);
    }
}

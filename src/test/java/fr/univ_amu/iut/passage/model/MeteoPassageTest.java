package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de l'accès typé à la **donnée météo optionnelle** d'un passage (#106) : lecture de la clé
/// `tempDebut` dans l'objet `weather_data`, écriture préservant les autres clés, rejet des valeurs non
/// finies, lecture de saisie stricte.
class MeteoPassageTest {

    @Test
    @DisplayName("temperatureDebutNuit lit la clé tempDebut d'un objet météo (tolérant)")
    void lecture_cle_tempDebut() {
        assertThat(MeteoPassage.temperatureDebutNuit(null)).isNull();
        assertThat(MeteoPassage.temperatureDebutNuit("")).isNull();
        assertThat(MeteoPassage.temperatureDebutNuit("{}")).isNull();
        assertThat(MeteoPassage.temperatureDebutNuit("{\"autre\":\"x\"}")).isNull();
        assertThat(MeteoPassage.temperatureDebutNuit("{\"tempDebut\":18.5}")).isEqualTo(18.5);
        assertThat(MeteoPassage.temperatureDebutNuit("{\"tempDebut\":-3,\"hygro\":80}"))
                .isEqualTo(-3.0);
        // Valeur non finie stockée en base : ignorée (jamais NaN °C à l'affichage).
        assertThat(MeteoPassage.temperatureDebutNuit("{\"tempDebut\":NaN}")).isNull();
    }

    @Test
    @DisplayName("definir met à jour tempDebut en PRÉSERVANT les autres clés ; null l'efface")
    void ecriture_preserve_les_autres_cles() {
        assertThat(MeteoPassage.definir(null, 8.5)).isEqualTo("{\"tempDebut\":8.5}");

        String avec = MeteoPassage.definir("{\"hygro\":80}", 8.5);
        assertThat(avec).contains("\"hygro\":80").contains("\"tempDebut\":8.5");
        assertThat(MeteoPassage.temperatureDebutNuit(avec)).as("round-trip").isEqualTo(8.5);

        String efface = MeteoPassage.definir("{\"hygro\":80,\"tempDebut\":8.5}", null);
        assertThat(efface).contains("\"hygro\":80").doesNotContain("tempDebut");

        assertThat(MeteoPassage.definir("{\"tempDebut\":8.5}", null))
                .as("objet devenu vide → colonne effacée")
                .isNull();
    }

    @Test
    @DisplayName("definir refuse une température non finie (NaN/Infini)")
    void ecriture_refuse_non_finie() {
        assertThatThrownBy(() -> MeteoPassage.definir(null, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MeteoPassage.definir(null, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("lireSaisie : vide → null ; virgule/point acceptés ; lève si non numérique OU non fini")
    void lecture_saisie_stricte() {
        assertThat(MeteoPassage.lireSaisie("")).isNull();
        assertThat(MeteoPassage.lireSaisie(null)).isNull();
        assertThat(MeteoPassage.lireSaisie("  9,0 ")).isEqualTo(9.0);
        assertThatThrownBy(() -> MeteoPassage.lireSaisie("froid")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> MeteoPassage.lireSaisie("NaN")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> MeteoPassage.lireSaisie("Infinity")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("lire remonte un relevé complet ; les grandeurs absentes valent null")
    void lecture_releve_complet() {
        assertThat(MeteoPassage.lire(null)).isEqualTo(MeteoReleve.VIDE);
        assertThat(MeteoPassage.lire("{}")).isEqualTo(MeteoReleve.VIDE);

        MeteoReleve r = MeteoPassage.lire("{\"tempDebut\":8.5,\"tempFin\":3,\"vent\":12.4,\"couvertureNuageuse\":75}");
        assertThat(r.temperatureDebutNuit()).isEqualTo(8.5);
        assertThat(r.temperatureFinNuit()).isEqualTo(3.0);
        assertThat(r.vent()).isEqualTo(12.4);
        assertThat(r.couvertureNuageuse()).isEqualTo(75.0);

        // Relevé partiel : seules les clés présentes sont remontées, le reste null.
        MeteoReleve partiel = MeteoPassage.lire("{\"tempFin\":4.0}");
        assertThat(partiel.temperatureFinNuit()).isEqualTo(4.0);
        assertThat(partiel.temperatureDebutNuit()).isNull();
        assertThat(partiel.vent()).isNull();
        assertThat(partiel.estVide()).isFalse();
    }

    @Test
    @DisplayName("definir(MeteoReleve) écrit les 4 grandeurs, préserve les clés inconnues, efface les null")
    void ecriture_releve_preserve_et_efface() {
        MeteoReleve complet = new MeteoReleve(8.5, 3.0, 12.0, 60.0);
        String json = MeteoPassage.definirReleve("{\"hygro\":80}", complet);
        assertThat(json)
                .contains("\"hygro\":80")
                .contains("\"tempDebut\":8.5")
                .contains("\"tempFin\":3.0")
                .contains("\"vent\":12.0")
                .contains("\"couvertureNuageuse\":60.0");
        assertThat(MeteoPassage.lire(json)).as("round-trip").isEqualTo(complet);

        // Un champ null efface SA clé sans toucher aux autres.
        String sansVent = MeteoPassage.definirReleve(json, new MeteoReleve(8.5, 3.0, null, 60.0));
        assertThat(sansVent)
                .doesNotContain("\"vent\"")
                .contains("\"tempDebut\":8.5")
                .contains("\"hygro\":80");

        // Relevé vide → efface toutes les clés météo, mais garde la clé inconnue (hygro).
        String toutEfface = MeteoPassage.definirReleve(json, MeteoReleve.VIDE);
        assertThat(toutEfface)
                .contains("\"hygro\":80")
                .doesNotContain("tempDebut")
                .doesNotContain("\"vent\"");
    }

    @Test
    @DisplayName("definir(MeteoReleve) refuse une grandeur non finie (NaN/Infini)")
    void ecriture_releve_refuse_non_finie() {
        assertThatThrownBy(() -> MeteoPassage.definirReleve(null, new MeteoReleve(null, Double.NaN, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        MeteoPassage.definirReleve(null, new MeteoReleve(null, null, Double.POSITIVE_INFINITY, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("MeteoReleve.VIDE est vide ; un relevé avec au moins une grandeur ne l'est pas")
    void releve_vide() {
        assertThat(MeteoReleve.VIDE.estVide()).isTrue();
        assertThat(new MeteoReleve(null, null, null, 0.0).estVide()).isFalse();
    }
}

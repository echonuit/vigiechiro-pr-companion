package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'étiquette de proximité **dit pourquoi** c'est une alerte (#1379).
///
/// Le libellé chiffrait la distance et posait une icône d'avertissement sous le seuil, sans jamais dire
/// ce qui cloche. L'icône signale ; elle n'explique pas - c'est le même défaut que le « ⚠ » écrit dans le
/// texte qu'elle a remplacé, sous une forme plus polie.
class LibelleProximiteTest {

    @Test
    @DisplayName("#1379 : sous le seuil, le libellé donne la règle et non le seul chiffre")
    void alerte_donne_la_regle() {
        String libelle = CartesPointsSite.libelleProximite(120, true);

        assertThat(libelle).as("le chiffre situe, il reste").contains("120 m");
        // La règle est écrite dans le code (« garde-fou de protocole ») et n'apparaissait nulle part à
        // l'écran. C'est elle que l'utilisateur doit lire pour savoir s'il doit agir.
        assertThat(libelle).contains("protocole");
        // Et la double cause, que `tropProche()` documente : deux points réellement trop rapprochés, ou
        // une coordonnée saisie de travers. Sans elle, on cherche l'erreur au mauvais endroit.
        assertThat(libelle).contains("GPS");
    }

    @Test
    @DisplayName("#1379 : au-dessus du seuil, l'information reste nue")
    void cas_neutre_reste_nu() {
        String libelle = CartesPointsSite.libelleProximite(850, false);

        assertThat(libelle).isEqualTo("à 850 m du point le plus proche");
        // Rien à expliquer quand rien ne cloche : accrocher la règle au cas nominal en ferait du bruit
        // permanent, et l'alerte cesserait de se distinguer.
        assertThat(libelle).doesNotContain("protocole");
    }

    @Test
    @DisplayName("Au-delà du kilomètre, la distance se lit en kilomètres")
    void distance_lisible_en_kilometres() {
        assertThat(CartesPointsSite.libelleProximite(2400, false)).contains("2,4 km");
    }
}

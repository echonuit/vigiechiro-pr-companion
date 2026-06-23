package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests du [Prefixe] (R6/R7) : forme du préfixe et détection « déjà préfixé » (source unique de
/// vérité partagée par l'inspection, le renommage et l'aperçu, #111).
class PrefixeTest {

    private final Prefixe prefixe = new Prefixe("640380", 2026, 1, "A1");

    @Test
    @DisplayName("prefixeFichier compose le quadruplet R6 avec des tirets U+002D et un tiret final")
    void prefixe_fichier_compose_le_quadruplet() {
        assertThat(prefixe.prefixeFichier()).isEqualTo("Car640380-2026-Pass1-A1-");
    }

    @Test
    @DisplayName("#111 : estNomPrefixe distingue un nom déjà préfixé (Car…) d'un nom brut (PaRecPR…)")
    void est_nom_prefixe_distingue_prefixe_et_brut() {
        assertThat(Prefixe.estNomPrefixe("Car640380-2026-Pass1-A1-PaRecPR1925492_20260422_203922.wav"))
                .as("nom déjà préfixé R6")
                .isTrue();
        assertThat(Prefixe.estNomPrefixe("Car111111-2025-Pass3-B2-PaRecPR1925492_20260422_203922.wav"))
                .as("préfixé même si discordant du rattachement courant")
                .isTrue();
        assertThat(Prefixe.estNomPrefixe("PaRecPR1925492_20260422_203922.wav"))
                .as("nom brut de l'enregistreur")
                .isFalse();
    }

    @Test
    @DisplayName("#111 : un simple « Car… » sans grammaire R6 valide n'est PAS considéré comme préfixé")
    void est_nom_prefixe_exige_la_grammaire_R6_complete() {
        // Commencer par « Car » ne suffit pas : il faut Car<carré>-<année>-Pass<n>-<point>-<suffixe>.
        assertThat(Prefixe.estNomPrefixe("Carto_20260422.wav")).isFalse();
        assertThat(Prefixe.estNomPrefixe("Car_old.wav")).isFalse();
        assertThat(Prefixe.estNomPrefixe("Car640380-2026-A1.wav")).isFalse(); // pas de segment Pass<n>
    }
}

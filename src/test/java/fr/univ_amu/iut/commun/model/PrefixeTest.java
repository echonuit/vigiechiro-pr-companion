package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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

    @Test
    @DisplayName("#530 : horodatageDe extrait l'heure de capture du nom de séquence (dernier _AAAAMMJJ_HHMMSS)")
    void horodatage_de_extrait_l_heure_de_capture() {
        // Nom de tranche horodaté : l'heure réelle de début de la séquence.
        assertThat(Prefixe.horodatageDe("PaRecPR1925492_20260422_225859_000.wav"))
                .contains(LocalDateTime.of(2026, 4, 22, 22, 58, 59));
        // Nom entièrement préfixé R6 : on prend le DERNIER horodatage, sans se laisser piéger par les
        // chiffres du préfixe (640380 / 2026 / 1925492).
        assertThat(Prefixe.horodatageDe("Car640380-2026-Pass1-A1-PaRecPR1925492_20260422_203922_000.wav"))
                .contains(LocalDateTime.of(2026, 4, 22, 20, 39, 22));
    }

    @Test
    @DisplayName("#530 : horodatageDe est vide pour un nom nul ou non horodaté")
    void horodatage_de_vide_si_absent() {
        assertThat(Prefixe.horodatageDe(null)).isEmpty();
        assertThat(Prefixe.horodatageDe("seqA_000.wav")).isEmpty();
        assertThat(Prefixe.horodatageDe("sans_horodatage.wav")).isEmpty();
    }

    @Test
    @DisplayName("#1406 : depuisNomDossier relit le préfixe du nom de son dossier (opération inverse)")
    void depuis_nom_dossier_est_l_inverse() {
        Prefixe prefixe = new Prefixe("040962", 2026, 3, "A1");

        assertThat(Prefixe.depuisNomDossier(prefixe.nomDossierSession())).contains(prefixe);
    }

    @Test
    @DisplayName("#1406 : depuisNomDossier est vide pour un dossier qui ne suit pas la grammaire R22")
    void depuis_nom_dossier_vide_si_hors_grammaire() {
        assertThat(Prefixe.depuisNomDossier(null)).isEmpty();
        assertThat(Prefixe.depuisNomDossier("mes-donnees")).isEmpty();
        assertThat(Prefixe.depuisNomDossier("Car040962-2026-A1"))
                .as("sans le numéro de passage, ce n'est pas un dossier de session")
                .isEmpty();
    }
}

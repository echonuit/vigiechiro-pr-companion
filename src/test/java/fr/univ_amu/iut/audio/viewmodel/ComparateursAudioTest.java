package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Comparateurs de tri des colonnes audio (affichage = chaîne formatée).
class ComparateursAudioTest {

    @Test
    @DisplayName("Numérique : ordre par valeur (« 9 % » < « 83 % » < « 100 % » ; « N°2 » < « N°10 »), « — » en tête")
    void numerique_ordonne_par_valeur() {
        var proba = new ArrayList<>(List.of("83 %", "100 %", "9 %", "—"));
        proba.sort(ComparateursAudio.comparateurNumerique());
        assertThat(proba).containsExactly("—", "9 %", "83 %", "100 %");

        var passage = new ArrayList<>(List.of("N°10", "N°2", "N°1"));
        passage.sort(ComparateursAudio.comparateurNumerique());
        assertThat(passage).containsExactly("N°1", "N°2", "N°10");

        var frequence = new ArrayList<>(List.of("45 kHz", "9 kHz", "—"));
        frequence.sort(ComparateursAudio.comparateurNumerique());
        assertThat(frequence).containsExactly("—", "9 kHz", "45 kHz");

        var position = new ArrayList<>(List.of("3,20 s", "0,50 s", "12,00 s", "—"));
        position.sort(ComparateursAudio.comparateurNumerique());
        assertThat(position).containsExactly("—", "0,50 s", "3,20 s", "12,00 s");
    }

    @Test
    @DisplayName("Durée : ordre par durée réelle malgré les unités mêlées (« 120 ms » < « 500 ms » < « 2,1 s »)")
    void duree_ordonne_par_valeur() {
        var tri = new ArrayList<>(List.of("2,1 s", "120 ms", "500 ms", "—"));
        tri.sort(ComparateursAudio.comparateurDuree());
        assertThat(tri).containsExactly("—", "120 ms", "500 ms", "2,1 s");
    }

    @Test
    @DisplayName("Statut : ordre de revue (À revoir → Validée → Corrigée), pas alphabétique")
    void statut_ordonne_par_revue() {
        var tri = new ArrayList<>(List.of("Corrigée", "À revoir", "Validée"));
        tri.sort(ComparateursAudio.comparateurStatut());
        assertThat(tri).containsExactly("À revoir", "Validée", "Corrigée");
    }
}

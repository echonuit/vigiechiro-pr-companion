package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le découpage du journal `LogPR` en nuits, sur les motifs de réveil que le firmware émet
/// réellement (`TeensyRecorder.ino`) : `ALARM`, `PINPUSH`, `unknow` et `unknow ISR`.
///
/// `CyclesJournal` ouvrait un cycle sur tout `Wakeup` portant un `Cpt`, sans regarder le motif. Un
/// appui sur une touche fabriquait donc une nuit de plus et refermait la précédente en « tronquée »
/// (#4981).
class CyclesJournalTest {

    private static final String SERIE = "PR1925492";

    private static String ligne(String horodatage, String message) {
        return horodatage + " " + SERIE + " " + message;
    }

    @Test
    @DisplayName("Une nuit programmée puis refermée rend un cycle complet")
    void une_nuit_programmee_rend_un_cycle_complet() {
        List<CycleAcquisition> cycles = CyclesJournal.depuis(List.of(
                ligne("22/04/26 - 20:30:00", "Wakeup by ALARM... Cpt 1"),
                ligne("23/04/26 - 06:30:00", "### Passage en mode Veille")));

        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0).complet()).isTrue();
    }

    @Test
    @DisplayName("#4981 : un réveil par touche au milieu de la nuit n'ouvre pas une nuit de plus")
    void un_reveil_par_touche_n_ouvre_pas_une_nuit() {
        // Le geste de l'observateur qui appuie sur une touche pour regarder l'écran. Le firmware sort
        // alors de la veille pour le laisser agir : c'est voulu, ce n'est pas une nuit neuve.
        List<CycleAcquisition> cycles = CyclesJournal.depuis(List.of(
                ligne("22/04/26 - 20:30:00", "Wakeup by ALARM... Cpt 1"),
                ligne("23/04/26 - 01:15:00", "Wakeup by PINPUSH... Cpt 2"),
                ligne("23/04/26 - 06:30:00", "### Passage en mode Veille")));

        assertThat(cycles).as("une seule nuit, et non deux").hasSize(1);
        assertThat(cycles.get(0).complet())
                .as("la nuit s'est refermée normalement, elle n'est pas tronquée")
                .isTrue();
    }

    @Test
    @DisplayName("Un réveil de cause inconnue ouvre bien un cycle : le journal ne sait pas ce qui s'est passé")
    void un_reveil_de_cause_inconnue_ouvre_un_cycle() {
        // Contrôle négatif du cas précédent : sans lui, un remède qui cesserait d'ouvrir un cycle sur
        // TOUT réveil non programmé passerait pour bon.
        List<CycleAcquisition> cycles = CyclesJournal.depuis(List.of(
                ligne("22/04/26 - 20:30:00", "Wakeup by ALARM... Cpt 1"),
                ligne("23/04/26 - 01:15:00", "Wakeup by unknow... Cpt 2"),
                ligne("23/04/26 - 06:30:00", "### Passage en mode Veille")));

        assertThat(cycles)
                .as("le réveil inconnu referme la première nuit et en ouvre une")
                .hasSize(2);
        assertThat(cycles.get(0).complet()).isFalse();
    }
}

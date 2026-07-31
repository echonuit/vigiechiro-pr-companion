package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Garde du filtre `--proba-min` (#2971), pendant CLI de la puce « Proba ».
///
/// Le cas qui compte est celui qui surprend : une détection **sans probabilité** est conservée. C'est
/// la règle de l'écran, et l'écarter reviendrait à décider qu'elle est mauvaise alors qu'on n'en sait
/// rien, en perdant précisément une ligne à revoir.
class FiltreProbabiliteTest {

    private static LigneObservationAudio ligne(long id, Double proba) {
        return new LigneObservationAudio(
                id,
                id,
                1L,
                2,
                "2026-06-22",
                "640380",
                "A1",
                "Étang de la Tuilière",
                "Rhifer",
                proba,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                null,
                "Grand rhinolophe",
                null,
                null,
                "Chiroptères",
                "seq" + id + ".wav",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                0,
                "Ahetze");
    }

    private static final LigneObservationAudio SURE = ligne(1, 0.93);
    private static final LigneObservationAudio INCERTAINE = ligne(2, 0.42);
    private static final LigneObservationAudio SANS_PROBA = ligne(3, null);

    @Test
    @DisplayName("#2971 : aucun seuil n'écarte rien")
    void sans_seuil_rien_n_est_ecarte() {
        List<LigneObservationAudio> toutes = List.of(SURE, INCERTAINE, SANS_PROBA);
        assertThat(FiltreProbabilite.appliquer(toutes, null)).isEqualTo(toutes);
    }

    @Test
    @DisplayName("#2971 : le seuil garde les détections ≥, et TOUJOURS celles sans probabilité")
    void le_seuil_garde_les_superieures_et_les_sans_probabilite() {
        assertThat(FiltreProbabilite.appliquer(List.of(SURE, INCERTAINE, SANS_PROBA), 0.9))
                .as("0,42 tombe ; 0,93 passe ; l'absence de probabilité n'est pas une mauvaise probabilité")
                .containsExactly(SURE, SANS_PROBA);
    }

    @Test
    @DisplayName("#2971 : le seuil est inclusif, comme le curseur de l'écran")
    void le_seuil_est_inclusif() {
        assertThat(FiltreProbabilite.appliquer(List.of(SURE), 0.93)).containsExactly(SURE);
    }

    @Test
    @DisplayName("#2971 : un résultat vide n'est PAS un refus, contrairement à --lieu")
    void un_resultat_vide_est_une_reponse() {
        // Un seuil est un nombre : il ne peut pas désigner quelque chose qui n'existe pas. « Aucune
        // détection au-dessus de 0,99 » est une réponse. Un nom de lieu, lui, se tape de travers, et
        // c'est ce qui justifiait le refus dans FiltreLieu.
        assertThat(FiltreProbabilite.appliquer(List.of(INCERTAINE), 0.99)).isEmpty();
    }

    @Test
    @DisplayName("#2971 : un seuil qui a tout écarté nomme la meilleure probabilité du lot")
    void un_seuil_trop_haut_dit_de_combien() {
        // Le seul filtre qui peut légitimement tout écarter est aussi le seul dont le résultat vide ne
        // dit rien. « 0,93 » apprend à la fois que le lot n'était pas vide et de combien descendre.
        assertThat(FiltreProbabilite.avertissementSeuilTropHaut(List.of(SURE, INCERTAINE), 0.99))
                .hasValueSatisfying(
                        message -> assertThat(message).contains("0,93").contains("0,99"));
    }

    @Test
    @DisplayName("#2971 : l'avertissement se tait quand il n'aurait rien à apprendre")
    void l_avertissement_se_tait_quand_il_faut() {
        // Sans seuil, le vide ne vient pas de lui.
        assertThat(FiltreProbabilite.avertissementSeuilTropHaut(List.of(SURE), null))
                .isEmpty();
        // Le seuil n'a rien écarté : il n'y a rien à regretter.
        assertThat(FiltreProbabilite.avertissementSeuilTropHaut(List.of(SURE), 0.5))
                .isEmpty();
        // Le lot était DÉJÀ vide (espèce absente, lieu sans ligne) : le seuil n'y est pour rien, et
        // annoncer « la plus sûre du lot » d'un lot inexistant serait une sottise.
        assertThat(FiltreProbabilite.avertissementSeuilTropHaut(List.of(), 0.9)).isEmpty();
        // Une ligne sans probabilité est toujours conservée : le résultat n'est donc pas vide.
        assertThat(FiltreProbabilite.avertissementSeuilTropHaut(List.of(SANS_PROBA), 0.99))
                .isEmpty();
    }

    @Test
    @DisplayName("#2971 : un seuil hors bornes est un refus qui nomme la plage ET l'unité")
    void hors_bornes_le_filtre_refuse() {
        // « 90 » est le réflexe du pourcentage : le message doit lever la confusion d'unité, pas
        // seulement rappeler des bornes. Borner en silence rendrait zéro ligne sans rien expliquer.
        assertThatThrownBy(() -> FiltreProbabilite.appliquer(List.of(SURE), 90.0))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("0.9");
        assertThatThrownBy(() -> FiltreProbabilite.appliquer(List.of(SURE), -0.2))
                .isInstanceOf(RegleMetierException.class);
    }
}

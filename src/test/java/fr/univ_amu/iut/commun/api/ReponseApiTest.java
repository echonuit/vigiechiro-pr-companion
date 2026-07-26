package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le vocabulaire des issues d'appel (#1284) : quatre variantes scellées, un adaptateur vers le
/// comportement historique (`enOptionnel`), un transport d'issue à travers les transformations.
class ReponseApiTest {

    @Test
    @DisplayName("enOptionnel : seule Succes porte une valeur ; les trois échecs rendent vide")
    void en_optionnel() {
        assertThat(ReponseApi.succes("corps").enOptionnel()).contains("corps");
        assertThat(ReponseApi.nonConnecte().enOptionnel()).isEmpty();
        assertThat(ReponseApi.injoignable("délai d'attente dépassé").enOptionnel())
                .isEmpty();
        assertThat(ReponseApi.refuse(422, "{}").enOptionnel()).isEmpty();
    }

    @Test
    @DisplayName("transformer : un succès est transformé, un échec traverse inchangé avec sa cause")
    void transformer() {
        assertThat(ReponseApi.succes("7").transformer(Integer::parseInt)).isEqualTo(ReponseApi.succes(7));
        assertThat(ReponseApi.<String>nonConnecte().transformer(Integer::parseInt))
                .isEqualTo(ReponseApi.nonConnecte());
        assertThat(ReponseApi.<String>injoignable("cause").transformer(Integer::parseInt))
                .isEqualTo(ReponseApi.injoignable("cause"));
        assertThat(ReponseApi.<String>refuse(500, "boom").transformer(Integer::parseInt))
                .isEqualTo(ReponseApi.refuse(500, "boom"));
    }

    @Test
    @DisplayName("un switch sur ReponseApi est exhaustif : la garantie qui manquait à la famille #1277")
    void switch_exhaustif() {
        // Ce test documente l'usage attendu : pas de branche par défaut, le compilateur exige les
        // quatre issues. En oublier une (le « cas auquel personne n'a pensé ») ne compile pas.
        ReponseApi<String> reponse = ReponseApi.refuse(422, "max_results");
        String message =
                switch (reponse) {
                    case ReponseApi.Succes<String>(String valeur) -> "succès : " + valeur;
                    case ReponseApi.NonConnecte<String> nonConnecte -> "non connecté";
                    case ReponseApi.Injoignable<String>(String cause) -> "injoignable : " + cause;
                    case ReponseApi.Refuse<String>(int statut, String corps) -> "refusé HTTP " + statut + " : " + corps;
                };
        assertThat(message).isEqualTo("refusé HTTP 422 : max_results");
    }

    @Test
    @DisplayName("estReessayable : réseau et 429/5xx oui ; 4xx définitif, non connecté et succès non")
    void est_reessayable() {
        // Rejouable : un incident réseau, et un serveur temporairement indisponible.
        assertThat(ReponseApi.injoignable("délai").estReessayable()).isTrue();
        assertThat(ReponseApi.refuse(429, "slow down").estReessayable()).isTrue();
        assertThat(ReponseApi.refuse(500, "boom").estReessayable()).isTrue();
        assertThat(ReponseApi.refuse(503, "maintenance").estReessayable()).isTrue();
        assertThat(ReponseApi.refuse(599, "réseau amont").estReessayable()).isTrue();

        // Jamais rejoué : un refus définitif (4xx), un succès, une absence de jeton.
        assertThat(ReponseApi.refuse(400, "corps refusé").estReessayable()).isFalse();
        assertThat(ReponseApi.refuse(403, "URL signée expirée").estReessayable())
                .isFalse();
        assertThat(ReponseApi.refuse(404, "absent").estReessayable()).isFalse();
        assertThat(ReponseApi.refuse(422, "validation").estReessayable()).isFalse();
        assertThat(ReponseApi.succes("ok").estReessayable()).isFalse();
        assertThat(ReponseApi.nonConnecte().estReessayable()).isFalse();

        // Bornes : 499 reste un 4xx, 500 et 599 sont des 5xx, 600 n'est plus un 5xx.
        assertThat(ReponseApi.refuse(499, "").estReessayable()).isFalse();
        assertThat(ReponseApi.refuse(600, "").estReessayable()).isFalse();
    }
}

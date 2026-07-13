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
}

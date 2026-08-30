package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("#4524 : deux 422 de sens oppose, et le refus dit lequel")
    class MotifDUnRefus {

        // Les trois formes sont MESUREES contre le back, avec leur temoin : un champ hors schema, un
        // champ connu mais ferme, et un champ inscriptible qui passe. Elles sont recopiees telles
        // quelles, corps compris, parce que c est la FORME du corps qui porte le sens - le statut est
        // le meme dans les deux refus.

        @Test
        @DisplayName("un champ que le schema ne connait pas : notre correspondance est fautive")
        void champ_inconnu() {
            ReponseApi.Refuse<String> refus = new ReponseApi.Refuse<>(
                    422, "{\"_errors\": {\"numero\": \"invalid field\"}, \"_status\": \"422 Unprocessable Entity\"}");

            assertThat(refus.motif())
                    .as("un champ hors schema : notre correspondance est fautive, elle se corrige ici")
                    .isEqualTo(MotifDeRefus.CHAMP_INCONNU);
        }

        @Test
        @DisplayName("un champ connu mais ferme : rien ne se corrige chez nous")
        void champ_ferme() {
            ReponseApi.Refuse<String> refus = new ReponseApi.Refuse<>(
                    422,
                    "{\"_errors\": {\"donnees_publiques\": \"field is read-only\"},"
                            + " \"_status\": \"422 Unprocessable Entity\"}");

            assertThat(refus.motif())
                    .as("un champ connu mais ferme : notre correspondance est juste, rien a corriger ici")
                    .isEqualTo(MotifDeRefus.CHAMP_FERME);
        }

        @Test
        @DisplayName("le controle qui compte : un refus qui n est ni l un ni l autre ne se force pas")
        void ni_l_un_ni_l_autre() {
            // Sans ce cas, un classement binaire mentirait des le premier refus inconnu, et il
            // mentirait dans le sens rassurant : il rangerait un 403 de droits ou un 422 de forme
            // inedite dans une case qui appelle un geste precis.
            assertThat(new ReponseApi.Refuse<String>(403, "{}").motif())
                    .as("un 403 n est pas un refus de champ")
                    .isEqualTo(MotifDeRefus.AUTRE);
            assertThat(new ReponseApi.Refuse<String>(
                                    422, "{\"_errors\": {\"date_debut\": \"must be of datetime type\"}}")
                            .motif())
                    .as("un 422 de FORME n est ni l un ni l autre : le ranger designerait un geste faux")
                    .isEqualTo(MotifDeRefus.AUTRE);
            assertThat(new ReponseApi.Refuse<String>(422, "").motif())
                    .as("un corps vide ne se range pas non plus")
                    .isEqualTo(MotifDeRefus.AUTRE);
        }

        @Test
        @DisplayName("le motif s ajoute : statut, corps et message ne bougent pas")
        void le_motif_n_efface_rien() {
            ReponseApi.Refuse<String> refus =
                    new ReponseApi.Refuse<>(422, "{\"_errors\": {\"numero\": \"invalid field\"}}");

            assertThat(refus.statut()).as("le statut ne bouge pas").isEqualTo(422);
            assertTrue(refus.echec().orElseThrow().startsWith("HTTP 422 : "));
        }
    }

    @Nested
    @DisplayName("#4631 : pourquoiVide ne perd jamais la cause")
    class PourquoiVide {

        private static final String ABSENTE = "participation introuvable";

        @Test
        @DisplayName("une coupure dit la coupure, pas l'absence")
        void injoignable_dit_la_coupure() {
            assertThat(ReponseApi.<String>injoignable("connexion réinitialisée").pourquoiVide(ABSENTE))
                    .contains("injoignable")
                    .contains("connexion réinitialisée")
                    .doesNotContain(ABSENTE);
        }

        @Test
        @DisplayName("l'absence de jeton dit l'absence de jeton")
        void non_connecte_dit_le_jeton() {
            assertThat(ReponseApi.<String>nonConnecte().pourquoiVide(ABSENTE))
                    .contains("jeton")
                    .doesNotContain(ABSENTE);
        }

        @Test
        @DisplayName("un 404 EST une absence : c'est le seul refus qui le soit")
        void refus_404_est_une_absence() {
            assertThat(ReponseApi.<String>refuse(404, "not found").pourquoiVide(ABSENTE))
                    .isEqualTo(ABSENTE);
        }

        @Test
        @DisplayName("un autre refus dit son statut, il n'est pas une absence")
        void autre_refus_dit_son_statut() {
            assertThat(ReponseApi.<String>refuse(503, "maintenance").pourquoiVide(ABSENTE))
                    .contains("503")
                    .doesNotContain(ABSENTE);
        }

        @Test
        @DisplayName("sur un succès la question ne se pose pas, et rien n'échoue")
        void succes_ne_pose_pas_la_question() {
            assertThat(ReponseApi.succes("valeur").pourquoiVide(ABSENTE)).isEqualTo(ABSENTE);
        }
    }
}

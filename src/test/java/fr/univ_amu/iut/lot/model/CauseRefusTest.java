package fr.univ_amu.iut.lot.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.ReponseApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Ce que `CauseRefus.de` déduit du statut, et **rien que du statut** (#3689).
///
/// ## Pourquoi ce fichier existe
///
/// Un PIT ciblé passé en clôture du lot #3900 a rendu **cinq survivants** sur cette seule méthode,
/// dont « replaced return value with null » : ne dériver **aucune** cause, jamais, ne cassait aucun
/// test. C'est pourtant la décision centrale de #3689 - c'est elle qui décide quelles unités une
/// reconnexion réarmera.
///
/// Le mécanisme d'aval était gardé (`DepotUniteDaoTest` : `rearmer` ne prend que la cause
/// AUTHENTIFICATION) ; la **dérivation** ne l'était pas.
class CauseRefusTest {

    @Nested
    @DisplayName("Ce qu'une reconnexion peut lever")
    class Authentification {

        @Test
        @DisplayName("401 : le jeton est mort, une reconnexion le renouvelle")
        void jeton_mort() {
            assertThat(CauseRefus.de(ReponseApi.refuse(401, "token expired"))).isEqualTo(CauseRefus.AUTHENTIFICATION);
        }

        @Test
        @DisplayName("403 : les droits manquent, une reconnexion peut les rendre")
        void droits_manquants() {
            assertThat(CauseRefus.de(ReponseApi.refuse(403, "forbidden"))).isEqualTo(CauseRefus.AUTHENTIFICATION);
        }
    }

    @Nested
    @DisplayName("Ce que rien d'extérieur ne répare")
    class Contenu {

        @Test
        @DisplayName("400 : la requête elle-même est refusée")
        void requete_refusee() {
            assertThat(CauseRefus.de(ReponseApi.refuse(400, "bad request"))).isEqualTo(CauseRefus.CONTENU);
        }

        @Test
        @DisplayName("422 : le contenu est refusé, se reconnecter n'y changerait rien")
        void contenu_refuse() {
            assertThat(CauseRefus.de(ReponseApi.refuse(422, "unprocessable"))).isEqualTo(CauseRefus.CONTENU);
        }

        @Test
        @DisplayName("404 : les autres 4xx sont du contenu, faute de raison de croire l'inverse")
        void autre_4xx() {
            assertThat(CauseRefus.de(ReponseApi.refuse(404, "not found"))).isEqualTo(CauseRefus.CONTENU);
        }
    }

    @Nested
    @DisplayName("Ce qui n'est pas un refus définitif n'a pas de cause à porter")
    class SansCause {

        @Test
        @DisplayName("429 : trop de requêtes, donc rejouable")
        void trop_de_requetes() {
            assertThat(CauseRefus.de(ReponseApi.refuse(429, "slow down"))).isNull();
        }

        @Test
        @DisplayName("503 : panne serveur, donc rejouable")
        void panne_serveur() {
            assertThat(CauseRefus.de(ReponseApi.refuse(503, "unavailable"))).isNull();
        }

        @Test
        @DisplayName("un succès, un injoignable et un non-connecté ne sont pas des refus")
        void pas_un_refus() {
            assertThat(CauseRefus.de(ReponseApi.succes("ok"))).isNull();
            assertThat(CauseRefus.de(ReponseApi.injoignable("réseau coupé"))).isNull();
            assertThat(CauseRefus.de(ReponseApi.nonConnecte())).isNull();
        }
    }
}

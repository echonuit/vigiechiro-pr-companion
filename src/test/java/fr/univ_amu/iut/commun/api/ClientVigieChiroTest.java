package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Transport HTTP** du client VigieChiro (#725/#728) : construction de l'en-tête d'authentification
/// Basic-token et **dégradation propre** (pas de token / réseau indisponible → vide). La lecture des
/// réponses JSON est testée à part dans `ReponsesVigieChiroTest`. On ne fait aucun appel réseau réel :
/// une URL injoignable suffit à exercer la dégradation.
class ClientVigieChiroTest {

    private static final FournisseurToken SANS_TOKEN = Optional::empty;
    private static final FournisseurToken TOKEN_ABC = () -> Optional.of("abc");

    @Test
    @DisplayName("enteteAuthorization : Basic base64(token:) ; token en username, mot de passe vide")
    void entete_authorization() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1", TOKEN_ABC);

        // base64("abc:") = "YWJjOg=="
        assertThat(client.enteteAuthorization()).contains("Basic YWJjOg==");
    }

    @Test
    @DisplayName("enteteAuthorization : sans token → vide (non connecté)")
    void entete_sans_token() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1", SANS_TOKEN);

        assertThat(client.enteteAuthorization()).isEmpty();
    }

    @Test
    @DisplayName("get / moi sans token → vide, sans même toucher le réseau")
    void moi_sans_token_est_vide() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1", SANS_TOKEN);

        assertThat(client.get("/moi")).isEmpty();
        assertThat(client.moi()).isEmpty();
    }

    @Test
    @DisplayName("get / moi hors-ligne (URL injoignable) → vide, sans lever")
    void moi_hors_ligne_est_vide() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1/api/v1", TOKEN_ABC);

        assertThat(client.get("/moi")).isEmpty();
        assertThat(client.moi()).isEmpty();
    }

    @Test
    @DisplayName("taxons / mesSites / donnees sans token → listes vides, sans toucher le réseau")
    void listes_sans_token_sont_vides() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1/api/v1", SANS_TOKEN);

        assertThat(client.taxons()).isEmpty();
        assertThat(client.mesSites()).isEmpty();
        assertThat(client.mesParticipations()).isEmpty();
        assertThat(client.participation("6a49")).isEmpty();
        assertThat(client.donnees("6a49")).isEmpty();
    }

    @Test
    @DisplayName("écritures sans token → vide / false, sans toucher le réseau (#142)")
    void ecritures_sans_token() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1/api/v1", SANS_TOKEN);

        assertThat(client.post("/fichiers", "{}")).isEmpty();
        assertThat(client.creerParticipation("site1", participationMinimale()).id())
                .isEmpty();
        assertThat(client.creerFichier("Car130711-2026-Pass1-Z41_000.wav")).isEmpty();
        assertThat(client.finaliserFichier("f1")).isFalse();
    }

    @Test
    @DisplayName("écritures hors-ligne (URL injoignable) → vide / false, sans lever (#142)")
    void ecritures_hors_ligne() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1/api/v1", TOKEN_ABC);

        assertThat(client.creerParticipation("site1", participationMinimale()).id())
                .isEmpty();
        assertThat(client.creerFichier("Car130711-2026-Pass1-Z41_000.wav")).isEmpty();
        assertThat(client.finaliserFichier("f1")).isFalse();
    }

    @Test
    @DisplayName("televerserVersS3 hors-ligne → false, sans lever (URL S3 déjà signée, sans auth)")
    void upload_s3_hors_ligne_est_false() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1/api/v1", TOKEN_ABC);

        assertThat(client.televerserVersS3("http://localhost:1/s3/signe", new byte[] {1, 2, 3}, "audio/x-wav"))
                .isFalse();
    }

    private static ParticipationADeposer participationMinimale() {
        return new ParticipationADeposer("Z41", "2026-07-03T19:00:00Z", "2026-07-04T04:00:00Z", null, null, null);
    }
}

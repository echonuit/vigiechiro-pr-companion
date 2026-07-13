package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Façade des points d'accès VigieChiro (#725/#728) : les endpoints encore « dégradation propre »
/// rendent vide/false sur toute issue non-succès (la mécanique HTTP et son tri sont testés dans
/// `TransportVigieChiroTest`, la lecture JSON dans `ReponsesVigieChiroTest`). Aucun appel réseau réel :
/// une URL injoignable suffit.
class ClientVigieChiroTest {

    private static final FournisseurToken SANS_TOKEN = Optional::empty;
    private static final FournisseurToken TOKEN_ABC = () -> Optional.of("abc");

    @Test
    @DisplayName("get / moi sans token → NonConnecte, sans même toucher le réseau")
    void moi_sans_token_est_vide() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1", SANS_TOKEN);

        assertThat(client.get("/moi")).isEmpty();
        assertThat(client.moi()).isInstanceOf(ReponseApi.NonConnecte.class);
    }

    @Test
    @DisplayName("get / moi hors-ligne (URL injoignable) → Injoignable, sans lever")
    void moi_hors_ligne_est_vide() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1/api/v1", TOKEN_ABC);

        assertThat(client.get("/moi")).isEmpty();
        assertThat(client.moi()).isInstanceOf(ReponseApi.Injoignable.class);
    }

    @Test
    @DisplayName("taxons / mesSites sans token → listes vides ; participation / donnees → NonConnecte (#1284)")
    void listes_sans_token_sont_vides() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1/api/v1", SANS_TOKEN);

        assertThat(client.taxons()).isEmpty();
        assertThat(client.mesSites()).isEmpty();
        assertThat(client.mesParticipations()).isEmpty();
        assertThat(client.participation("6a49")).isInstanceOf(ReponseApi.NonConnecte.class);
        assertThat(client.donnees("6a49")).isInstanceOf(ReponseApi.NonConnecte.class);
    }

    @Test
    @DisplayName("participation / donnees hors-ligne → Injoignable, plus jamais un faux « vide » (#1284)")
    void lectures_triees_hors_ligne() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1/api/v1", TOKEN_ABC);

        assertThat(client.participation("6a49")).isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(client.donnees("6a49")).isInstanceOf(ReponseApi.Injoignable.class);
    }

    @Test
    @DisplayName("écritures sans token → échec « non connecté » explicite, sans toucher le réseau (#1284)")
    void ecritures_sans_token() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1/api/v1", SANS_TOKEN);

        assertThat(client.post("/fichiers", "{}")).isEmpty();
        assertThat(client.creerParticipation("site1", participationMinimale()).id())
                .isEmpty();
        assertThat(client.creerParticipation("site1", participationMinimale()).echec())
                .contains("jeton");
        assertThat(client.modifierParticipation("p1", "etag1", participationMinimale())
                        .id())
                .isEmpty();
        assertThat(client.creerFichier("Car130711-2026-Pass1-Z41_000.wav", "p1"))
                .isEmpty();
        assertThat(client.finaliserFichier("f1")).isFalse();
    }

    @Test
    @DisplayName("écritures hors-ligne (URL injoignable) → échec « injoignable » explicite, sans lever")
    void ecritures_hors_ligne() {
        ClientVigieChiro client = new ClientVigieChiro("http://localhost:1/api/v1", TOKEN_ABC);

        assertThat(client.creerParticipation("site1", participationMinimale()).id())
                .isEmpty();
        assertThat(client.creerParticipation("site1", participationMinimale()).echec())
                .contains("injoignable");
        assertThat(client.modifierParticipation("p1", "etag1", participationMinimale())
                        .id())
                .isEmpty();
        assertThat(client.creerFichier("Car130711-2026-Pass1-Z41_000.wav", "p1"))
                .isEmpty();
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

    @Test
    @DisplayName("journalTraitement : sans token ou hors ligne → vide, sans lever (#1132)")
    void journal_traitement_degrade_proprement() {
        assertThat(new ClientVigieChiro("http://localhost:1", SANS_TOKEN).journalTraitement("6a49"))
                .isEmpty();
        assertThat(new ClientVigieChiro("http://localhost:1", TOKEN_ABC).journalTraitement("6a49"))
                .isEmpty();
    }

    @Test
    @DisplayName("corrigerObservation (#723) : sans token ou hors ligne → échec EXPLIQUÉ, distinct (#1284)")
    void correction_degrade_proprement() {
        ResultatCorrection sansToken = new ClientVigieChiro("http://localhost:1", SANS_TOKEN)
                .corrigerObservation("6a4f", 0, "5526", fr.univ_amu.iut.commun.model.CertitudeObservateur.SUR, true);
        ResultatCorrection horsLigne = new ClientVigieChiro("http://localhost:1", TOKEN_ABC)
                .corrigerObservation("6a4f", 0, "5526", fr.univ_amu.iut.commun.model.CertitudeObservateur.SUR, false);

        // Une écriture refusée est expliquée (jamais un booléen opaque), et depuis #1284 la cause est
        // la bonne : « aucun jeton » n'est plus déguisé en panne réseau, et réciproquement.
        assertThat(sansToken.estReussie()).isFalse();
        assertThat(sansToken.echec()).contains("jeton");
        assertThat(horsLigne.estReussie()).isFalse();
        assertThat(horsLigne.echec()).contains("injoignable");
    }
}

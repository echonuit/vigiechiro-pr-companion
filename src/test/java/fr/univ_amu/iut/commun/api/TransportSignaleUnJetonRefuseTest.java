package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// Le transport dit à la source du jeton qu'un `401` de l'API l'a refusé, et à elle seule (#6151).
///
/// C'est le seul endroit où un `401` atteint la source du jeton : les deux émissions qui le portent. Ce banc tient
/// les deux moitiés de la règle : un `401` de l'API se signale, avec le jeton qui est parti ; un refus
/// du stockage S3, qui refuse une URL signée et non le jeton, ne se signale pas, ni aucun autre statut.
class TransportSignaleUnJetonRefuseTest {

    private static final String URL_S3 = "https://vigiechiro.s3.amazonaws.com/zip/x.zip?Signature=a";

    /// Les jetons que la source s'est vu signaler comme refusés.
    private final List<String> refuses = new CopyOnWriteArrayList<>();

    private final FournisseurToken source = new FournisseurToken() {
        @Override
        public Optional<String> token() {
            return Optional.of("jeton-mort");
        }

        @Override
        public void jetonRefuse(String jeton) {
            refuses.add(jeton);
        }
    };

    @Test
    @DisplayName("#6151 : un 401 sur une lecture signale le jeton qui est parti")
    void un_401_en_lecture_se_signale() throws Exception {
        ReponseApi<String> reponse = transport(401).lire("/moi/participations");

        assertThat(reponse).isInstanceOf(ReponseApi.Refuse.class);
        assertThat(refuses).containsExactly("jeton-mort");
    }

    @Test
    @DisplayName("#6151 : un 401 sur une écriture signale le jeton qui est parti")
    void un_401_en_ecriture_se_signale() throws Exception {
        transport(401).ecrire("POST", "/fichiers", "{}", null, TransportVigieChiro.Rejeu.INTERDIT);

        assertThat(refuses).containsExactly("jeton-mort");
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {200, 403, 404, 422, 500})
    @DisplayName("#6151 : aucun autre statut de l'API ne fait oublier le jeton")
    void un_autre_statut_ne_se_signale_pas(int statut) throws Exception {
        transport(statut).lire("/moi");
        transport(statut).ecrire("PATCH", "/participations/p1", "{}", "e1", TransportVigieChiro.Rejeu.INTERDIT);

        assertThat(refuses).isEmpty();
    }

    @Test
    @DisplayName("#6151 : un 401 du stockage S3 refuse une URL signée, pas le jeton : rien ne se signale")
    void un_401_du_stockage_ne_se_signale_pas() throws Exception {
        ReponseApi<String> reponse =
                transport(401).deposerVersS3(URL_S3, () -> HttpRequest.BodyPublishers.ofString("x"), "application/zip");

        assertThat(reponse).isInstanceOf(ReponseApi.Refuse.class);
        assertThat(refuses).isEmpty();
    }

    private TransportVigieChiro transport(int statut) throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(appel -> reponse(statut));
        return new TransportVigieChiro(
                "https://api.exemple/api/v1", source, http, new PolitiqueReessai(delai -> {}, () -> 0.0));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<Object> reponse(int statut) {
        byte[] octets = "{}".getBytes(StandardCharsets.UTF_8);
        HttpResponse<Object> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(statut);
        when(reponse.body()).thenAnswer(appel -> new ByteArrayInputStream(octets));
        when(reponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (nom, valeur) -> true));
        return reponse;
    }
}

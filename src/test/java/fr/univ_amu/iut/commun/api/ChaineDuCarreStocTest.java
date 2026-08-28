package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.sites.model.ControleCarreStoc;
import fr.univ_amu.iut.sites.model.VerdictCarre;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La chaîne complète du carré STOC (#4592) : corps HTTP, lecture JSON, verdict rendu à l'observateur.
///
/// **Pourquoi cette classe existe, et pourquoi ICI.** `ControleCarreStocTest` bouchonne
/// `ClientVigieChiro` - c'est le bon choix pour ce qu'il vérifie, la forme des verdicts. Mais le
/// rembourrage du zéro de gauche vit dans [ReponsesVigieChiro], donc **en amont de ce bouchon** : un
/// test écrit là-bas stuberait un numéro déjà rembourré, resterait vert après qu'on ait retiré le
/// rembourrage, et aurait l'air de le tenir.
///
/// Le seul montage où ce test peut rougir traverse le transport. `TransportVigieChiro` est de portée
/// paquet, et le rendre public pour un test affaiblirait l'encapsulation d'une couche entière ; monter
/// un vrai serveur local demanderait `requires jdk.httpserver` dans le descripteur de **production**,
/// pour un besoin de test. Le test vient donc au joint plutôt que l'inverse.
class ChaineDuCarreStocTest {

    /// Position mesurée le 2026-08-26, à cheval sur les départements 04 et 05.
    private static final double LATITUDE = 44.44674980384396;

    private static final double LONGITUDE = 6.298116860416506;

    @Test
    @DisplayName("#4592 : la grille ampute le zéro du département 04, et le contrôle CONCORDE quand même")
    void departement_a_un_chiffre_ne_fait_pas_crier_le_controle() throws Exception {
        // La grille rend « 40110 » là où le catalogue déclare « 040110 » : les deux numéros désignent
        // le MÊME carré. Accuser ici, c'est avoir tort dans neuf départements, et toujours.
        ControleCarreStoc controle = new ControleCarreStoc(clientQuiRend("40110"));

        VerdictCarre verdict = controle.confronter("040110", LATITUDE, LONGITUDE);

        assertThat(verdict).isInstanceOf(VerdictCarre.Concorde.class);
        assertThat(verdict.severite()).isEqualTo(Severite.SUCCES);
    }

    @Test
    @DisplayName("Le rembourrage ne fabrique pas de concordance : un AUTRE carré diverge toujours")
    void un_autre_carre_diverge_toujours() {
        // Le garde d'à côté serait satisfait par un rembourrage qui rendrait tout égal à tout. Celui-ci
        // tient l'autre bord : la divergence réelle doit continuer de se dire, sinon le contrôle
        // n'aurait plus d'objet.
        ControleCarreStoc controle = new ControleCarreStoc(clientQuiRend("40111"));

        VerdictCarre verdict = controle.confronter("040110", LATITUDE, LONGITUDE);

        assertThat(verdict).isEqualTo(new VerdictCarre.Diverge("040111", "040110"));
        assertThat(verdict.severite()).isEqualTo(Severite.AVERTISSEMENT);
    }

    /// Client sur un transport dont le `HttpClient` rend le corps que la grille rendrait.
    private static ClientVigieChiro clientQuiRend(String numeroTelQueLaGrilleLeRend) {
        String corps = "{\"_items\":[{\"_id\":\"g1\",\"numero\":\"" + numeroTelQueLaGrilleLeRend + "\","
                + "\"centre\":{\"type\":\"Point\",\"coordinates\":[6.293767361,44.44544392]}}],"
                + "\"_meta\":{\"total\":1}}";
        HttpClient http = mock(HttpClient.class);
        try {
            when(http.send(any(), any())).thenAnswer(appel -> reponse(corps));
        } catch (Exception impossible) {
            throw new AssertionError("le bouchon ne lève pas", impossible);
        }
        return new ClientVigieChiro(new TransportVigieChiro(
                "http://api.exemple/v1", () -> Optional.of("abc"), http, new PolitiqueReessai(delai -> {}, () -> 0.0)));
    }

    private static HttpResponse<Object> reponse(String corps) {
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        HttpResponse<Object> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(200);
        when(reponse.body()).thenAnswer(appel -> new ByteArrayInputStream(octets));
        when(reponse.headers()).thenReturn(HttpHeaders.of(Map.<String, List<String>>of(), (n, v) -> true));
        return reponse;
    }
}

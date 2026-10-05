package fr.univ_amu.iut.lot.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientSurReseauSimule;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.SuiviReprise;
import java.io.ByteArrayInputStream;
import java.io.RandomAccessFile;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// La **provenance** d'un refus, lue sur le résultat d'un téléversement réel (#5598).
///
/// Le client n'est pas simulé : seul le réseau l'est. C'est ce qui permet d'éprouver le dépôt en
/// parties, où la première issue en échec peut venir de l'API (URL de partie, finalisation) comme du
/// stockage (`PUT` d'une partie). Un client simulé aurait rendu l'issue d'un seul appel, et caché
/// exactement l'étape que ce banc doit distinguer.
class TeleverseurArchiveTest {

    private static final String URL_S3 = "https://vigiechiro.s3.amazonaws.com/zip/pa-1/Car.zip?Signature=abc";

    /// L'étape qui reçoit le `403`, et la cause qu'elle doit donner.
    enum Etape {
        DECLARATION(false, CauseRefus.AUTHENTIFICATION),
        PUT_D_UN_BLOC(false, CauseRefus.STOCKAGE),
        FINALISATION_D_UN_BLOC(false, CauseRefus.AUTHENTIFICATION),
        // Trouvé par PIT à la clôture de #5596 : la déclaration d'un envoi en parties n'était jouée par
        // aucun cas, celle d'un bloc passant par un autre chemin.
        DECLARATION_DES_PARTIES(true, CauseRefus.AUTHENTIFICATION),
        URL_DE_PARTIE(true, CauseRefus.AUTHENTIFICATION),
        PUT_D_UNE_PARTIE(true, CauseRefus.STOCKAGE),
        FINALISATION_DES_PARTIES(true, CauseRefus.AUTHENTIFICATION);

        final boolean enParties;
        final CauseRefus attendue;

        Etape(boolean enParties, CauseRefus attendue) {
            this.enParties = enParties;
            this.attendue = attendue;
        }
    }

    @ParameterizedTest(name = "403 à l''étape {0}")
    @EnumSource(Etape.class)
    @DisplayName("#5598 : un 403 du stockage n'est pas un refus de droits, un 403 de l'API l'est")
    void la_cause_suit_la_provenance(Etape etape, @TempDir Path dossier) throws Exception {
        Path fichier = archive(dossier, etape.enParties);
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(appel -> repondre(appel.getArgument(0), etape));

        TeleverseurArchive.Resultat resultat = new TeleverseurArchive(ClientSurReseauSimule.sur(http))
                .televerser(fichier, "pa-1", f -> {}, SuiviReprise.SILENCIEUX);

        assertThat(resultat.reussi())
                .as("le 403 à l'étape %s doit faire échouer l'envoi", etape)
                .isFalse();
        assertThat(resultat.definitif()).as("un 403 n'est pas rejouable").isTrue();
        assertThat(resultat.cause())
                .as("un 403 du stockage conseillait une reconnexion qui ne peut rien pour une URL pré-signée")
                .isEqualTo(etape.attendue);
    }

    /// Le serveur simulé : tout réussit, sauf l'étape visée, qui répond `403`.
    private static HttpResponse<Object> repondre(HttpRequest requete, Etape etape) {
        String chemin = requete.uri().getPath();
        boolean stockage = requete.uri().getHost().endsWith("amazonaws.com");
        if (stockage) {
            boolean vise = etape == Etape.PUT_D_UN_BLOC || etape == Etape.PUT_D_UNE_PARTIE;
            return vise
                    ? reponse(403, "<Error><Code>AccessDenied</Code></Error>", Map.of())
                    : reponse(200, "", Map.of("ETag", List.of("\"etag-x\"")));
        }
        if (chemin.endsWith("/fichiers")) {
            return etape == Etape.DECLARATION || etape == Etape.DECLARATION_DES_PARTIES
                    ? reponse(403, "{}", Map.of())
                    : reponse(201, "{\"_id\": \"f-1\", \"s3_signed_url\": \"" + URL_S3 + "\"}", Map.of());
        }
        if (chemin.endsWith("/multipart")) {
            return etape == Etape.URL_DE_PARTIE
                    ? reponse(403, "{}", Map.of())
                    : reponse(200, "{\"s3_signed_url\": \"" + URL_S3 + "\"}", Map.of());
        }
        if ("DELETE".equals(requete.method())) {
            return reponse(200, "{}", Map.of());
        }
        boolean finalisation = etape == Etape.FINALISATION_D_UN_BLOC || etape == Etape.FINALISATION_DES_PARTIES;
        return finalisation ? reponse(403, "{}", Map.of()) : reponse(200, "{}", Map.of());
    }

    /// Une archive sous le seuil multipart, ou juste au-dessus : deux parties réelles.
    private static Path archive(Path dossier, boolean enParties) throws Exception {
        Path fichier = dossier.resolve("Car202013-2026-Pass1-Z1-1.zip");
        if (!enParties) {
            Files.write(fichier, new byte[] {1, 2, 3});
            return fichier;
        }
        try (RandomAccessFile creux = new RandomAccessFile(fichier.toFile(), "rw")) {
            creux.setLength(ClientVigieChiro.SEUIL_MULTIPART_OCTETS + 1);
        }
        return fichier;
    }

    private static HttpResponse<Object> reponse(int statut, String corps, Map<String, List<String>> entetes) {
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        HttpResponse<Object> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(statut);
        when(reponse.body()).thenAnswer(appel -> new ByteArrayInputStream(octets));
        when(reponse.headers()).thenReturn(HttpHeaders.of(entetes, (nom, valeur) -> true));
        return reponse;
    }
}

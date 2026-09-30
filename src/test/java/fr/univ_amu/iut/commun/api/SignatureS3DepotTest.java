package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le `PUT` S3 face à une **signature recalculée**, et non face à une valeur d'en-tête choisie à la main
/// (#5597).
///
/// Les tests de [ClientVigieChiroTest] acceptaient n'importe quel en-tête : toute archive réelle a été
/// refusée en `SignatureDoesNotMatch` sans qu'aucun ne rougisse. Le bouchon d'ici fait ce que fait S3
/// en signature v2 : il recalcule la chaîne signée à partir de la requête reçue, `Content-Type`
/// compris, et la compare à la signature portée par l'URL. Les URL sont produites comme les produit
/// `_sign_request` dans `vigiechiro-api` (`resources/fichiers.py`) : **avec** le mime pour un dépôt d'un
/// seul bloc, **sans** type pour une partie multipart.
class SignatureS3DepotTest {

    private static final String HOTE = "vigiechiro.s3.amazonaws.com";
    private static final String BUCKET = "vigiechiro";
    private static final String SECRET = "secret-de-banc";
    private static final String CLE = "AKIABANC";
    private static final long EXPIRES = 1_900_000_000L;

    @Test
    @DisplayName("#5597 : une partie multipart est acceptée par une URL signée sans type")
    void une_partie_multipart_respecte_la_signature_du_serveur(@TempDir Path dossier) throws Exception {
        Path fichier = dossier.resolve("Car202013-2026-Pass1-Z1-1.zip");
        Files.write(fichier, new byte[] {1, 2, 3, 4, 5, 6, 7});
        List<String> refus = new ArrayList<>();
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(appel -> {
            HttpRequest requete = appel.getArgument(0);
            if (HOTE.equals(requete.uri().getHost())) {
                return s3(requete, refus);
            }
            if (requete.uri().getPath().endsWith("/multipart")) {
                // `fichier_multipart_continue` : `_sign_request(verb='PUT', …)` SANS `content_type`.
                String urlPartie = signer("zip/pa-1/Car.zip", "", "partNumber=1&uploadId=u-1");
                return reponse(200, "{\"s3_signed_url\": \"" + urlPartie + "\"}", Map.of());
            }
            return reponse(200, "{}", Map.of());
        });

        ReponseApi<String> issue = clientAvec(http)
                .deposerEnParts("f-1", fichier, 3, fraction -> {}, SuiviReprise.SILENCIEUX)
                .reponse();

        assertThat(refus)
                .as("S3 recalcule la chaîne avec le Content-Type reçu : une partie qui en porte un que"
                        + " l'URL ne signe pas est refusée, et c'est ce qui a arrêté toute archive réelle")
                .isEmpty();
        assertThat(issue).isInstanceOf(ReponseApi.Succes.class);
    }

    @Test
    @DisplayName("#5597 : un dépôt d'un seul bloc garde le type que sa signature couvre")
    void un_depot_d_un_seul_bloc_garde_son_type() throws Exception {
        List<String> refus = new ArrayList<>();
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(appel -> s3(appel.getArgument(0), refus));
        // `_s3_create_singlepart` : `_sign_request(verb='PUT', …, content_type=payload['mime'])`.
        String url = signer("zip/pa-1/Car.zip", "application/zip", "");

        ReponseApi<String> issue = clientAvec(http).televerserVersS3(url, new byte[] {1}, "application/zip");

        assertThat(refus)
                .as("retirer le type partout casserait l'autre chemin : sa signature, elle, le couvre")
                .isEmpty();
        assertThat(issue).isInstanceOf(ReponseApi.Succes.class);
    }

    /// Ce que fait S3 d'un `PUT` pré-signé en v2 : recalculer la signature depuis la requête reçue.
    private static HttpResponse<Object> s3(HttpRequest requete, List<String> refus) throws Exception {
        String typeRecu = requete.headers().firstValue("Content-Type").orElse("");
        Map<String, String> parametres = parametres(requete.uri().getRawQuery());
        String objet = requete.uri().getPath().substring(1);
        String attendue = signature(
                chaineASigner(objet, typeRecu, sousRessource(requete.uri().getRawQuery())));
        if (!attendue.equals(parametres.get("Signature"))) {
            refus.add(typeRecu);
            return reponse(403, "<Error><Code>SignatureDoesNotMatch</Code></Error>", Map.of());
        }
        return reponse(200, "", Map.of("ETag", List.of("\"etag-x\"")));
    }

    /// L'URL que produit `_sign_request`, pour `objet`, le type signé et l'éventuelle sous-ressource.
    private static String signer(String objet, String typeSigne, String sousRessource) throws Exception {
        String signature = signature(chaineASigner(objet, typeSigne, sousRessource));
        String tete = sousRessource.isEmpty() ? "?" : "?" + sousRessource + "&";
        return "https://" + HOTE + "/" + objet + tete + "AWSAccessKeyId=" + CLE + "&Expires=" + EXPIRES + "&Signature="
                + URLEncoder.encode(signature, StandardCharsets.UTF_8);
    }

    private static String chaineASigner(String objet, String type, String sousRessource) {
        String chemin = "/" + BUCKET + "/" + objet + (sousRessource.isEmpty() ? "" : "?" + sousRessource);
        return "PUT\n\n" + type + "\n" + EXPIRES + "\n" + chemin;
    }

    private static String signature(String chaine) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(chaine.getBytes(StandardCharsets.UTF_8)));
    }

    /// Les paramètres de la requête qui ne sont pas ceux de la signature : ce que S3 signe avec le chemin.
    private static String sousRessource(String requete) {
        StringJoiner garde = new StringJoiner("&");
        for (String paire : requete.split("&")) {
            String nom = paire.split("=", 2)[0];
            if (!List.of("AWSAccessKeyId", "Expires", "Signature").contains(nom)) {
                garde.add(paire);
            }
        }
        return garde.toString();
    }

    private static Map<String, String> parametres(String requete) {
        Map<String, String> lus = new java.util.HashMap<>();
        for (String paire : requete.split("&")) {
            String[] morceaux = paire.split("=", 2);
            lus.put(morceaux[0], morceaux.length > 1 ? URLDecoder.decode(morceaux[1], StandardCharsets.UTF_8) : "");
        }
        return lus;
    }

    private static ClientVigieChiro clientAvec(HttpClient http) {
        return new ClientVigieChiro(new TransportVigieChiro(
                "http://api.exemple/v1", () -> Optional.of("abc"), http, new PolitiqueReessai(delai -> {}, () -> 0.0)));
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

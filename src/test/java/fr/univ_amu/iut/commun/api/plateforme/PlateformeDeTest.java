package fr.univ_amu.iut.commun.api.plateforme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/// Monte la **plateforme de test** de l'ADR 5641 par Testcontainers (#5663) : l'API Vigie-Chiro à la
/// révision épinglée, son Mongo, le faux S3 en TLS, et l'état de départ déclaré.
///
/// Une plateforme **par JVM**, montée au premier test qui la demande et partagée par les suivants.
/// Ryuk, le conteneur de nettoyage de Testcontainers, la retire à la fin du processus.
///
/// L'ordre de montage est imposé. Le faux S3 démarre **avant** l'API, parce que l'API rend comme URL
/// de dépôt `DEV_FAKE_S3_URL`, et que cette URL doit être celle que **la JVM** joint : l'hôte et le
/// port publiés du faux S3, connus seulement une fois qu'il tourne.
///
/// La confiance TLS se pose par [SSLContext#setDefault]. `TransportVigieChiro` construit son
/// `HttpClient` sans contexte, donc sur celui par défaut, que la JVM fige à la première lecture. Cela
/// ne tient que si aucun client n'a été construit avant dans ce fork, d'où le profil
/// `-Pplateforme-de-test`, qui ne joue que ce tag.
public final class PlateformeDeTest implements BeforeAllCallback {

    /// Ce que la plateforme rend aux tests : où la joindre, sous quel jeton, et ce qu'elle contient.
    public record Acces(String urlDeBase, String urlS3, Map<String, String> jetons, Map<String, String> ids) {

        /// Le jeton frappé pour l'utilisateur déclaré sous cette clé.
        public String jeton(String cle) {
            String jeton = jetons.get(cle);
            if (jeton == null) {
                throw new IllegalArgumentException("aucun utilisateur déclaré sous la clé " + cle);
            }
            return jeton;
        }

        /// L'identifiant de l'objet déclaré sous `collection:cle`.
        public String id(String collectionEtCle) {
            String id = ids.get(collectionEtCle);
            if (id == null) {
                throw new IllegalArgumentException("aucun objet déclaré sous " + collectionEtCle);
            }
            return id;
        }

        /// Le journal des PUT reçus par le faux S3, tel qu'il le rend.
        public String journalS3() {
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest requete =
                        HttpRequest.newBuilder(URI.create(urlS3 + "/_journal")).build();
                return client.send(requete, HttpResponse.BodyHandlers.ofString())
                        .body();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private static final String MONGO_DANS_LE_RESEAU = "mongodb://mongo:27017/vigiechiro";
    private static Acces acces;

    @Override
    public void beforeAll(ExtensionContext contexte) {
        acces();
    }

    /// La plateforme, montée si elle ne l'est pas encore.
    public static synchronized Acces acces() {
        if (acces == null) {
            acces = monter(dossierDeLaPlateforme());
        }
        return acces;
    }

    private static Acces monter(Path dossier) {
        Properties epingles = epingles(dossier);
        Network reseau = Network.newNetwork();

        GenericContainer<?> mongo = new GenericContainer<>(DockerImageName.parse(epingles.getProperty("mongo.image"))
                        .asCompatibleSubstituteFor("mongo"))
                .withNetwork(reseau)
                .withNetworkAliases("mongo")
                .withTmpFs(Map.of("/data/db", "rw"))
                .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1));
        mongo.start();

        GenericContainer<?> fauxS3 = new GenericContainer<>(
                        new ImageFromDockerfile("vigiechiro-plateforme-faux-s3", false)
                                .withDockerfile(dossier.resolve("faux-s3.Dockerfile")))
                .withNetwork(reseau)
                .withExposedPorts(8443)
                .waitingFor(Wait.forListeningPort());
        String hote = fauxS3.getHost();
        fauxS3.withEnv("FAUX_S3_NOMS", nomsDuCertificat(hote));
        fauxS3.start();
        String urlS3 = "https://" + hote + ":" + fauxS3.getMappedPort(8443);
        X509Certificate certificat =
                fauxS3.copyFileFromContainer("/certificats/faux-s3.pem", PlateformeDeTest::lireCertificat);
        faireConfiance(certificat);
        System.setProperty("vigiechiro.s3.hotes", hote);

        GenericContainer<?> api = new GenericContainer<>(new ImageFromDockerfile("vigiechiro-plateforme-api", false)
                        .withDockerfile(dossier.resolve("api.Dockerfile"))
                        .withBuildArg("REVISION", epingles.getProperty("api.revision")))
                .withNetwork(reseau)
                .withEnv("MONGO_HOST", MONGO_DANS_LE_RESEAU)
                .withEnv("DEV_FAKE_S3_URL", urlS3)
                .withExposedPorts(8080)
                .waitingFor(Wait.forListeningPort());
        api.start();

        JsonObject amorce = amorcer(api, dossier);
        deposer(urlS3, amorce.getAsJsonObject("objets"));
        return new Acces(
                "http://" + api.getHost() + ":" + api.getMappedPort(8080) + "/api/v1",
                urlS3,
                enTable(amorce.getAsJsonObject("jetons")),
                enTable(amorce.getAsJsonObject("ids")));
    }

    /// Matérialise l'état de départ DANS le conteneur de l'API, qui a déjà `pymongo` (#5662).
    private static JsonObject amorcer(GenericContainer<?> api, Path dossier) {
        api.copyFileToContainer(MountableFile.forHostPath(dossier.resolve("amorcer.py")), "/tmp/amorcer.py");
        api.copyFileToContainer(
                MountableFile.forHostPath(dossier.resolve("etat-de-depart.json")), "/tmp/etat-de-depart.json");
        try {
            Container.ExecResult rendu = api.execInContainer(
                    "python", "/tmp/amorcer.py",
                    "--declaration", "/tmp/etat-de-depart.json",
                    "--mongo", MONGO_DANS_LE_RESEAU);
            if (rendu.getExitCode() != 0) {
                throw new IllegalStateException("l'amorçage a échoué : " + rendu.getStderr());
            }
            return JsonParser.parseString(rendu.getStdout()).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /// Dépose dans le faux S3 les fichiers que l'état de départ déclare, chacun à sa clé `s3_id` (#5747).
    ///
    /// C'est la JVM qui dépose, et non l'amorçage : en mode `DEV_FAKE_S3_URL`, l'API rend comme URL
    /// d'accès l'adresse que la JVM joint, et son propre conteneur ne la joint pas. Le contexte TLS
    /// par défaut accepte déjà le certificat du faux S3.
    private static void deposer(String urlS3, JsonObject objets) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (Map.Entry<String, JsonElement> objet : objets.entrySet()) {
                HttpRequest requete = HttpRequest.newBuilder(URI.create(urlS3 + "/" + objet.getKey()))
                        .PUT(HttpRequest.BodyPublishers.ofString(
                                objet.getValue().getAsString()))
                        .build();
                int statut = client.send(requete, HttpResponse.BodyHandlers.discarding())
                        .statusCode();
                if (statut != 200) {
                    throw new IllegalStateException("le faux S3 refuse " + objet.getKey() + " : " + statut);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /// Les noms que le certificat du faux S3 doit couvrir : l'hôte que Testcontainers rend, sous la
    /// forme que la JVM compare (un nom, ou une adresse), et la boucle locale.
    static String nomsDuCertificat(String hote) {
        boolean adresse = hote.matches("[0-9.]+") || hote.contains(":");
        String premier = (adresse ? "IP:" : "DNS:") + hote;
        return "127.0.0.1".equals(hote) ? premier : premier + ",IP:127.0.0.1";
    }

    private static X509Certificate lireCertificat(InputStream flux) throws IOException {
        try {
            byte[] octets = flux.readAllBytes();
            return (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(octets));
        } catch (CertificateException e) {
            throw new IOException("certificat du faux S3 illisible", e);
        }
    }

    /// Les autorités habituelles, et en plus le certificat du faux S3 : une URL signée de la plateforme
    /// nationale resterait joignable dans ce fork.
    private static void faireConfiance(X509Certificate certificat) {
        try {
            X509TrustManager habituel = gestionnaire(null);
            KeyStore magasin = KeyStore.getInstance(KeyStore.getDefaultType());
            magasin.load(null, null);
            magasin.setCertificateEntry("faux-s3", certificat);
            X509TrustManager fauxS3 = gestionnaire(magasin);
            SSLContext contexte = SSLContext.getInstance("TLS");
            contexte.init(null, new TrustManager[] {new ConfianceDouble(fauxS3, habituel)}, null);
            SSLContext.setDefault(contexte);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("impossible de faire accepter le certificat du faux S3", e);
        }
    }

    private static X509TrustManager gestionnaire(KeyStore magasin) throws GeneralSecurityException {
        TrustManagerFactory fabrique = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        fabrique.init(magasin);
        for (TrustManager candidat : fabrique.getTrustManagers()) {
            if (candidat instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new IllegalStateException("aucun gestionnaire de confiance X.509");
    }

    private static Properties epingles(Path dossier) {
        Properties epingles = new Properties();
        try (InputStream flux = Files.newInputStream(dossier.resolve("epingles.properties"))) {
            epingles.load(flux);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return epingles;
    }

    private static Map<String, String> enTable(JsonObject objet) {
        Map<String, String> table = new HashMap<>();
        objet.entrySet()
                .forEach(entree -> table.put(entree.getKey(), entree.getValue().getAsString()));
        return Map.copyOf(table);
    }

    /// `scripts/plateforme-de-test/`, cherché en remontant depuis le dossier de travail : surefire lance
    /// les tests à la racine du module, un IDE parfois ailleurs.
    static Path dossierDeLaPlateforme() {
        for (Path courant = Path.of("").toAbsolutePath(); courant != null; courant = courant.getParent()) {
            Path candidat = courant.resolve("scripts/plateforme-de-test");
            if (Files.isRegularFile(candidat.resolve("epingles.properties"))) {
                return candidat;
            }
        }
        throw new IllegalStateException(
                "scripts/plateforme-de-test introuvable depuis " + Path.of("").toAbsolutePath());
    }

    /// Accepte ce que l'un OU l'autre gestionnaire accepte : le faux S3, ou une autorité habituelle.
    private record ConfianceDouble(X509TrustManager premier, X509TrustManager second) implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chaine, String type) throws CertificateException {
            second.checkClientTrusted(chaine, type);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chaine, String type) throws CertificateException {
            try {
                premier.checkServerTrusted(chaine, type);
            } catch (CertificateException refuseParLeFauxS3) {
                second.checkServerTrusted(chaine, type);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] a = premier.getAcceptedIssuers();
            X509Certificate[] b = second.getAcceptedIssuers();
            X509Certificate[] tous = java.util.Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, tous, a.length, b.length);
            return tous;
        }
    }
}

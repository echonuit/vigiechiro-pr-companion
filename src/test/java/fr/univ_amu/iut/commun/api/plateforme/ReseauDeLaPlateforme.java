package fr.univ_amu.iut.commun.api.plateforme;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.testcontainers.containers.Network;
import org.testcontainers.toxiproxy.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

/// Toxiproxy, placé devant l'API et le faux S3 de la plateforme de test, et les profils qu'il pose
/// (#6258, chantier #5646).
///
/// Deux mandataires, l'un par service. La JVM joint l'API et le faux S3 par eux, et l'adresse de dépôt
/// que l'API rend (`DEV_FAKE_S3_URL`) est celle du mandataire du faux S3 : sans cela, les téléversements
/// passeraient à côté. Le TLS du faux S3 traverse son mandataire, qui ne voit que des octets.
///
/// Le navigateur des bancs de la fiche web garde son chemin dans le réseau, par l'alias de l'API : il
/// lit une page, il ne transfère rien que ces profils doivent ralentir.
final class ReseauDeLaPlateforme {

    /// Le port du mandataire de l'API, dans le conteneur de Toxiproxy.
    private static final int PORT_DE_L_API = 8666;

    /// Le port du mandataire du faux S3.
    private static final int PORT_DU_FAUX_S3 = 8667;

    private final ToxiproxyContainer conteneur;

    private final List<Proxy> mandataires;

    private ReseauDeLaPlateforme(ToxiproxyContainer conteneur, List<Proxy> mandataires) {
        this.conteneur = conteneur;
        this.mandataires = mandataires;
    }

    /// Démarre Toxiproxy dans le réseau de la plateforme, et ouvre ses deux mandataires vers les alias
    /// de l'API et du faux S3. Ils peuvent exister avant leurs services : Toxiproxy ne résout l'amont
    /// qu'à la connexion.
    static ReseauDeLaPlateforme demarrer(String image, Network reseau, String amontApi, String amontFauxS3) {
        ToxiproxyContainer conteneur = new ToxiproxyContainer(
                        DockerImageName.parse(image).asCompatibleSubstituteFor("ghcr.io/shopify/toxiproxy"))
                .withNetwork(reseau);
        conteneur.start();
        ToxiproxyClient client = new ToxiproxyClient(conteneur.getHost(), conteneur.getControlPort());
        try {
            Proxy api = client.createProxy("api", "0.0.0.0:" + PORT_DE_L_API, amontApi);
            Proxy fauxS3 = client.createProxy("faux-s3", "0.0.0.0:" + PORT_DU_FAUX_S3, amontFauxS3);
            return new ReseauDeLaPlateforme(conteneur, List.of(api, fauxS3));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// L'hôte par lequel la JVM joint les mandataires.
    String hote() {
        return conteneur.getHost();
    }

    /// Le port, vu de la JVM, du mandataire de l'API.
    int portDeLApi() {
        return conteneur.getMappedPort(PORT_DE_L_API);
    }

    /// Le port, vu de la JVM, du mandataire du faux S3.
    int portDuFauxS3() {
        return conteneur.getMappedPort(PORT_DU_FAUX_S3);
    }

    /// Pose le profil sur les deux mandataires, après avoir retiré le précédent.
    void poser(ProfilReseau profil) {
        try {
            for (Proxy mandataire : mandataires) {
                for (Toxic toxique : mandataire.toxics().getAll()) {
                    toxique.remove();
                }
                if (profil == ProfilReseau.COUPURE) {
                    mandataire.disable();
                    continue;
                }
                mandataire.enable();
                if (profil.latenceMs() > 0) {
                    mandataire.toxics().latency("latence", ToxicDirection.DOWNSTREAM, profil.latenceMs());
                }
                if (profil.debitKoParS() > 0) {
                    mandataire.toxics().bandwidth("debit", ToxicDirection.UPSTREAM, profil.debitKoParS());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

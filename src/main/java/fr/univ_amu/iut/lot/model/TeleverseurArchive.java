package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FichierSigne;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SuiviReprise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.function.DoubleConsumer;

/// Téléverse **une archive** vers Vigie-Chiro (#984, #2354), extrait de [DepotVigieChiro] (Extract
/// Class, plafond God Class) : le dépôt orchestre le plan **par unité** ; ce téléverseur sait envoyer
/// une archive - **d'un bloc** si elle est petite, **en parties réessayables** au-delà du seuil, avec le
/// réessai porté par le transport (#2354).
final class TeleverseurArchive {

    private final ClientVigieChiro client;

    TeleverseurArchive(ClientVigieChiro client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /// Téléverse `fichier` en trois temps (déclaration → `PUT` S3 → finalisation), **d'un bloc** ou **en
    /// parties** selon sa taille (#2354). Rend l'id distant en cas de succès, la raison de l'étape fautive
    /// sinon. Un fichier absent (archive à régénérer) est un échec porté, pas une exception.
    Resultat televerser(Path fichier, String participationId, DoubleConsumer progression, SuiviReprise reprise) {
        if (fichier == null) {
            return Resultat.echec("fichier introuvable sur le disque (archives à régénérer ?)");
        }
        String titre = fichier.getFileName().toString();
        // Grosse archive (#2354) : découpée en parties réessayables séparément, pour qu'une coupure ne
        // fasse pas rejouer 700 Mo mais la seule partie en vol.
        if (tailleDe(fichier) > ClientVigieChiro.SEUIL_MULTIPART_OCTETS) {
            return televerserEnParts(fichier, titre, participationId, progression, reprise);
        }
        // #1284 : chaque étape échoue avec sa cause exacte (non connecté / injoignable / HTTP n),
        // plus jamais un « refusé par VigieChiro » générique quand c'était le réseau.
        ReponseApi<FichierSigne> declaration = client.creerFichier(titre, participationId);
        if (!(declaration instanceof ReponseApi.Succes<FichierSigne>(FichierSigne signe))) {
            return Resultat.echec("déclaration du fichier : " + causeDe(declaration));
        }
        if (!client.televerserVersS3(signe.urlSignee(), fichier, mime(titre), progression, reprise)) {
            return Resultat.echec("téléversement S3 refusé (réseau ou fichier illisible)");
        }
        ReponseApi<String> finalisation = client.finaliserFichier(signe.id());
        if (finalisation.echec().isPresent()) {
            return Resultat.echec("finalisation : " + causeDe(finalisation));
        }
        return Resultat.reussi(signe.id());
    }

    /// Téléverse une grosse archive **en parties** (#2354) : déclaration multipart, dépôt partie par
    /// partie (chaque partie réessayée seule, `ETag` collecté), finalisation. À l'échec définitif, on
    /// abandonne l'upload côté serveur (`DELETE`, best-effort) pour ne pas laisser de parties orphelines,
    /// puis on rend la cause.
    private Resultat televerserEnParts(
            Path fichier, String titre, String participationId, DoubleConsumer progression, SuiviReprise reprise) {
        ReponseApi<String> declaration = client.creerFichierMultipart(titre, participationId);
        if (!(declaration instanceof ReponseApi.Succes<String>(String fichierId))) {
            return Resultat.echec("déclaration multipart : " + causeDe(declaration));
        }
        ReponseApi<String> depot = client.deposerEnParts(fichierId, fichier, mime(titre), progression, reprise);
        if (depot.echec().isPresent()) {
            client.abandonnerFichier(fichierId);
            return Resultat.echec("téléversement multipart : " + causeDe(depot));
        }
        return Resultat.reussi(fichierId);
    }

    /// Taille du fichier, ou `-1` s'il est illisible : le seuil multipart n'est alors pas franchi et
    /// l'échec sera rapporté par la lecture de l'étape suivante, avec sa cause exacte.
    private static long tailleDe(Path fichier) {
        try {
            return Files.size(fichier);
        } catch (IOException illisible) {
            return -1;
        }
    }

    /// Cause d'échec en clair d'une étape du téléversement (vocabulaire unique [ReponseApi#echec()]).
    private static String causeDe(ReponseApi<?> reponse) {
        return reponse.echec().orElse("issue inattendue");
    }

    /// Type de média déduit de l'extension, pour le `Content-Type` du `PUT` S3 (il doit correspondre à la
    /// signature calculée côté serveur). `.wav` → `audio/x-wav`, `.zip` → `application/zip`, sinon binaire.
    private static String mime(String nom) {
        String minuscule = nom.toLowerCase(Locale.ROOT);
        if (minuscule.endsWith(".wav")) {
            return "audio/x-wav";
        }
        return minuscule.endsWith(".zip") ? "application/zip" : "application/octet-stream";
    }

    /// Issue d'un téléversement d'archive : l'id distant en cas de succès, la raison sinon.
    record Resultat(String fichierId, String raison) {
        static Resultat reussi(String fichierId) {
            return new Resultat(fichierId, null);
        }

        static Resultat echec(String raison) {
            return new Resultat(null, raison);
        }

        boolean reussi() {
            return fichierId != null;
        }
    }
}

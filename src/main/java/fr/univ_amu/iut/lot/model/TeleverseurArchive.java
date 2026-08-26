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

/// Téléverse **une archive** vers Vigie-Chiro (#984, #2354) : le dépôt orchestre le plan **par
/// unité** ; ce téléverseur sait envoyer une archive - **d'un bloc** si elle est petite, **en
/// parties réessayables** au-delà du seuil, avec le réessai porté par le transport (#2354).
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
        // Mesuré UNE fois : ce chiffre décide de la voie d'envoi, et repart ensuite dans l'issue pour que
        // la fin de dépôt puisse dire le volume téléversé (#2653) sans le recalculer.
        long octets = tailleDe(fichier);
        // Grosse archive (#2354) : découpée en parties réessayables séparément, pour qu'une coupure ne
        // fasse pas rejouer 700 Mo mais la seule partie en vol.
        if (octets > ClientVigieChiro.SEUIL_MULTIPART_OCTETS) {
            return televerserEnParts(fichier, titre, participationId, progression, reprise, octets);
        }
        // #1284 : chaque étape échoue avec sa cause exacte (non connecté / injoignable / HTTP n),
        // plus jamais un « refusé par VigieChiro » générique quand c'était le réseau.
        ReponseApi<FichierSigne> declaration = client.creerFichier(titre, participationId);
        if (!(declaration instanceof ReponseApi.Succes<FichierSigne>(FichierSigne signe))) {
            return Resultat.echec("déclaration du fichier : " + causeDe(declaration), declaration);
        }
        ReponseApi<String> depose =
                client.televerserVersS3(signe.urlSignee(), fichier, mime(titre), progression, reprise);
        if (depose.echec().isPresent()) {
            return Resultat.echec("téléversement S3 : " + causeDe(depose), depose);
        }
        ReponseApi<String> finalisation = client.finaliserFichier(signe.id());
        if (finalisation.echec().isPresent()) {
            return Resultat.echec("finalisation : " + causeDe(finalisation), finalisation);
        }
        return Resultat.reussi(signe.id(), octets);
    }

    /// Téléverse une grosse archive **en parties** (#2354) : déclaration multipart, dépôt partie par
    /// partie (chaque partie réessayée seule, `ETag` collecté), finalisation. À l'échec définitif, on
    /// abandonne l'upload côté serveur (`DELETE`, best-effort) pour ne pas laisser de parties orphelines,
    /// puis on rend la cause.
    private Resultat televerserEnParts(
            Path fichier,
            String titre,
            String participationId,
            DoubleConsumer progression,
            SuiviReprise reprise,
            long octets) {
        ReponseApi<String> declaration = client.creerFichierMultipart(titre, participationId);
        if (!(declaration instanceof ReponseApi.Succes<String>(String fichierId))) {
            return Resultat.echec("déclaration multipart : " + causeDe(declaration), declaration);
        }
        ReponseApi<String> depot = client.deposerEnParts(fichierId, fichier, mime(titre), progression, reprise);
        if (depot.echec().isPresent()) {
            client.abandonnerFichier(fichierId);
            return Resultat.echec("téléversement multipart : " + causeDe(depot), depot);
        }
        return Resultat.reussi(fichierId, octets);
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

    /// Issue d'un téléversement d'archive : l'id distant en cas de succès, la raison sinon, et le
    /// **volume effectivement parti** (#2653).
    ///
    /// Ce volume n'est pas calculé pour l'occasion : [#televerser] mesure déjà le fichier pour décider
    /// entre l'envoi d'un bloc et l'envoi en parties, et jetait ce chiffre. Même situation qu'à l'import
    /// avant #2586, où le garde-fou d'espace disque parcourait les originaux puis oubliait le volume.
    ///
    /// @param octets taille du fichier parti, `0` sur un échec (rien n'est en ligne) ou un fichier illisible
    record Resultat(String fichierId, String raison, boolean definitif, CauseRefus cause, long octets) {
        static Resultat reussi(String fichierId, long octets) {
            return new Resultat(fichierId, null, false, null, Math.max(0, octets));
        }

        /// Échec dont on ne sait pas s'il se retente : **conservateur**, donc rejouable.
        ///
        /// Il ne reste qu'un cas : le **fichier introuvable** sur le disque, où aucune réponse serveur
        /// n'existe. Le `PUT` S3 d'un seul bloc l'empruntait aussi, faute de rendre son statut ; depuis
        /// #3688 il rend son issue et passe par la variante ci-dessous.
        static Resultat echec(String raison) {
            return new Resultat(null, raison, false, null, 0);
        }

        /// Échec dont la **réponse** dit s'il se retente (#3469).
        ///
        /// Le caractère définitif vient de `ReponseApi.estReessayable()`, et jamais d'une lecture du
        /// texte de la raison : la même panne s'y écrit de trop de façons pour qu'on la redevine.
        /// La cause vient du **statut**, decidee ici (#3689) : elle dira plus tard ce qui peut la
        /// lever - une reconnexion reussie pour un refus d authentification, rien pour un contenu
        /// refuse. Nulle sur un echec rejouable, qui n a pas a en porter.
        static Resultat echec(String raison, ReponseApi<?> reponse) {
            return new Resultat(null, raison, !reponse.estReessayable(), CauseRefus.de(reponse), 0);
        }

        boolean reussi() {
            return fichierId != null;
        }
    }
}

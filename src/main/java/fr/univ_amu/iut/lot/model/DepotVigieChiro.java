package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FichierSigne;
import fr.univ_amu.iut.commun.api.ResultatParticipation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// **Dépôt d'une nuit** (un passage) sur l'API VigieChiro (#142) : s'assure d'abord que la **participation
/// existe** — elle est **réutilisée** si le passage y est déjà lié (créée à l'import ou à un dépôt
/// précédent), **créée en repli** sinon (via [SynchronisationParticipation]) —, puis **téléverse** les
/// `fichiers` fournis (déclaration → `PUT` S3 → finalisation, cf. [ClientVigieChiro]).
///
/// Service d'**orchestration** (patron de `ServiceLot`) : la construction de la participation, la garde
/// « site rattaché » et le lien `ENTITE_PASSAGE` sont **délégués** à la passerelle `passage` ; le dépôt ne
/// garde que l'**upload**. La politique du choix des fichiers (séquences vs archives) est décidée par
/// l'appelant qui passe la liste des [Path].
///
/// Réutiliser la participation liée (au lieu d'en recréer une à chaque appel) rend le dépôt **reprenable**
/// et évite les doublons. En repli, un site non rattaché / un refus de création lève [RegleMetierException].
public final class DepotVigieChiro {

    private final SynchronisationParticipation participations;
    private final ClientVigieChiro client;

    public DepotVigieChiro(SynchronisationParticipation participations, ClientVigieChiro client) {
        this.participations = Objects.requireNonNull(participations, "participations");
        this.client = Objects.requireNonNull(client, "client");
    }

    /// Dépose la nuit `idPassage` : réutilise ou crée sa participation, puis téléverse chaque fichier de
    /// `fichiers`. Renvoie un [BilanDepot] (participation + fichiers déposés / en échec) ; un échec de
    /// téléversement isolé n'interrompt pas les suivants (dépôt partiel relançable), mais l'échec de
    /// **création** de la participation lève.
    public BilanDepot deposer(Long idPassage, List<Path> fichiers) {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(fichiers, "fichiers");

        String participationId =
                participations.participationDe(idPassage).orElseGet(() -> creerParticipation(idPassage));

        List<String> echecs = new ArrayList<>();
        int deposees = 0;
        for (Path fichier : fichiers) {
            if (televerser(fichier)) {
                deposees++;
            } else {
                echecs.add(fichier.getFileName().toString());
            }
        }
        return new BilanDepot(participationId, deposees, echecs);
    }

    /// Crée la participation (repli lazy quand elle n'a pas été créée à l'import) et renvoie son id, ou lève
    /// avec le détail du refus VigieChiro.
    private String creerParticipation(Long idPassage) {
        ResultatParticipation creation = participations.creerPour(idPassage);
        return creation.id()
                .orElseThrow(() -> new RegleMetierException(
                        "Création de la participation refusée par VigieChiro : " + creation.echec()));
    }

    /// Téléverse un fichier en trois temps (déclaration → `PUT` S3 → finalisation). `false` si l'une des
    /// étapes échoue ou si le fichier est illisible (l'appelant le compte en échec, sans interrompre).
    private boolean televerser(Path fichier) {
        try {
            String titre = fichier.getFileName().toString();
            Optional<FichierSigne> signe = client.creerFichier(titre);
            if (signe.isEmpty()) {
                return false;
            }
            byte[] octets = Files.readAllBytes(fichier);
            return client.televerserVersS3(signe.get().urlSignee(), octets, mime(titre))
                    && client.finaliserFichier(signe.get().id());
        } catch (IOException illisible) {
            return false;
        }
    }

    /// Type de média déduit de l'extension du fichier, pour le `Content-Type` du `PUT` S3 (il doit
    /// correspondre à la signature calculée côté serveur). `.wav` → `audio/x-wav`, sinon binaire.
    private static String mime(String nom) {
        return nom.toLowerCase().endsWith(".wav") ? "audio/x-wav" : "application/octet-stream";
    }
}

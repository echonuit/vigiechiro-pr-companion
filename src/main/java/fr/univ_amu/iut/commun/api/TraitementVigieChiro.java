package fr.univ_amu.iut.commun.api;

import java.util.Objects;
import java.util.Optional;

/// **Traitement serveur** d'une participation : le lancer (`POST /participations/#id/compute`) et lire où
/// il en est (`GET /participations/#id` → bloc `traitement`).
///
/// Collaborateur de [ClientVigieChiro] plutôt que méthodes de plus sur lui : le client est un **transport**
/// (authentifier, émettre, dégrader proprement), tandis que **décider** de ce qu'un refus veut dire est une
/// règle métier. La séparation n'est pas cosmétique — c'est ce que PMD constatait en voyant le client
/// franchir le seuil de la God Class.
public final class TraitementVigieChiro {

    private final ClientVigieChiro client;

    public TraitementVigieChiro(ClientVigieChiro client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /// Lance le traitement Tadarida de la participation.
    ///
    /// Un refus n'est pas forcément un échec : le serveur répond `400 « Already »` quand un traitement est
    /// déjà planifié ou en cours **depuis moins de 24 h** (`participations.py:231-237`) — il travaille, il
    /// n'y a qu'à attendre. Plutôt que de décrypter son message d'erreur, on **relit l'état** : c'est lui
    /// qui fait foi (#1261).
    public ResultatLancement lancer(String participationId) {
        Optional<ClientVigieChiro.ReponseHttp> reponse =
                client.postDetaille("/participations/" + participationId + "/compute", RequetesVigieChiro.traitement());
        if (reponse.isEmpty()) {
            return ResultatLancement.injoignable();
        }
        ClientVigieChiro.ReponseHttp refusOuSucces = reponse.get();
        if (refusOuSucces.statut() / 100 == 2) {
            return ResultatLancement.accepte();
        }
        Traitement etat = etat(participationId);
        return etat.enAttente()
                ? ResultatLancement.dejaLance(etat)
                : ResultatLancement.refuse(refusOuSucces.statut(), refusOuSucces.corps());
    }

    /// État du traitement de la participation, ou [Traitement#absent()] si elle est injoignable ou n'a
    /// jamais été calculée (#1260).
    public Traitement etat(String participationId) {
        return client.participation(participationId)
                .map(ParticipationDetail::traitement)
                .orElseGet(Traitement::absent);
    }
}

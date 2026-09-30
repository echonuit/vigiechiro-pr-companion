package fr.univ_amu.iut.commun.api;

import java.net.http.HttpClient;
import java.util.Optional;

/// Un vrai [ClientVigieChiro] dont seul le réseau est simulé, pour les bancs **hors** de ce paquet
/// (#5598).
///
/// Le client et son transport ne se construisent qu'ici, par des constructeurs de paquet. Un banc qui
/// doit éprouver l'enchaînement réel des requêtes, et non l'issue d'un appel simulé, passe par cette
/// porte. Jeton présent, politique de réessai sans attente.
public final class ClientSurReseauSimule {

    private ClientSurReseauSimule() {}

    public static ClientVigieChiro sur(HttpClient reseau) {
        return new ClientVigieChiro(new TransportVigieChiro(
                "http://api.exemple/v1", () -> Optional.of("abc"), reseau, new PolitiqueReessai(d -> {}, () -> 0.0)));
    }
}

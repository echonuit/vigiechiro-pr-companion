package fr.univ_amu.iut.commun.api;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/// Issue d'un appel HTTP à l'API VigieChiro (#1284) : le transport ne parle plus par silence.
///
/// Historiquement, toute lecture « dégradait proprement » : pas de jeton, réseau coupé, délai dépassé,
/// `422`, `403`, `500`... tout devenait [Optional#empty()] ou liste vide. L'appelant ne pouvait pas
/// distinguer « je ne suis pas connecté » (silence légitime : l'application vit hors ligne) de « le
/// serveur a refusé » (neuf fois sur dix un bug de notre côté, cf. #1277 : le `422` de
/// `max_results=1000` a rendu l'import des observations mort et muet en production) ni de « le serveur
/// n'a pas répondu » (une latence rendue comme collection vide, cf. le faux négatif du contrat live).
///
/// Ce type rend les issues distinctes et **exhaustives à la compilation** : un `switch` sur une
/// [ReponseApi] qui oublie une branche ne compile pas — la garantie qui manquait à la famille de
/// pannes #1277 (« un cas auquel personne n'a pensé »). Le comportement commun est porté par
/// **override** dans chaque variante, jamais par `switch` sur soi-même.
public sealed interface ReponseApi<T> {

    /// Le serveur a répondu (2xx) et la réponse est exploitable.
    record Succes<T>(T valeur) implements ReponseApi<T> {
        public Succes {
            Objects.requireNonNull(valeur, "valeur");
        }

        @Override
        public Optional<T> enOptionnel() {
            return Optional.of(valeur);
        }

        @Override
        public <U> ReponseApi<U> transformer(Function<T, U> transformation) {
            return new Succes<>(transformation.apply(valeur));
        }
    }

    /// Aucun jeton : l'appel n'a **pas eu lieu**. C'est le silence légitime du mode hors connexion,
    /// le seul cas où se taire est le comportement voulu.
    record NonConnecte<T>() implements ReponseApi<T> {
        @Override
        public Optional<T> enOptionnel() {
            return Optional.empty();
        }

        @Override
        public <U> ReponseApi<U> transformer(Function<T, U> transformation) {
            return new NonConnecte<>();
        }
    }

    /// Le serveur n'a **pas répondu** : réseau coupé, DNS, TLS, délai dépassé, appel interrompu — ou
    /// réponse au corps illisible (portail captif, mandataire) : « pas de réponse exploitable » n'est
    /// pas un refus. On ne sait rien de l'état distant ; à ne jamais confondre avec une collection
    /// réellement vide.
    record Injoignable<T>(String cause) implements ReponseApi<T> {
        public Injoignable {
            Objects.requireNonNull(cause, "cause");
        }

        @Override
        public Optional<T> enOptionnel() {
            return Optional.empty();
        }

        @Override
        public <U> ReponseApi<U> transformer(Function<T, U> transformation) {
            return new Injoignable<>(cause);
        }
    }

    /// Le serveur a **répondu non** (statut hors 2xx) : l'information existe, on la garde. Le corps
    /// accompagne le statut pour un message exploitable (`422` de validation, `403` de droits...).
    record Refuse<T>(int statut, String corps) implements ReponseApi<T> {
        public Refuse {
            Objects.requireNonNull(corps, "corps");
        }

        @Override
        public Optional<T> enOptionnel() {
            return Optional.empty();
        }

        @Override
        public <U> ReponseApi<U> transformer(Function<T, U> transformation) {
            return new Refuse<>(statut, corps);
        }
    }

    /// La valeur portée en cas de succès, vide sinon : l'adaptateur vers le comportement historique
    /// (« dégradation propre »), à réserver aux sites où le silence est le comportement **voulu**.
    Optional<T> enOptionnel();

    /// Transporte l'issue vers un autre type de valeur : un succès est transformé, toute autre issue
    /// traverse inchangée (la cause d'un échec ne dépend pas de ce qu'on comptait lire).
    <U> ReponseApi<U> transformer(Function<T, U> transformation);

    static <T> ReponseApi<T> succes(T valeur) {
        return new Succes<>(valeur);
    }

    static <T> ReponseApi<T> nonConnecte() {
        return new NonConnecte<>();
    }

    static <T> ReponseApi<T> injoignable(String cause) {
        return new Injoignable<>(cause);
    }

    static <T> ReponseApi<T> refuse(int statut, String corps) {
        return new Refuse<>(statut, corps);
    }
}

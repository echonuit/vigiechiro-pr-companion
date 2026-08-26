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
/// [ReponseApi] qui oublie une branche ne compile pas : la garantie qui manquait à la famille de
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

        @Override
        public <U> ReponseApi<U> lireAvec(Function<T, Optional<U>> lecture) {
            return lecture.apply(valeur)
                    .<ReponseApi<U>>map(ReponseApi::succes)
                    .orElseGet(() -> ReponseApi.injoignable(REPONSE_ILLISIBLE));
        }

        @Override
        public <U> ReponseApi<U> puis(Function<T, ReponseApi<U>> suite) {
            return suite.apply(valeur);
        }

        @Override
        public Optional<String> echec() {
            return Optional.empty();
        }

        @Override
        public boolean estReessayable() {
            return false;
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

        @Override
        public <U> ReponseApi<U> lireAvec(Function<T, Optional<U>> lecture) {
            return new NonConnecte<>();
        }

        @Override
        public <U> ReponseApi<U> puis(Function<T, ReponseApi<U>> suite) {
            return new NonConnecte<>();
        }

        @Override
        public Optional<String> echec() {
            return Optional.of("non connecté à Vigie-Chiro (aucun jeton).");
        }

        @Override
        public boolean estReessayable() {
            return false;
        }
    }

    /// Le serveur n'a **pas répondu** : réseau coupé, DNS, TLS, délai dépassé, appel interrompu, ou
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

        @Override
        public <U> ReponseApi<U> lireAvec(Function<T, Optional<U>> lecture) {
            return new Injoignable<>(cause);
        }

        @Override
        public <U> ReponseApi<U> puis(Function<T, ReponseApi<U>> suite) {
            return new Injoignable<>(cause);
        }

        @Override
        public Optional<String> echec() {
            return Optional.of("Vigie-Chiro injoignable : " + cause + ".");
        }

        @Override
        public boolean estReessayable() {
            return true;
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

        @Override
        public <U> ReponseApi<U> lireAvec(Function<T, Optional<U>> lecture) {
            return new Refuse<>(statut, corps);
        }

        @Override
        public <U> ReponseApi<U> puis(Function<T, ReponseApi<U>> suite) {
            return new Refuse<>(statut, corps);
        }

        /// Ce que ce refus demande de faire, quand son statut ne suffit pas a le dire (#4524).
        ///
        /// La plateforme rend deux `422` de sens oppose a l'ecriture, et le geste differe : l'un se
        /// corrige chez nous, l'autre pas du tout. Le statut est le meme ; c'est la FORME du corps
        /// qui porte le sens.
        ///
        /// Ce qui n'est ni l'un ni l'autre rend [MotifDeRefus#AUTRE], et c'est un refus de ranger
        /// plutot qu'un fourre-tout : designer un geste precis sur un refus inedit serait pire que
        /// n'en designer aucun.
        public MotifDeRefus motif() {
            if (statut != 422) {
                return MotifDeRefus.AUTRE;
            }
            if (corps.contains("invalid field")) {
                return MotifDeRefus.CHAMP_INCONNU;
            }
            if (corps.contains("field is read-only")) {
                return MotifDeRefus.CHAMP_FERME;
            }
            return MotifDeRefus.AUTRE;
        }

        @Override
        public Optional<String> echec() {
            return Optional.of("HTTP " + statut + " : " + corps);
        }

        @Override
        public boolean estReessayable() {
            // Seuls 429 (trop de requêtes) et 5xx (panne serveur) peuvent réussir plus tard ; un 4xx
            // (URL signée expirée, corps refusé, jeton mort) ne deviendra jamais valide en réessayant.
            return statut == 429 || (statut >= 500 && statut < 600);
        }
    }

    /// Cause d'un `200` que nos parseurs ne savent pas lire : un portail captif ou un mandataire qui
    /// répond à la place du serveur. « Pas de réponse exploitable » n'est pas un refus.
    String REPONSE_ILLISIBLE = "réponse illisible";

    /// La valeur portée en cas de succès, vide sinon : l'adaptateur vers le comportement historique
    /// (« dégradation propre »), à réserver aux sites où le silence est le comportement **voulu**.
    Optional<T> enOptionnel();

    /// Transporte l'issue vers un autre type de valeur : un succès est transformé, toute autre issue
    /// traverse inchangée (la cause d'un échec ne dépend pas de ce qu'on comptait lire).
    <U> ReponseApi<U> transformer(Function<T, U> transformation);

    /// Lit la valeur d'un succès avec un **lecteur qui peut échouer** (parseur JSON) : un corps que le
    /// lecteur ne comprend pas devient [Injoignable] ([#REPONSE_ILLISIBLE]) ; toute autre issue
    /// traverse inchangée.
    <U> ReponseApi<U> lireAvec(Function<T, Optional<U>> lecture);

    /// Enchaîne un appel qui **dépend** du succès du précédent (chaîne du journal de traitement,
    /// #1132) : la suite n'est tentée que sur un succès, toute autre issue traverse inchangée.
    <U> ReponseApi<U> puis(Function<T, ReponseApi<U>> suite);

    /// La cause d'échec **en clair** (vocabulaire unique des messages : « non connecté... »,
    /// « VigieChiro injoignable : ... », « HTTP n : corps »), ou vide si succès.
    Optional<String> echec();

    /// Si rejouer **le même appel** peut raisonnablement réussir : un incident réseau ([Injoignable]) ou
    /// un serveur temporairement indisponible ([Refuse] `429`/`5xx`), oui ; un refus définitif (`4xx` :
    /// URL signée expirée, corps refusé, jeton mort), l'absence de jeton ([NonConnecte]) ou un succès,
    /// non. C'est le type scellé qui le dit, variante par variante (ADR politique de réessai), plutôt
    /// qu'un `switch` disséminé chez les appelants qui oublierait un cas.
    boolean estReessayable();

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

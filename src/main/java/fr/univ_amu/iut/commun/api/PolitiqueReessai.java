package fr.univ_amu.iut.commun.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/// Politique de réessai des appels réseau (#2354, chantier #2350).
///
/// Le transport persiste déjà un plan de dépôt reprenable (`depot_plan`/`depot_unite`, « Retenter les
/// échecs ») : c'est le **filet** qui répare une panne. Cette politique est le **pare-chocs** qui
/// l'évite - un paquet perdu sur une connexion mobile ne doit pas coûter une unité de dépôt et un geste
/// humain pour un incident qui n'en méritait aucun.
///
/// Trois règles la gouvernent (ADR politique de réessai) :
///
/// 1. **Le réessai n'est jamais aveugle.** Seul ce que [ReponseApi#estReessayable()] déclare rejouable
///    est rejoué : un incident réseau ([ReponseApi.Injoignable]) ou un serveur temporairement
///    indisponible ([ReponseApi.Refuse] `429`/`5xx`). Un `4xx` (URL signée expirée, corps refusé, jeton
///    mort) ne deviendra jamais valide en réessayant : on renonce tout de suite.
/// 2. **Le réessai n'est pas uniforme.** Un appel que quelqu'un attend ([Profil#PREMIER_PLAN]) mérite
///    d'insister ; un relevé d'état de fond ([Profil#ARRIERE_PLAN]) renonce vite, sinon il transforme
///    une lenteur serveur en rafale de requêtes et amplifie l'incident.
/// 3. **La temporisation porte un aléa** (jitter) : sans lui, plusieurs unités qui échouent ensemble
///    retentent ensemble et la rafale se reforme. Et **`Retry-After` fait autorité** quand le serveur
///    l'envoie : c'est lui qui sait, pas nous.
///
/// L'endormissement et l'aléa sont **injectés** ([Temporisateur], [DoubleSupplier]) pour que les tests
/// soient déterministes et instantanés : la production dort, le test enregistre.
final class PolitiqueReessai {

    /// Combien insister, et jusqu'où espacer. Le choix n'est pas cosmétique : un relevé d'état qui
    /// insiste aggrave l'incident qu'il essaie d'absorber.
    enum Profil {
        /// Quelqu'un attend devant l'écran (téléversement, écriture demandée) : plusieurs tentatives.
        PREMIER_PLAN(4, Duration.ofMillis(500), Duration.ofSeconds(8)),
        /// Relevé d'état ou tâche périodique : une seule tentative supplémentaire au plus.
        ARRIERE_PLAN(2, Duration.ofMillis(500), Duration.ofSeconds(1));

        /// Nombre total de tentatives, la première incluse.
        private final int maxTentatives;

        private final Duration delaiBase;
        private final Duration delaiMax;

        Profil(int maxTentatives, Duration delaiBase, Duration delaiMax) {
            this.maxTentatives = maxTentatives;
            this.delaiBase = delaiBase;
            this.delaiMax = delaiMax;
        }
    }

    /// Ce qu'une tentative rend au moteur : l'issue typée, et le délai que le serveur a **éventuellement**
    /// imposé (`Retry-After`), qui fait alors autorité sur le backoff calculé.
    record Issue<T>(ReponseApi<T> reponse, Optional<Duration> retryAfter) {
        Issue {
            Objects.requireNonNull(reponse, "reponse");
            Objects.requireNonNull(retryAfter, "retryAfter");
        }

        /// Issue sans consigne du serveur : le backoff décide seul du délai.
        static <T> Issue<T> de(ReponseApi<T> reponse) {
            return new Issue<>(reponse, Optional.empty());
        }
    }

    /// Endormissement d'une durée, injecté pour des tests déterministes. La production dort vraiment,
    /// le test enregistre les durées sans jamais bloquer.
    @FunctionalInterface
    interface Temporisateur {
        void attendre(Duration delai) throws InterruptedException;

        static Temporisateur systeme() {
            return delai -> Thread.sleep(delai.toMillis());
        }
    }

    private final Temporisateur temporisateur;

    /// Source d'aléa dans `[0, 1[`, injectée pour des tests déterministes.
    private final DoubleSupplier alea;

    PolitiqueReessai(Temporisateur temporisateur, DoubleSupplier alea) {
        this.temporisateur = Objects.requireNonNull(temporisateur, "temporisateur");
        this.alea = Objects.requireNonNull(alea, "alea");
    }

    /// Politique de production : dort vraiment, aléa réel.
    static PolitiqueReessai systeme() {
        return new PolitiqueReessai(
                Temporisateur.systeme(), () -> ThreadLocalRandom.current().nextDouble());
    }

    /// Émet `tentative`, et la rejoue tant qu'elle rend une issue réessayable et qu'il reste des
    /// tentatives au profil. Rend la dernière issue obtenue (succès, refus définitif, ou dernier échec
    /// après épuisement). Une interruption pendant l'attente rend l'issue courante sans réessayer.
    <T> ReponseApi<T> executer(Profil profil, SuiviReprise suivi, Supplier<Issue<T>> tentative) {
        Issue<T> issue = tentative.get();
        int tentativesFaites = 1;
        while (tentativesFaites < profil.maxTentatives && issue.reponse().estReessayable()) {
            Duration delai = delaiAvantReprise(profil, tentativesFaites, issue.retryAfter());
            suivi.nouvelleTentative(tentativesFaites + 1, delai);
            try {
                temporisateur.attendre(delai);
            } catch (InterruptedException interrompu) {
                Thread.currentThread().interrupt();
                return issue.reponse();
            }
            issue = tentative.get();
            tentativesFaites++;
        }
        return issue.reponse();
    }

    /// Délai avant la reprise qui suit `tentativesFaites` tentatives. `Retry-After` du serveur fait
    /// autorité s'il est là ; sinon backoff exponentiel plafonné (`base`, `2·base`, `4·base`… jusqu'à
    /// `delaiMax`), avec **jitter égal** - moitié fixe pour que le délai croisse, moitié aléatoire pour
    /// que deux reprises simultanées se désynchronisent.
    Duration delaiAvantReprise(Profil profil, int tentativesFaites, Optional<Duration> retryAfter) {
        if (retryAfter.isPresent()) {
            return retryAfter.get();
        }
        long baseMs = profil.delaiBase.toMillis();
        long plafondMs = profil.delaiMax.toMillis();
        long exponentiel = Math.min(plafondMs, baseMs << (tentativesFaites - 1));
        long moitie = exponentiel / 2;
        long avecAlea = moitie + (long) (alea.getAsDouble() * moitie);
        return Duration.ofMillis(avecAlea);
    }
}

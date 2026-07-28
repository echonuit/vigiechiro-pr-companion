package fr.univ_amu.iut.commun.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
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
/// 2. **Le réessai n'est pas uniforme.** Un appel que quelqu'un attend mérite d'insister
///    ([Profil#INSISTANT]) ; un sondage périodique renonce vite ([Profil#BREF]), sinon il transforme une
///    lenteur serveur en rafale de requêtes et amplifie l'incident. Les profils sont nommés d'après **ce
///    qu'ils font**, la situation qui les motive vivant dans leur documentation : c'est ce qui permet à un
///    appelant de dire « j'insiste » sans avoir à justifier qu'il est bien « au premier plan ».
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
        /// Plusieurs tentatives, temporisation croissante. **À choisir quand quelqu'un attend** une
        /// réponse : téléversement, écriture demandée, et toute lecture de ce produit - aucune n'est un
        /// sondage automatique (#1338, #2619).
        INSISTANT(4, Duration.ofMillis(500), Duration.ofSeconds(8)),
        /// Une seule tentative supplémentaire au plus. **À choisir pour un sondage périodique**, qui
        /// repassera de toute façon : insister y transformerait une lenteur serveur en rafale de requêtes
        /// et amplifierait l'incident qu'on prétend absorber.
        BREF(2, Duration.ofMillis(500), Duration.ofSeconds(1));

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

        /// Attend `delai`, en **abandonnant** dès que `renoncer` le demande (#2686).
        ///
        /// Le défaut se contente d'attendre : c'est le bon comportement pour un double de test, qui
        /// n'attend pas vraiment et n'a donc pas de trou à surveiller. **Comment** on surveille est un
        /// détail de celui qui dort réellement - le mettre dans la politique lui ferait découper une
        /// attente que le double observe d'un seul tenant, et casserait la lecture de ses durées.
        default void attendre(Duration delai, BooleanSupplier renoncer) throws InterruptedException {
            attendre(delai);
        }

        /// Temporisateur de production : dort vraiment, et **par tranches** quand un renoncement est
        /// offert - `Retry-After` peut demander une minute, et « Annuler » n'a pas à y disparaître.
        static Temporisateur systeme() {
            return new Temporisateur() {
                @Override
                public void attendre(Duration delai) throws InterruptedException {
                    Thread.sleep(delai.toMillis());
                }

                @Override
                public void attendre(Duration delai, BooleanSupplier renoncer) throws InterruptedException {
                    Duration restant = delai;
                    while (restant.compareTo(Duration.ZERO) > 0 && !renoncer.getAsBoolean()) {
                        Duration tranche = restant.compareTo(TRANCHE_SURVEILLANCE) > 0 ? TRANCHE_SURVEILLANCE : restant;
                        Thread.sleep(tranche.toMillis());
                        restant = restant.minus(tranche);
                    }
                }
            };
        }
    }

    /// Renoncement des appels qui n'en offrent pas.
    private static final BooleanSupplier JAMAIS = () -> false;

    /// Pas de surveillance du renoncement pendant une attente. Assez court pour qu'un « Annuler »
    /// paraisse immédiat, assez long pour ne pas réveiller le fil sans cesse.
    private static final Duration TRANCHE_SURVEILLANCE = Duration.ofMillis(200);

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
    /// tentatives au profil. Sans renoncement possible : les appels qui n'offrent pas d'annulation.
    <T> ReponseApi<T> executer(Profil profil, SuiviReprise suivi, Supplier<Issue<T>> tentative) {
        return executer(profil, suivi, JAMAIS, tentative);
    }

    /// Même chose, en honorant le **renoncement** de l'appelant. Rend la dernière issue obtenue (succès,
    /// refus définitif, dernier échec après épuisement, ou issue courante si l'on renonce). Une
    /// interruption de fil pendant l'attente rend elle aussi l'issue courante.
    ///
    /// `renoncer` existe parce que **l'annulation de ce produit est un drapeau coopératif**, pas une
    /// interruption de fil ([fr.univ_amu.iut.commun.model.JetonAnnulation] : « jamais d'interruption
    /// brutale »), et qu'elle n'est consultée qu'**entre deux unités de travail**. Une temporisation de
    /// reprise serait donc un trou pendant lequel « Annuler » ne fait rien - exactement le trou que le
    /// relais page par page avait bouché (#1522, #2686). Le drapeau se passe tel quel : `jeton::estAnnule`.
    <T> ReponseApi<T> executer(
            Profil profil, SuiviReprise suivi, BooleanSupplier renoncer, Supplier<Issue<T>> tentative) {
        Issue<T> issue = tentative.get();
        int tentativesFaites = 1;
        while (tentativesFaites < profil.maxTentatives && issue.reponse().estReessayable()) {
            // AVANT l'annonce, et ce n'est pas redondant avec le contrôle de l'attente : sans lui,
            // `nouvelleTentative` promet à l'écran une reprise « dans N s » qui n'aura jamais lieu.
            if (renoncer.getAsBoolean()) {
                return issue.reponse();
            }
            Duration delai = delaiAvantReprise(profil, tentativesFaites, issue.retryAfter());
            suivi.nouvelleTentative(tentativesFaites + 1, delai);
            try {
                temporisateur.attendre(delai, renoncer);
            } catch (InterruptedException interrompu) {
                Thread.currentThread().interrupt();
                return issue.reponse();
            }
            if (renoncer.getAsBoolean()) {
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

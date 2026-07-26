package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.PolitiqueReessai.Issue;
import fr.univ_amu.iut.commun.api.PolitiqueReessai.Profil;
import fr.univ_amu.iut.commun.api.PolitiqueReessai.Suivi;
import fr.univ_amu.iut.commun.api.PolitiqueReessai.Temporisateur;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Politique de réessai (#2354) : le pare-chocs qui absorbe une coupure momentanée. Aucune vraie
/// attente ni réseau : le temporisateur enregistre les durées, l'aléa est figé, la suite des issues
/// est scénarisée. On vérifie les trois règles de l'ADR - jamais aveugle, jamais uniforme, jamais sans
/// aléa - et l'autorité de `Retry-After`.
class PolitiqueReessaiTest {

    /// Le temporisateur n'attend pas : il note ce qu'on lui a demandé d'attendre.
    private final List<Duration> attentes = new ArrayList<>();

    private final Temporisateur enregistreur = attentes::add;

    /// Aléa nul : le délai vaut alors la moitié fixe de l'exponentiel, donc parfaitement déterministe.
    private final PolitiqueReessai politique = new PolitiqueReessai(enregistreur, () -> 0.0);

    private final AtomicInteger appels = new AtomicInteger();

    /// Une suite d'issues rendues à chaque tentative ; la dernière se répète si l'on insiste au-delà.
    @SafeVarargs
    private <T> Supplier<Issue<T>> scenario(Issue<T>... issues) {
        return () -> issues[Math.min(appels.getAndIncrement(), issues.length - 1)];
    }

    @Test
    @DisplayName("un refus définitif (4xx) n'est jamais rejoué : une tentative, aucune attente")
    void refus_definitif_ne_reessaie_pas() {
        ReponseApi<String> issue = ReponseApi.refuse(422, "validation");

        ReponseApi<String> resultat =
                politique.executer(Profil.PREMIER_PLAN, Suivi.SILENCIEUX, scenario(Issue.de(issue)));

        assertThat(resultat).isEqualTo(issue);
        assertThat(appels).hasValue(1);
        assertThat(attentes).isEmpty();
    }

    @Test
    @DisplayName("injoignable puis succès : deux tentatives, une attente, le succès l'emporte")
    void injoignable_puis_succes() {
        ReponseApi<String> resultat = politique.executer(
                Profil.PREMIER_PLAN,
                Suivi.SILENCIEUX,
                scenario(Issue.de(ReponseApi.<String>injoignable("coupure")), Issue.de(ReponseApi.succes("ok"))));

        assertThat(resultat).isEqualTo(ReponseApi.succes("ok"));
        assertThat(appels).hasValue(2);
        assertThat(attentes).hasSize(1);
    }

    @Test
    @DisplayName("premier plan, toujours injoignable : 4 tentatives, 3 attentes, backoff croissant")
    void premier_plan_epuise_les_tentatives() {
        ReponseApi<String> resultat = politique.executer(
                Profil.PREMIER_PLAN, Suivi.SILENCIEUX, scenario(Issue.de(ReponseApi.<String>injoignable("coupure"))));

        assertThat(resultat).isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(appels).hasValue(4);
        // Aléa nul : la moitié fixe de 500, 1000, 2000 ms.
        assertThat(attentes).containsExactly(Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofMillis(1000));
    }

    @Test
    @DisplayName("arrière-plan : une seule reprise au plus, pour ne pas aggraver l'incident")
    void arriere_plan_une_seule_reprise() {
        politique.executer(
                Profil.ARRIERE_PLAN, Suivi.SILENCIEUX, scenario(Issue.de(ReponseApi.<String>injoignable("coupure"))));

        assertThat(appels).hasValue(2);
        assertThat(attentes).hasSize(1);
    }

    @Test
    @DisplayName("Retry-After fait autorité : le délai du serveur l'emporte sur le backoff calculé")
    void retry_after_fait_autorite() {
        Issue<String> imposee = new Issue<>(ReponseApi.refuse(429, "slow down"), Optional.of(Duration.ofSeconds(7)));

        politique.executer(Profil.PREMIER_PLAN, Suivi.SILENCIEUX, scenario(imposee, Issue.de(ReponseApi.succes("ok"))));

        assertThat(attentes).containsExactly(Duration.ofSeconds(7));
    }

    @Test
    @DisplayName("backoff : exponentiel plafonné, jitter égal (moitié fixe + moitié aléatoire)")
    void backoff_plafonne_et_jitter() {
        // Aléa nul -> plancher = moitié de l'exponentiel plafonné.
        assertThat(politique.delaiAvantReprise(Profil.PREMIER_PLAN, 1, Optional.empty()))
                .isEqualTo(Duration.ofMillis(250));
        // Très au-delà du plafond (8 s) : le délai sature à la moitié du plafond.
        assertThat(politique.delaiAvantReprise(Profil.PREMIER_PLAN, 10, Optional.empty()))
                .isEqualTo(Duration.ofMillis(4000));
        // Aléa quasi maximal : on approche le plafond sans jamais le dépasser.
        PolitiqueReessai avecAlea = new PolitiqueReessai(enregistreur, () -> 0.999);
        assertThat(avecAlea.delaiAvantReprise(Profil.PREMIER_PLAN, 10, Optional.empty()))
                .isBetween(Duration.ofMillis(4000), Duration.ofMillis(8000));
    }

    @Test
    @DisplayName("interrompu pendant l'attente : rend l'issue courante, drapeau d'interruption reposé")
    void interruption_pendant_l_attente() {
        Temporisateur interrupteur = delai -> {
            throw new InterruptedException("arrêt demandé");
        };
        PolitiqueReessai interrompue = new PolitiqueReessai(interrupteur, () -> 0.0);

        ReponseApi<String> resultat = interrompue.executer(
                Profil.PREMIER_PLAN, Suivi.SILENCIEUX, scenario(Issue.de(ReponseApi.<String>injoignable("coupure"))));

        assertThat(resultat).isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(Thread.interrupted())
                .as("le drapeau d'interruption est reposé pour l'appelant")
                .isTrue();
        assertThat(appels)
                .as("aucune nouvelle tentative après une interruption")
                .hasValue(1);
    }

    @Test
    @DisplayName("le suivi est prévenu avant chaque attente : n° de la tentative à venir et son délai")
    void suivi_prevenu_avant_chaque_attente() {
        record Avis(int tentative, Duration delai) {}
        List<Avis> avis = new ArrayList<>();
        Suivi suivi = (tentative, delai) -> avis.add(new Avis(tentative, delai));

        politique.executer(Profil.PREMIER_PLAN, suivi, scenario(Issue.de(ReponseApi.<String>injoignable("coupure"))));

        assertThat(avis)
                .containsExactly(
                        new Avis(2, Duration.ofMillis(250)),
                        new Avis(3, Duration.ofMillis(500)),
                        new Avis(4, Duration.ofMillis(1000)));
    }
}

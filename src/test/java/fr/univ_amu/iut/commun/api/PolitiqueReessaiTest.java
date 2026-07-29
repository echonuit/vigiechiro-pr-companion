package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.PolitiqueReessai.Issue;
import fr.univ_amu.iut.commun.api.PolitiqueReessai.Profil;
import fr.univ_amu.iut.commun.api.PolitiqueReessai.Temporisateur;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
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
                politique.executer(Profil.INSISTANT, SuiviReprise.SILENCIEUX, scenario(Issue.de(issue)));

        assertThat(resultat).isEqualTo(issue);
        assertThat(appels).hasValue(1);
        assertThat(attentes).isEmpty();
    }

    @Test
    @DisplayName("injoignable puis succès : deux tentatives, une attente, le succès l'emporte")
    void injoignable_puis_succes() {
        ReponseApi<String> resultat = politique.executer(
                Profil.INSISTANT,
                SuiviReprise.SILENCIEUX,
                scenario(Issue.de(ReponseApi.<String>injoignable("coupure")), Issue.de(ReponseApi.succes("ok"))));

        assertThat(resultat).isEqualTo(ReponseApi.succes("ok"));
        assertThat(appels).hasValue(2);
        assertThat(attentes).hasSize(1);
    }

    @Test
    @DisplayName("premier plan, toujours injoignable : 4 tentatives, 3 attentes, backoff croissant")
    void premier_plan_epuise_les_tentatives() {
        ReponseApi<String> resultat = politique.executer(
                Profil.INSISTANT,
                SuiviReprise.SILENCIEUX,
                scenario(Issue.de(ReponseApi.<String>injoignable("coupure"))));

        assertThat(resultat).isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(appels).hasValue(4);
        // Aléa nul : la moitié fixe de 500, 1000, 2000 ms.
        assertThat(attentes).containsExactly(Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofMillis(1000));
    }

    @Test
    @DisplayName("arrière-plan : une seule reprise au plus, pour ne pas aggraver l'incident")
    void arriere_plan_une_seule_reprise() {
        politique.executer(
                Profil.BREF, SuiviReprise.SILENCIEUX, scenario(Issue.de(ReponseApi.<String>injoignable("coupure"))));

        assertThat(appels).hasValue(2);
        assertThat(attentes).hasSize(1);
    }

    @Test
    @DisplayName("Retry-After fait autorité : le délai du serveur l'emporte sur le backoff calculé")
    void retry_after_fait_autorite() {
        Issue<String> imposee = new Issue<>(ReponseApi.refuse(429, "slow down"), Optional.of(Duration.ofSeconds(7)));

        politique.executer(
                Profil.INSISTANT, SuiviReprise.SILENCIEUX, scenario(imposee, Issue.de(ReponseApi.succes("ok"))));

        assertThat(attentes).containsExactly(Duration.ofSeconds(7));
    }

    @Test
    @DisplayName("backoff : exponentiel plafonné, jitter égal (moitié fixe + moitié aléatoire)")
    void backoff_plafonne_et_jitter() {
        // Aléa nul -> plancher = moitié de l'exponentiel plafonné.
        assertThat(politique.delaiAvantReprise(Profil.INSISTANT, 1, Optional.empty()))
                .isEqualTo(Duration.ofMillis(250));
        // Très au-delà du plafond (8 s) : le délai sature à la moitié du plafond.
        assertThat(politique.delaiAvantReprise(Profil.INSISTANT, 10, Optional.empty()))
                .isEqualTo(Duration.ofMillis(4000));
        // Aléa quasi maximal : on approche le plafond sans jamais le dépasser.
        //
        // Valeur EXACTE, et non un intervalle : `isBetween(4000, 8000)` avait pour borne basse le délai
        // sans aléa, donc acceptait un aléa nul - précisément ce que ce test prétend écarter. PIT l'a
        // montré en remplaçant la multiplication de l'aléa par une division : le délai retombait à
        // 4000 ms et l'assertion restait verte. Sans aléa, N clients reprennent en cadence.
        PolitiqueReessai avecAlea = new PolitiqueReessai(enregistreur, () -> 0.999);
        assertThat(avecAlea.delaiAvantReprise(Profil.INSISTANT, 10, Optional.empty()))
                .isEqualTo(Duration.ofMillis(7996));
    }

    @Test
    @DisplayName("interrompu pendant l'attente : rend l'issue courante, drapeau d'interruption reposé")
    void interruption_pendant_l_attente() {
        Temporisateur interrupteur = delai -> {
            throw new InterruptedException("arrêt demandé");
        };
        PolitiqueReessai interrompue = new PolitiqueReessai(interrupteur, () -> 0.0);

        ReponseApi<String> resultat = interrompue.executer(
                Profil.INSISTANT,
                SuiviReprise.SILENCIEUX,
                scenario(Issue.de(ReponseApi.<String>injoignable("coupure"))));

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
        SuiviReprise suivi = (tentative, delai) -> avis.add(new Avis(tentative, delai));

        politique.executer(Profil.INSISTANT, suivi, scenario(Issue.de(ReponseApi.<String>injoignable("coupure"))));

        assertThat(avis)
                .containsExactly(
                        new Avis(2, Duration.ofMillis(250)),
                        new Avis(3, Duration.ofMillis(500)),
                        new Avis(4, Duration.ofMillis(1000)));
    }

    @Test
    @DisplayName("#2686 : renoncer arrête la reprise, et n'annonce pas une tentative qui n'aura pas lieu")
    void renoncer_arrete_la_reprise_sans_l_annoncer() {
        List<Integer> annonces = new ArrayList<>();

        ReponseApi<String> issue = politique.executer(
                Profil.INSISTANT,
                (tentative, delai) -> annonces.add(tentative),
                () -> true,
                scenario(Issue.de(ReponseApi.<String>injoignable("coupure"))));

        assertThat(issue).isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(attentes)
                .as("on ne temporise pas pour une reprise à laquelle on renonce")
                .isEmpty();
        assertThat(annonces)
                .as("annoncer « nouvelle tentative dans N s » pour une tentative qui n'aura jamais lieu est"
                        + " un mensonge d'écran : le renoncement se lit AVANT l'annonce, pas après")
                .isEmpty();
        assertThat(appels).as("une seule émission : la première").hasValue(1);
    }

    @Test
    @DisplayName("#2686 : sans renoncement, rien ne change - l'attente reste d'un seul tenant")
    void sans_renoncement_l_attente_reste_entiere() {
        politique.executer(
                Profil.INSISTANT, SuiviReprise.SILENCIEUX, scenario(Issue.de(ReponseApi.<String>injoignable("x"))));

        // Le découpage appartient au temporisateur de PRODUCTION : un double qui n'attend pas vraiment
        // n'a pas de trou à surveiller, et continue d'observer une durée par reprise.
        assertThat(attentes).containsExactly(Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofMillis(1000));
    }

    @Test
    @DisplayName("#2686 : le temporisateur de PRODUCTION découpe son attente pour voir le renoncement")
    void le_temporisateur_de_production_decoupe_son_attente() throws InterruptedException {
        List<Duration> tranches = new ArrayList<>();
        Temporisateur systeme = new Temporisateur() {
            @Override
            public void attendre(Duration delai) {
                tranches.add(delai);
            }
        };

        // Le défaut de l'interface délègue au simple `attendre` : c'est ce que fait un double.
        systeme.attendre(Duration.ofSeconds(1), () -> false);

        assertThat(tranches).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("#2686 : l'attente de production est découpée en tranches, pas dormie d'un bloc")
    void l_attente_de_production_est_decoupee_en_tranches() throws InterruptedException {
        List<Duration> tranches = new ArrayList<>();

        Temporisateur.dormant(tranches::add).attendre(Duration.ofSeconds(1), () -> false);

        // Cinq tranches de 200 ms plutôt qu'un sommeil d'une seconde : c'est ce découpage qui donne
        // au renoncement cinq occasions d'être vu au lieu d'une seule, à la fin.
        assertThat(tranches)
                .as("une attente d'une seconde se dort en tranches de 200 ms")
                .containsExactly(
                        Duration.ofMillis(200),
                        Duration.ofMillis(200),
                        Duration.ofMillis(200),
                        Duration.ofMillis(200),
                        Duration.ofMillis(200));
    }

    @Test
    @DisplayName("#2686 : une attente plus courte qu'une tranche se dort d'un seul coup")
    void une_attente_plus_courte_qu_une_tranche_se_dort_d_un_coup() throws InterruptedException {
        List<Duration> tranches = new ArrayList<>();

        Temporisateur.dormant(tranches::add).attendre(Duration.ofMillis(50), () -> false);

        // Sans quoi la dernière tranche d'une attente non multiple dormirait TROP longtemps.
        assertThat(tranches).containsExactly(Duration.ofMillis(50));

        // Et l'attente NON surveillée dort le délai d'un bloc : c'est la même source de sommeil, mais
        // rien ne la découpe puisque personne n'écoute un renoncement.
        tranches.clear();
        Temporisateur.dormant(tranches::add).attendre(Duration.ofSeconds(3));
        assertThat(tranches).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("#2686 : le renoncement arrête l'attente en cours, sans dormir le reste")
    void le_renoncement_arrete_l_attente_en_cours() throws InterruptedException {
        List<Duration> tranches = new ArrayList<>();
        // Renonce après la deuxième tranche : c'est l'utilisateur qui clique « Annuler » pendant l'attente.
        BooleanSupplier apresDeuxTranches = () -> tranches.size() >= 2;

        Temporisateur.dormant(tranches::add).attendre(Duration.ofSeconds(10), apresDeuxTranches);

        // 400 ms au lieu de 10 s : sans ce contrôle, « Annuler » resterait sans effet jusqu'au bout.
        assertThat(tranches).containsExactly(Duration.ofMillis(200), Duration.ofMillis(200));
    }
}

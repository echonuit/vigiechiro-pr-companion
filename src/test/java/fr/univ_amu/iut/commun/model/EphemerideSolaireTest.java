package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie l'[EphemerideSolaire] contre des **valeurs de référence connues** (heures UTC issues de
/// sources astronomiques publiques) et contre des **invariants astronomiques**, et couvre les cas
/// limites : jour et nuit polaires, déterminisme.
///
/// ## Pourquoi les dates comptent autant que la tolérance (#2892)
///
/// La mesure de mutation donnait 21 survivants sur 72 : **la moitié des survivants du paquet
/// `commun.model` dans cette seule classe**. La cause n'était pas la tolérance mais **le choix des
/// dates**.
///
/// Les trois cas tempérés tombaient sur un **solstice**, c'est-à-dire l'extremum de la courbe du
/// lever, donc son **point stationnaire**. Mesuré à Paris :
///
/// | Autour de | Déplacement du lever |
/// |---|---|
/// | solstice de juin (18 → 24 juin) | **11 s par jour** |
/// | équinoxe de mars (17 → 23 mars) | **127 s par jour** |
///
/// **Onze fois plus sensible à l'équinoxe.** Un mutant qui décale le calcul de plusieurs semaines
/// passait donc inaperçu : les tests choisissaient les seules dates où la fonction ne réagit pas.
///
/// D'où les deux tests d'invariant ci-dessous. Ils n'ont besoin d'**aucune table de référence** : la
/// durée du jour à l'équinoxe et la dérive quotidienne du lever sont des faits astronomiques, pas des
/// valeurs à recopier. Un test qui recalculerait ses attendus avec l'implémentation ne prouverait
/// rien.
class EphemerideSolaireTest {

    private static final double PARIS_LAT = 48.8566;
    private static final double PARIS_LON = 2.3522;

    /// Tolérance alignée sur la précision que la classe documente (« environ une minute »), et non
    /// cinq fois plus lâche qu'elle. Vérifié : les valeurs de référence existantes passent déjà.
    private static void proche(Optional<LocalTime> obtenu, LocalTime attendu) {
        proche(obtenu, attendu, 1);
    }

    /// Variante à tolérance choisie, pour l'ancrage externe : la précision de la formule dépend de la
    /// saison, et une seule tolérance pour tous les cas mentirait sur l'un ou sur l'autre.
    private static void proche(Optional<LocalTime> obtenu, LocalTime attendu, int minutes) {
        assertThat(obtenu).isPresent();
        assertThat(obtenu.orElseThrow()).isCloseTo(attendu, within(minutes, ChronoUnit.MINUTES));
    }

    private static long dureeDuJourMinutes(double latitude, double longitude, LocalDate jour) {
        LocalTime lever = EphemerideSolaire.lever(latitude, longitude, jour).orElseThrow();
        LocalTime coucher = EphemerideSolaire.coucher(latitude, longitude, jour).orElseThrow();
        return ChronoUnit.MINUTES.between(lever, coucher);
    }

    private static long deriveDuLeverSecondesParJour(LocalDate debut, LocalDate fin) {
        LocalTime a = EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, debut).orElseThrow();
        LocalTime b = EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, fin).orElseThrow();
        return Math.abs(ChronoUnit.SECONDS.between(a, b)) / (fin.toEpochDay() - debut.toEpochDay());
    }

    @Test
    @DisplayName("Paris au solstice d'été : lever ≈ 03:48 UTC, coucher ≈ 19:58 UTC (soit 05:48 / 21:58 heure locale)")
    void paris_solstice_ete() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        proche(EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, jour), LocalTime.of(3, 48));
        proche(EphemerideSolaire.coucher(PARIS_LAT, PARIS_LON, jour), LocalTime.of(19, 58));
    }

    @Test
    @DisplayName("Paris au solstice d'hiver : lever ≈ 07:42 UTC, coucher ≈ 15:57 UTC")
    void paris_solstice_hiver() {
        LocalDate jour = LocalDate.of(2026, 12, 21);
        proche(EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, jour), LocalTime.of(7, 42));
        proche(EphemerideSolaire.coucher(PARIS_LAT, PARIS_LON, jour), LocalTime.of(15, 57));
    }

    @Test
    @DisplayName("Londres au solstice d'été : lever ≈ 03:44 UTC, coucher ≈ 20:22 UTC")
    void londres_solstice_ete() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        proche(EphemerideSolaire.lever(51.5074, -0.1278, jour), LocalTime.of(3, 44));
        proche(EphemerideSolaire.coucher(51.5074, -0.1278, jour), LocalTime.of(20, 22));
    }

    @Test
    @DisplayName("Aux deux équinoxes, le jour dure ~12 h à toute latitude (et non « entre 11 et 13 »)")
    void equinoxes_journee_de_douze_heures() {
        // L'invariant : à l'équinoxe le soleil est sur l'équateur céleste, donc le jour géométrique
        // dure exactement 12 h PARTOUT. Ce qu'on observe dure un peu plus, et pour une raison connue :
        // le lever est compté quand le CENTRE du soleil est à -0,833° (rayon du disque + réfraction),
        // ce qui avance le lever et retarde le coucher. L'excédent croît avec la latitude.
        //
        // La borne haute à 12 h 20 est donc physique, pas empirique. L'ancienne version tolérait « entre
        // 11 h et 13 h », soit ±1 heure : elle passait sur à peu près n'importe quel jour de mars.
        for (LocalDate equinoxe : new LocalDate[] {LocalDate.of(2026, 3, 20), LocalDate.of(2026, 9, 22)}) {
            assertThat(dureeDuJourMinutes(0.0, 0.0, equinoxe))
                    .as("équateur, %s", equinoxe)
                    .isBetween(12L * 60, 12L * 60 + 20);
            assertThat(dureeDuJourMinutes(PARIS_LAT, PARIS_LON, equinoxe))
                    .as("Paris, %s", equinoxe)
                    .isBetween(12L * 60, 12L * 60 + 20);
        }
    }

    @Test
    @DisplayName(
            "Le lever se déplace ~11 fois plus vite à l'équinoxe qu'au solstice : la date choisie décide de ce qu'un test peut voir")
    void derive_du_lever_bien_plus_forte_a_l_equinoxe() {
        // C'est LE test que l'ancienne suite n'avait pas, et qui explique ses 21 survivants : un
        // solstice est l'extremum de la courbe du lever, donc son point stationnaire. Y poser une
        // assertion, c'est mesurer une fonction là où elle ne bouge pas.
        //
        // Les deux bornes encadrent des faits astronomiques (dérivée de la déclinaison solaire), pas
        // des valeurs relevées : au solstice le lever bouge de quelques secondes par jour, à l'équinoxe
        // de l'ordre de deux minutes.
        long auSolstice = deriveDuLeverSecondesParJour(LocalDate.of(2026, 6, 18), LocalDate.of(2026, 6, 24));
        long aLEquinoxe = deriveDuLeverSecondesParJour(LocalDate.of(2026, 3, 17), LocalDate.of(2026, 3, 23));

        assertThat(auSolstice)
                .as("au solstice, le lever est quasi stationnaire")
                .isLessThan(30);
        assertThat(aLEquinoxe)
                .as("à l'équinoxe, le lever se déplace de l'ordre de deux minutes par jour")
                .isGreaterThan(100);
        assertThat(aLEquinoxe)
                .as("et il se déplace bien plus vite qu'au solstice")
                .isGreaterThan(5 * auSolstice);
    }

    @Test
    @DisplayName("L'invariant des équinoxes tient aussi avant J2000 et dans un autre siècle")
    void equinoxes_hors_de_l_annee_courante() {
        // Troisième angle mort trouvé en lisant les survivants : la classe n'était testée QUE sur 2026.
        // Or deux morceaux du calcul ne s'exercent que hors de cette fenêtre :
        //
        //   - `mod360` ne reçoit un argument NÉGATIF que pour une date antérieure à J2000 (2000-01-01),
        //     puisque le décompte des jours part de là. Sa correction « + 360 » ne servait donc jamais ;
        //   - `jourJulien` porte l'arithmétique séculaire (`y/100`, `y/400`), et 1900 n'est PAS
        //     bissextile là où 2000 l'est - une année qui distingue les deux règles.
        //
        // L'invariant des 12 h, lui, ne dépend d'aucune année : c'est ce qui permet de tester ces dates
        // sans avoir à se procurer de nouvelles valeurs de référence.
        assertThat(dureeDuJourMinutes(PARIS_LAT, PARIS_LON, LocalDate.of(1985, 3, 20)))
                .as("Paris, équinoxe de mars 1985 (avant J2000 : mod360 reçoit un négatif)")
                .isBetween(12L * 60, 12L * 60 + 20);
        assertThat(dureeDuJourMinutes(0.0, 0.0, LocalDate.of(1899, 9, 23)))
                .as("équateur, équinoxe de septembre 1899 (siècle non bissextile)")
                .isBetween(12L * 60, 12L * 60 + 20);
    }

    @Test
    @DisplayName("Autour de l'équinoxe, le lever avance chaque jour ; autour du solstice, il fait demi-tour")
    void sens_de_variation_du_lever() {
        // Deuxième invariant de date, et il est de SIGNE, donc insensible à toute valeur de référence :
        // entre l'équinoxe de mars et celui de juin, le lever avance (de plus en plus tôt) ; il atteint
        // son minimum au solstice, puis recule. C'est cette inversion qu'aucun test ne regardait.
        LocalTime mars = EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, LocalDate.of(2026, 3, 20))
                .orElseThrow();
        LocalTime avantSolstice = EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, LocalDate.of(2026, 6, 18))
                .orElseThrow();
        LocalTime apresSolstice = EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, LocalDate.of(2026, 6, 24))
                .orElseThrow();

        assertThat(avantSolstice)
                .as("de mars à juin, le lever avance de plus de deux heures")
                .isBefore(mars.minusHours(2));
        assertThat(apresSolstice)
                .as("passé le solstice, le lever recule : c'est un minimum, pas un plateau")
                .isAfter(avantSolstice);
    }

    @Test
    @DisplayName("Le coucher suit le lever (même jour UTC) aux latitudes tempérées")
    void coucher_apres_lever() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        LocalTime lever = EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, jour).orElseThrow();
        LocalTime coucher =
                EphemerideSolaire.coucher(PARIS_LAT, PARIS_LON, jour).orElseThrow();
        assertThat(coucher).isAfter(lever);
    }

    @Test
    @DisplayName("Ancrage sur une référence EXTERNE, aux équinoxes : la date absolue est tenue")
    void ancrage_sur_une_reference_externe() {
        // Rien ici ne vient de notre implémentation : les valeurs sont celles d'api.sunrise-sunset.org,
        // mise en œuvre NOAA indépendante, au même seuil d'horizon (-0,833°). Une valeur reprise de
        // notre sortie ne prouverait que notre accord avec nous-mêmes.
        //
        // La tolérance est de CINQ minutes, et non d'une, parce que la mesure l'impose : l'écart
        // atteint 3 min 38 s sur le lever du 25 mars. La formule du lever simplifiée est précise à la
        // minute près des solstices - là où les autres tests l'éprouvent - et à quelques minutes près
        // des équinoxes. Resserrer ferait échouer le test contre l'algorithme, pas contre un défaut.
        proche(EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, LocalDate.of(2026, 3, 25)), LocalTime.of(5, 41, 1), 5);
        proche(EphemerideSolaire.coucher(PARIS_LAT, PARIS_LON, LocalDate.of(2026, 3, 25)), LocalTime.of(18, 12, 5), 5);
        proche(EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, LocalDate.of(2026, 9, 25)), LocalTime.of(5, 40, 0), 5);
        proche(EphemerideSolaire.coucher(PARIS_LAT, PARIS_LON, LocalDate.of(2026, 9, 25)), LocalTime.of(17, 44, 30), 5);
    }

    @Test
    @DisplayName("Jour polaire (Longyearbyen, solstice d'été) : le soleil ne se couche pas → vide")
    void jour_polaire_pas_de_coucher() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        assertThat(EphemerideSolaire.lever(78.22, 15.65, jour)).isEmpty();
        assertThat(EphemerideSolaire.coucher(78.22, 15.65, jour)).isEmpty();
    }

    @Test
    @DisplayName("Nuit polaire (Longyearbyen, solstice d'hiver) : le soleil ne se lève pas → vide")
    void nuit_polaire_pas_de_lever() {
        LocalDate jour = LocalDate.of(2026, 12, 21);
        assertThat(EphemerideSolaire.lever(78.22, 15.65, jour)).isEmpty();
        assertThat(EphemerideSolaire.coucher(78.22, 15.65, jour)).isEmpty();
    }

    @Test
    @DisplayName("Heure locale (Europe/Paris) au solstice d'été : coucher ≈ 21:58, lever ≈ 05:48")
    void paris_solstice_ete_heure_locale() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        ZoneId paris = ZoneId.of("Europe/Paris");
        proche(EphemerideSolaire.coucherLocal(PARIS_LAT, PARIS_LON, jour, paris), LocalTime.of(21, 58));
        proche(EphemerideSolaire.leverLocal(PARIS_LAT, PARIS_LON, jour, paris), LocalTime.of(5, 48));
    }

    @Test
    @DisplayName("Heure locale : jour polaire → vide (propagé depuis le calcul UTC)")
    void heure_locale_jour_polaire_vide() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        ZoneId oslo = ZoneId.of("Europe/Oslo");
        assertThat(EphemerideSolaire.coucherLocal(78.22, 15.65, jour, oslo)).isEmpty();
        assertThat(EphemerideSolaire.leverLocal(78.22, 15.65, jour, oslo)).isEmpty();
    }

    @Test
    @DisplayName("Déterministe : mêmes entrées → même résultat")
    void deterministe() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        assertThat(EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, jour))
                .isEqualTo(EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, jour));
    }
}

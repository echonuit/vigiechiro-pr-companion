package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'instant que le serveur rend, tel que l'utilisateur le lit (#3678).
///
/// ⚠️ Le fuseau est **fourni** à chaque cas plutôt que pris du système : `fuseau-alternatif` rejoue
/// toute la suite sous `America/Cayenne` (ADR 3450), et figer « 21:00 » y ferait rougir un test qui ne
/// constate aucun défaut. C'est la propriété qui compte - « converti dans le fuseau du lecteur » -,
/// pas une heure en dur.
class EtatTraitementVigieChiroInstantTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Test
    @DisplayName("#3678 : la forme RFC 1123 du serveur devient une date française, à l'heure du lecteur")
    void rfc_1123_devient_lisible() {
        assertThat(EtatTraitementVigieChiro.depuis("Fri, 3 Jul 2026 19:00:00 GMT", PARIS))
                // 19 h UTC en juillet = 21 h à Paris. C'est tout l'enjeu : l'observateur décide s'il
                // attend ou s'il revient demain, et deux heures d'écart changent la réponse.
                .isEqualTo(" (le 03/07/2026 à 21:00)");
    }

    @Test
    @DisplayName("#3678 : la forme ISO avec décalage aussi - la plateforme rend les deux")
    void iso_avec_decalage_devient_lisible() {
        assertThat(EtatTraitementVigieChiro.depuis("2026-07-03T19:00:00+00:00", PARIS))
                .isEqualTo(" (le 03/07/2026 à 21:00)");
    }

    @Test
    @DisplayName("#3678 : le fuseau du lecteur change ce qui s'affiche, et c'est le but")
    void le_fuseau_du_lecteur_compte() {
        assertThat(EtatTraitementVigieChiro.depuis("Fri, 3 Jul 2026 19:00:00 GMT", ZoneId.of("America/Cayenne")))
                .as("le même instant, seize heures locales : sans conversion, le message ment à l'un des deux")
                .isEqualTo(" (le 03/07/2026 à 16:00)");
    }

    @Test
    @DisplayName("#3678 : une forme inconnue reste affichée telle quelle plutôt que perdue")
    void une_forme_inconnue_survit() {
        // Perdre l'information vaudrait moins que l'afficher mal : un lecteur peut interpréter une
        // chaîne étrange, jamais une absence.
        assertThat(EtatTraitementVigieChiro.depuis("bientôt", PARIS)).isEqualTo(" (le bientôt)");
    }

    @Test
    @DisplayName("#3678 : pas de date, pas de parenthèses vides")
    void sans_date_rien_du_tout() {
        // Des parenthèses vides promettent une information et n'en donnent aucune - le défaut que #3571
        // a corrigé sur le verrou, à ne pas réintroduire ici.
        assertThat(EtatTraitementVigieChiro.depuis(null, PARIS)).isEmpty();
    }
}

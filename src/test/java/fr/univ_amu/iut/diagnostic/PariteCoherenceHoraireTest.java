package fr.univ_amu.iut.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.diagnostic.model.AnalyseCoherenceHoraire;
import fr.univ_amu.iut.diagnostic.model.CoherenceHoraire;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'écran de diagnostic et la commande `diagnostiquer` disent la même chose de la même nuit
/// (ADR 0014, parité).
///
/// Le cas confronte les **deux formulations** au même verdict : sans lui, reprendre une surface et
/// oublier l'autre laisserait le produit se contredire, et rien ne le dirait.
class PariteCoherenceHoraireTest {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final double AIX_LAT = 43.529;
    private static final double AIX_LON = 5.447;

    /// Une nuit qui ne couvre pas la fenêtre exigée : c'est le cas qui a un verdict à porter.
    private static CoherenceHoraire nuitTropCourte() {
        return AnalyseCoherenceHoraire.analyser(AIX_LAT, AIX_LON, "2026-06-20", "22:00:00", "05:00:00");
    }

    @Test
    @DisplayName("Les deux surfaces citent les mêmes heures, quelle que soit leur mise en forme")
    void les_deux_surfaces_citent_les_memes_heures() {
        // La parité porte sur ce qui est DIT, pas sur la façon de le dire. L'écran sépare ses bornes
        // par « à » et le terminal par une flèche, parce que `U+2192` n'est pas dans la police
        // embarquée (ADR 0035) : comparer les chaînes entières ferait rougir cette différence-là,
        // qui est voulue.
        CoherenceHoraire coherence = nuitTropCourte();

        String ecran = fr.univ_amu.iut.diagnostic.viewmodel.PlagesHoraires.lisible(coherence);
        String terminal = fr.univ_amu.iut.cli.commande.Diagnostiquer.plagesLisibles(coherence);

        for (java.time.LocalTime heure : java.util.List.of(
                coherence.debutExige(),
                coherence.finExigee(),
                coherence.debutEnregistre(),
                coherence.finEnregistree())) {
            assertThat(ecran).as("l'écran cite %s", heure).contains(HEURE.format(heure));
            assertThat(terminal).as("le terminal cite %s", heure).contains(HEURE.format(heure));
        }
    }

    @Test
    @DisplayName("Sans données, ni l'une ni l'autre n'invente une plage")
    void sans_donnees_aucune_des_deux_n_invente() {
        CoherenceHoraire indisponible = CoherenceHoraire.indisponible();

        assertThat(fr.univ_amu.iut.diagnostic.viewmodel.PlagesHoraires.lisible(indisponible))
                .as("un attendu sans obtenu ne se montre pas")
                .isEmpty();
        assertThat(fr.univ_amu.iut.cli.commande.Diagnostiquer.plagesLisibles(indisponible))
                .isEmpty();
    }

    @Test
    @DisplayName("ADR 0014 : les deux surfaces tranchent la MÊME nuit dans le même sens")
    void les_deux_surfaces_tranchent_dans_le_meme_sens() {
        // La parité au-dessus porte sur les HEURES citées. Elle laissait passer le cas qui compte le
        // plus : deux surfaces qui citent les mêmes heures et en tirent des verdicts opposés. Les
        // deux `switch` sont aujourd'hui d'accord parce qu'ils lisent la même énumération, et rien
        // n'obligeait le second à suivre le premier si on en changeait un.
        for (CoherenceHoraire.Couverture etat : CoherenceHoraire.Couverture.values()) {
            CoherenceHoraire coherence = coherenceDe(etat);

            boolean ecranAlerte = fr.univ_amu.iut.diagnostic.viewmodel.DiagnosticViewModel.libelleEcart(coherence)
                            .severite()
                    == Severite.AVERTISSEMENT;
            boolean terminalAlerte = fr.univ_amu.iut.cli.commande.Diagnostiquer.coherenceLisible(coherence)
                    .contains("n'est pas couverte");

            assertThat(ecranAlerte)
                    .as(
                            "sur une nuit %s, l'écran alerte=%s et le terminal alerte=%s",
                            etat, ecranAlerte, terminalAlerte)
                    .isEqualTo(terminalAlerte);
        }
    }

    /// Une cohérence dans l'état voulu, construite par l'analyse réelle plutôt qu'à la main : un
    /// exemple écrit à la main ne garderait que lui-même.
    private static CoherenceHoraire coherenceDe(CoherenceHoraire.Couverture etat) {
        return switch (etat) {
            case INDISPONIBLE -> CoherenceHoraire.indisponible();
            case INCOMPLETE -> nuitTropCourte();
            // Une nuit qui commence tôt et finit tard couvre la fenêtre exigée quelle que soit la date.
            case COUVERTE -> AnalyseCoherenceHoraire.analyser(AIX_LAT, AIX_LON, "2026-06-20", "18:00:00", "09:00:00");
        };
    }
}

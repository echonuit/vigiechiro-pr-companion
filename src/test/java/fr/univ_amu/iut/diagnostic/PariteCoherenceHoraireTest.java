package fr.univ_amu.iut.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

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
}

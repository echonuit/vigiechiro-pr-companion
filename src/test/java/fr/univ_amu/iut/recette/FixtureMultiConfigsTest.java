package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.ConfigurationAcquisition;
import fr.univ_amu.iut.importation.model.JournalParse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La fixture `sd-multi-configs` porte bien **deux configurations de capteur**, et l'analyseur les
/// distingue (#3898).
///
/// ## Pourquoi ce garde existe
///
/// #3460 a corrigé un défaut **silencieux** : une nuit importée repartait avec les paramètres
/// d'acquisition d'une autre session, ce qui fausse la transformation des séquences. La correction est
/// assertée par la CI, mais elle n'était **rejouable à la main par personne** : aucune carte de recette
/// ne portait deux configurations, si bien qu'une case S2 aurait été injouable.
///
/// Ce test ne vérifie donc pas le comportement du produit - `PassageDeLaNuitTest` s'en charge - mais que
/// la **fixture sert à quelque chose**. Sans lui, quelqu'un pourrait simplifier la spec de bonne foi et
/// rendre la case de recette creuse sans que rien ne le signale.
///
/// C'est le même raisonnement que le cliquet des fixtures : *une dette qu'aucun test ne compte n'est
/// pas une dette, c'est un vœu.*
class FixtureMultiConfigsTest {

    private static final Path SPEC = Path.of("recette", "fixtures", "spec", "sd-multi-configs.yaml");

    private static final LocalDate PREMIERE_NUIT = LocalDate.of(2026, 7, 3);
    private static final LocalDate SECONDE_NUIT = LocalDate.of(2026, 7, 5);

    @TempDir
    Path destination;

    @Test
    @DisplayName("#3898 : la carte générée porte deux configurations, et chaque nuit reçoit la sienne")
    void chaque_nuit_de_la_carte_recoit_sa_configuration() throws IOException {
        Path carte = new GenerateurCartesSD().genererDepuisFichier(SPEC, destination);

        JournalParse journal = new AnalyseurLogPR()
                .analyser(Files.readAllLines(carte.resolve("LogPR1925492.txt"), StandardCharsets.UTF_8));

        // La condition d'utilité de la fixture : sans deux configurations, tout le reste passerait
        // encore, et la case de recette ne vérifierait rien.
        assertThat(journal.configurations())
                .as("la carte doit porter DEUX lignes « Paramètres », sinon la case S2 est creuse")
                .hasSize(2);

        assertThat(journal.configurationPourNuit(PREMIERE_NUIT))
                .get()
                .extracting(ConfigurationAcquisition::frequenceEchantillonnageHz)
                .isEqualTo(384_000);

        assertThat(journal.configurationPourNuit(SECONDE_NUIT))
                .get()
                .extracting(ConfigurationAcquisition::frequenceEchantillonnageHz)
                .isEqualTo(256_000);
    }
}

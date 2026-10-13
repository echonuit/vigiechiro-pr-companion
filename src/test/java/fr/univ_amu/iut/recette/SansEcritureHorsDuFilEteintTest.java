package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Pendant une séance filmée, l'espion ne charge ni son registre ni ByteBuddy (#6142).
///
/// La preuve se fait dans une JVM à part : dans celle du build, une autre classe a déjà pu poser
/// la sonde, et « elle n'est pas posée » n'y voudrait rien dire. La JVM fille liste les classes
/// qu'elle charge, et le cas sans film sert de contrôle : sans lui, une liste où le registre manque
/// pourrait venir d'un témoin qui n'a rien joué.
class SansEcritureHorsDuFilEteintTest {

    private static final String REGISTRE = "fr.univ_amu.iut.recette.EcrituresHorsDuFil";
    private static final String FILM = "recette.film";
    private static final String BYTEBUDDY = "net.bytebuddy.agent.builder.AgentBuilder";

    /// L'encodage dans lequel la JVM fille écrit, et dans lequel sa sortie se relit (#6190).
    ///
    /// Une sortie redirigée s'écrit dans l'encodage natif du système, Cp1252 sous Windows, et le
    /// témoin imprime un refus accentué quand la pose échoue faute d'agent. La fille reçoit donc
    /// son encodage sur sa ligne de commande, au lieu de le tenir de la machine.
    private static final Charset ENCODAGE = StandardCharsets.UTF_8;

    @TempDir
    private Path dossier;

    @Test
    @DisplayName("-Drecette.film, la chaîne des tournages : la JVM ne charge ni le registre ni ByteBuddy")
    void sous_l_enregistreur_de_film_l_espion_ne_charge_rien() throws IOException, InterruptedException {
        String classes = classesChargees("-D" + FILM);

        assertThat(classes).contains(EspionEteintTemoin.class.getName());
        assertThat(classes).doesNotContain(REGISTRE).doesNotContain(BYTEBUDDY);
    }

    @Test
    @DisplayName("recette.reperes posée, la séance filmée à l'écran : la JVM ne charge rien non plus")
    void sous_les_reperes_de_seance_l_espion_ne_charge_rien() throws IOException, InterruptedException {
        String classes = classesChargees("-D" + JournalDesReperes.PROPRIETE + "=target/reperes.tsv");

        assertThat(classes).contains(EspionEteintTemoin.class.getName());
        assertThat(classes).doesNotContain(REGISTRE).doesNotContain(BYTEBUDDY);
    }

    @Test
    @DisplayName("recette.reperes vide, l'état d'un build ordinaire : l'espion charge son registre et ByteBuddy")
    void hors_de_tout_film_l_espion_charge_son_registre() throws IOException, InterruptedException {
        String classes = classesChargees("-D" + JournalDesReperes.PROPRIETE + "=");

        assertThat(classes).contains(REGISTRE).contains(BYTEBUDDY);
    }

    private String classesChargees(String option) throws IOException, InterruptedException {
        Path sortie = dossier.resolve("classes.txt");
        List<String> commande = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                "-Xlog:class+load=info",
                "-Dstdout.encoding=" + ENCODAGE.name(),
                "-Dstderr.encoding=" + ENCODAGE.name(),
                option,
                EspionEteintTemoin.class.getName()));
        Process fille = new ProcessBuilder(commande)
                .redirectErrorStream(true)
                .redirectOutput(sortie.toFile())
                .start();
        assertThat(fille.waitFor(60, TimeUnit.SECONDS))
                .as("la JVM fille doit rendre la main")
                .isTrue();
        return Files.readString(sortie, ENCODAGE);
    }
}

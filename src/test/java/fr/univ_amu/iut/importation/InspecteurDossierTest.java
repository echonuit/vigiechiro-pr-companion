package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.NuitDetectee;
import fr.univ_amu.iut.importation.model.RapportInspection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests de l'inspection en lecture seule d'un dossier de carte SD (P2, étape 1). Vérifie la
/// détection du journal, des originaux, du relevé climatique, de l'état de nommage, et surtout que
/// l'inspection **n'écrit rien** sur la source (R9).
class InspecteurDossierTest {

    private static final String LOG =
            "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01,"
                    + " CPU 600000000, T4.1\n"
                    + "22/04/26 - 16:02:21 PR1925492 Sonde température/hygrométrie présente, lecture toutes"
                    + " les 600s\n"
                    + "22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz, Bd. Freq."
                    + " 8-120kHz\n";

    @TempDir
    Path racine;

    private final InspecteurDossier inspecteur = new InspecteurDossier(new AnalyseurLogPR());
    private Path sd;

    @BeforeEach
    void preparer() throws IOException {
        sd = Files.createDirectories(racine.resolve("sd"));
        Files.writeString(sd.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(sd.resolve("PaRecPR1925492_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        Files.writeString(sd.resolve("PaRecPR1925492_20260422_203922.wav"), "wav1");
        Files.writeString(sd.resolve("PaRecPR1925492_20260422_204326.wav"), "wav2");
    }

    @Test
    @DisplayName("Détecte le journal, les originaux bruts et le relevé climatique")
    void inspecte_un_dossier_brut() {
        RapportInspection rapport = inspecteur.inspecter(sd);

        assertThat(rapport.aUnJournal()).isTrue();
        assertThat(rapport.journal().numeroSerie()).isEqualTo("1925492");
        assertThat(rapport.nombreOriginaux()).isEqualTo(2);
        assertThat(rapport.aUnReleveClimatique()).isTrue();
        assertThat(rapport.etatNommage()).isEqualTo(EtatNommage.BRUT);
    }

    @Test
    @DisplayName("R9 : l'inspection ne modifie pas la source (mêmes fichiers avant et après)")
    void inspection_en_lecture_seule() throws IOException {
        List<String> avant = listerNoms(sd);

        inspecteur.inspecter(sd);

        assertThat(listerNoms(sd)).isEqualTo(avant);
    }

    @Test
    @DisplayName("Des originaux déjà préfixés sont reconnus comme PREFIXE")
    void detecte_etat_prefixe() throws IOException {
        Files.delete(sd.resolve("PaRecPR1925492_20260422_203922.wav"));
        Files.delete(sd.resolve("PaRecPR1925492_20260422_204326.wav"));
        Files.writeString(sd.resolve("Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_203922.wav"), "x");

        RapportInspection rapport = inspecteur.inspecter(sd);

        assertThat(rapport.etatNommage()).isEqualTo(EtatNommage.PREFIXE);
    }

    @Test
    @DisplayName("R20 : un dossier sans relevé climatique est signalé (aUnReleveClimatique == false)")
    void releve_climatique_absent() throws IOException {
        Files.delete(sd.resolve("PaRecPR1925492_THLog.csv"));

        RapportInspection rapport = inspecteur.inspecter(sd);

        assertThat(rapport.aUnReleveClimatique()).isFalse();
        assertThat(rapport.cheminReleveClimatique()).isNull();
    }

    @Test
    @DisplayName("Les originaux rangés dans un sous-dossier bruts/ sont détectés")
    void detecte_les_originaux_dans_bruts() throws IOException {
        Path source = Files.createDirectories(racine.resolve("session"));
        Files.writeString(source.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        Path bruts = Files.createDirectories(source.resolve("bruts"));
        Files.writeString(bruts.resolve("PaRecPR1925492_20260422_203922.wav"), "wav");

        RapportInspection rapport = inspecteur.inspecter(source);

        assertThat(rapport.nombreOriginaux()).isEqualTo(1);
    }

    @Test
    @DisplayName("Détecte 3 nuits d'un enregistreur laissé tourner ; la dernière (carte pleine) est incomplète")
    void partitionne_les_nuits_avec_la_derniere_tronquee() throws IOException {
        String log = String.join(
                "\n",
                "03/07/26 - 16:29:33 PR1997632 Démarrage Passive Recorder numéro de série 1997632, V1.04a, CPU 6, T4",
                "03/07/26 - 16:29:34 PR1997632 Paramètres : Acquisi. 21:00-06:30, Fe384kHz, Bd. Freq. 8-120kHz",
                "03/07/26 - 21:00:02 PR1997632 Wakeup by ALARM... Cpt 1",
                "04/07/26 - 06:31:00 PR1997632 ### Passage en mode Veille",
                "04/07/26 - 21:00:01 PR1997632 Wakeup by ALARM... Cpt 2",
                "05/07/26 - 06:31:00 PR1997632 ### Passage en mode Veille",
                "05/07/26 - 21:00:01 PR1997632 Wakeup by ALARM... Cpt 3",
                "05/07/26 - 23:07:04 PR1997632 1 problème de taille SD place restante 0.044250GO",
                "05/07/26 - 23:07:50 PR1997632 ### Passage en mode Erreur",
                "05/07/26 - 23:07:50 PR1997632 Passage en mode Erreur (Carte SD pleine !)");
        Path multi = Files.createDirectories(racine.resolve("multi"));
        Files.writeString(multi.resolve("LogPR1997632.txt"), log, StandardCharsets.UTF_8);
        for (String h : List.of(
                "20260703_213000", "20260704_060000", // nuit 1 (soir → petit matin)
                "20260704_213000", "20260705_060000", // nuit 2
                "20260705_213000", "20260705_230000")) { // nuit 3 (interrompue avant l'aube)
            Files.writeString(multi.resolve("PaRecPR1997632_" + h + ".wav"), "wav");
        }

        List<NuitDetectee> nuits = inspecteur.inspecter(multi).partitionNuits();

        assertThat(nuits)
                .extracting(NuitDetectee::dateNuit)
                .containsExactly(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 5));
        assertThat(nuits).extracting(NuitDetectee::complete).containsExactly(true, true, false);
        assertThat(nuits.get(2).motifIncompletude()).isEqualTo("carte SD pleine");
        assertThat(nuits).extracting(NuitDetectee::nombreFichiers).containsExactly(2, 2, 2);
    }

    @Test
    @DisplayName("Un chemin qui n'est pas un dossier est refusé")
    void chemin_invalide_refuse() {
        assertThatThrownBy(() -> inspecteur.inspecter(racine.resolve("inexistant")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> listerNoms(Path dossier) throws IOException {
        try (var flux = Files.list(dossier)) {
            return flux.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }
}

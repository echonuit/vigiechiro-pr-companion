package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Export CSV des observations affichées (#149) : format ouvrable par tableur (BOM, séparateur `;`,
/// décimales à la virgule), échappement des champs, statuts et référence lisibles.
class ExportObservationsCsvTest {

    @Test
    @DisplayName("Le CSV commence par un BOM et une ligne d'en-têtes")
    void bom_et_entetes() {
        String csv = ExportObservationsCsv.contenu(List.of());

        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("Carré;Point;Site;Passage;Date;Fichier;Taxon Tadarida;Proba Tadarida");
        assertThat(csv).endsWith("\r\n");
    }

    @Test
    @DisplayName("Une observation est aplatie ; décimales à la virgule, statut et référence lisibles")
    void ligne_formatee() {
        String csv = ExportObservationsCsv.contenu(List.of(ligne(
                "640380",
                "A1",
                "Étang",
                "Pippip",
                0.74,
                "Nyclei",
                "Pipistrelle commune",
                "Chiroptères",
                StatutObservation.CORRIGEE,
                true,
                "Cri social net",
                45,
                0.5,
                3.8)));

        assertThat(csv)
                .contains("640380;A1;Étang;2;2026-06-22;seqA_000.wav;Pippip;0,74;Nyclei;Pipistrelle commune"
                        + ";Chiroptères;Corrigée;oui;45;0,50;3,80;Cri social net");
    }

    @Test
    @DisplayName("Un champ contenant le séparateur est entouré de guillemets")
    void champ_avec_separateur_est_echappe() {
        String csv = ExportObservationsCsv.contenu(List.of(ligne(
                "640380",
                "A1",
                "Étang",
                "Pippip",
                0.5,
                null,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                "clic ; puis silence",
                null,
                null,
                null)));

        assertThat(csv).contains("\"clic ; puis silence\"");
        // Champs nuls → vides ; statut « À revoir » ; référence « non ».
        assertThat(csv).contains(";À revoir;non;");
    }

    @Test
    @DisplayName("ecrire écrit exactement le contenu en UTF-8 dans le fichier choisi")
    void ecrire_dans_fichier(@TempDir Path dossier) throws IOException {
        List<LigneObservationAudio> lignes = List.of(ligne(
                "640380",
                "A1",
                "Étang",
                "Pippip",
                0.9,
                null,
                null,
                null,
                StatutObservation.VALIDEE,
                false,
                null,
                45,
                null,
                null));
        Path cible = dossier.resolve("observations.csv");

        Path ecrit = ExportObservationsCsv.ecrire(lignes, cible);

        assertThat(ecrit).isEqualTo(cible);
        assertThat(Files.readString(cible, StandardCharsets.UTF_8)).isEqualTo(ExportObservationsCsv.contenu(lignes));
    }

    /// Construit une observation en fixant les champs testés ; identifiants, passage, date, fichier,
    /// nom Tadarida et heure de capture reçoivent des valeurs de démonstration constantes.
    private static LigneObservationAudio ligne(
            String carre,
            String point,
            String site,
            String taxonTadarida,
            Double probTadarida,
            String taxonObservateur,
            String nomEspece,
            String groupe,
            StatutObservation statut,
            boolean reference,
            String commentaire,
            Integer frequenceKHz,
            Double debutS,
            Double finS) {
        return new LigneObservationAudio(
                1L,
                1L,
                1L,
                2,
                "2026-06-22",
                carre,
                point,
                site,
                taxonTadarida,
                probTadarida,
                taxonObservateur,
                taxonObservateur == null ? null : 0.91,
                statut,
                reference,
                commentaire,
                frequenceKHz,
                nomEspece,
                "Pipistrelle commune",
                groupe,
                "seqA_000.wav",
                debutS,
                finS,
                null,
                false);
    }
}

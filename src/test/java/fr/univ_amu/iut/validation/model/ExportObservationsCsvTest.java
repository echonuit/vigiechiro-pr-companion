package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Certitude;
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
        assertThat(csv).contains("Statut;Référence;Douteux;Espèce à enjeu;Fréquence médiane (kHz)");
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
                true,
                "Cri social net",
                45,
                0.5,
                3.8)));

        assertThat(csv)
                .contains("640380;A1;Étang;2;2026-06-22;seqA_000.wav;Pippip;0,74;Nyclei;;;;0"
                        + ";Pipistrelle commune;Chiroptères;Corrigée;oui;oui;non;45;0,50;3,80;Cri social net");
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
                false,
                "clic ; puis silence",
                null,
                null,
                null)));

        assertThat(csv).contains("\"clic ; puis silence\"");
        // Champs nuls → vides ; statut « À revoir » ; référence « non » ; douteux « non ».
        assertThat(csv).contains(";À revoir;non;non;");
    }

    @Test
    @DisplayName("#1417 : les TROIS avis sortent dans le CSV — un export qui n'en porterait que deux ferait"
            + " perdre le verdict de l'expert à qui ouvre le fichier dans un tableur")
    void les_trois_avis_sont_exportes() {
        LigneObservationAudio ligne = new LigneObservationAudio(
                1L,
                1L,
                1L,
                2,
                "2026-06-22",
                "640380",
                "A1",
                "Étang",
                "Pipkuh",
                0.74,
                "Pippip",
                0.91,
                StatutObservation.CORRIGEE,
                false,
                null,
                45,
                "Pipistrelle commune",
                "Pipistrelle de Kuhl",
                null,
                "Chiroptères",
                "seqA_000.wav",
                0.5,
                3.8,
                null,
                false,
                Certitude.POSSIBLE,
                "Pipnat",
                Certitude.SUR,
                "Pipistrelle de Nathusius",
                2);

        String csv = ExportObservationsCsv.contenu(List.of(ligne));

        assertThat(csv)
                .as("l'en-tête annonce les trois avis et l'existence d'une discussion")
                .contains("Votre certitude;Avis du validateur;Certitude du validateur;Messages");
        assertThat(csv)
                .as("Tadarida propose, l'observateur corrige, le validateur tranche — et 2 messages en"
                        + " attestent : tout cela doit survivre à l'export")
                .contains("Pipkuh;0,74;Pippip;Possible;Pipnat;Sûr;2;");
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
    @Test
    @DisplayName("#2353 : la colonne « Espèce à enjeu » suit le référentiel de conservation")
    void colonne_espece_a_enjeu() {
        // Le repère de l'écran doit survivre à la sortie du fichier : sans cette colonne, qui ouvre
        // l'export dans un tableur devrait reconstituer l'information depuis une liste externe.
        String csv =
                ExportObservationsCsv.contenu(List.of(ligneSimple("Pippip"), ligneSimple("Barbar")), "Pippip"::equals);

        assertThat(csv).contains("Espèce à enjeu");
        String[] lignes = csv.split("\\R");
        assertThat(lignes[1])
                .as("Pipistrelle commune : prioritaire au plan national")
                .contains(";oui;");
        assertThat(lignes[2]).as("Barbastelle : non prioritaire").doesNotContain(";oui;non;oui;");
    }

    @Test
    @DisplayName("Sans référentiel fourni, la colonne existe et vaut « non » partout")
    void colonne_espece_a_enjeu_sans_referentiel() {
        // L'ancienne signature reste utilisable : elle n'invente pas d'enjeu faute de référentiel.
        String csv = ExportObservationsCsv.contenu(List.of(ligneSimple("Pippip")));

        assertThat(csv).contains("Espèce à enjeu");
        assertThat(csv.split("\\R")[1]).doesNotContain(";oui;non;oui");
    }

    /// Une ligne réduite à ce qui compte ici : le taxon retenu (posé par l'observateur).
    private static LigneObservationAudio ligneSimple(String taxon) {
        return ligne(
                "640380",
                "A1",
                "Site",
                taxon,
                0.9,
                taxon,
                taxon,
                "Chiroptères",
                StatutObservation.VALIDEE,
                false,
                false,
                null,
                45,
                0.0,
                1.0);
    }

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
            boolean douteux,
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
                null,
                groupe,
                "seqA_000.wav",
                debutS,
                finS,
                null,
                douteux,
                null,
                null,
                null,
                null,
                0);
    }
}

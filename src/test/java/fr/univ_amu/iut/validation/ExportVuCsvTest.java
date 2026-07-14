package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.LecteurCsv;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.validation.model.ExportVuCsv;
import fr.univ_amu.iut.validation.model.LigneObservation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests unitaires de [ExportVuCsv] : structure des colonnes du CSV `_Vu`, règle R17 (ligne non
/// touchée conserve ses colonnes Tadarida, ligne validée/corrigée reflète l'observateur) et
/// colonne optionnelle `validation_mode` (R24). Pas de base : on travaille sur des
/// [LigneObservation] en mémoire.
class ExportVuCsvTest {

    private final ExportVuCsv export = new ExportVuCsv();
    private final LecteurCsv lecteur = new LecteurCsv();

    private static LigneObservation nonTouchee() {
        return new LigneObservation(
                "seq_000",
                0.3,
                3.9,
                153,
                "noise",
                0.93,
                null,
                null,
                null,
                ModeValidation.NON_VALIDE,
                null,
                null,
                null,
                null,
                null);
    }

    private static LigneObservation validee() {
        return new LigneObservation(
                "seq_001",
                0.4,
                4.8,
                45,
                "Pippip",
                0.8,
                null,
                "Pippip",
                0.8,
                ModeValidation.MANUEL,
                null,
                null,
                null,
                null,
                null);
    }

    private static LigneObservation corrigee() {
        return new LigneObservation(
                "seq_002",
                1.0,
                2.0,
                30,
                "Nyclei",
                0.5,
                "Pippip",
                "Tadten",
                0.9,
                ModeValidation.MANUEL,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("L'entête reprend les 11 colonnes Tadarida dans l'ordre")
    void entete_11_colonnes() {
        String csv = export.versChaine(List.of(nonTouchee()));

        List<String> entete = lecteur.lire(csv).get(0);
        assertThat(entete)
                .containsExactly(
                        "nom du fichier",
                        "temps_debut",
                        "temps_fin",
                        "frequence_mediane",
                        "tadarida_taxon",
                        "tadarida_probabilite",
                        "tadarida_taxon_autre",
                        "observateur_taxon",
                        "observateur_probabilite",
                        "validateur_taxon",
                        "validateur_probabilite");
    }

    @Test
    @DisplayName("R17 : une ligne non touchée conserve ses colonnes Tadarida et laisse l'observateur vide")
    void r17_ligne_non_touchee_conserve_tadarida() {
        List<List<String>> lignes = lecteur.lire(export.versChaine(List.of(nonTouchee())));
        List<String> ligne = lignes.get(1);

        assertThat(ligne.get(0)).isEqualTo("seq_000");
        assertThat(ligne.get(4)).as("tadarida_taxon verbatim").isEqualTo("noise");
        assertThat(ligne.get(5)).as("tadarida_probabilite verbatim").isEqualTo("0.93");
        assertThat(ligne.get(7)).as("observateur_taxon vide").isEmpty();
        assertThat(ligne.get(8)).as("observateur_probabilite vide").isEmpty();
    }

    @Test
    @DisplayName("Une ligne validée reflète le taxon observateur (= taxon Tadarida, R15)")
    void ligne_validee_reflete_observateur() {
        List<String> ligne = lecteur.lire(export.versChaine(List.of(validee()))).get(1);

        assertThat(ligne.get(4)).isEqualTo("Pippip");
        assertThat(ligne.get(7)).as("observateur_taxon = taxon Tadarida").isEqualTo("Pippip");
        assertThat(ligne.get(8)).isEqualTo("0.8");
    }

    @Test
    @DisplayName("Une ligne corrigée reflète un taxon observateur différent (R16) et garde la 2e propal Tadarida")
    void ligne_corrigee_reflete_observateur_different() {
        List<String> ligne =
                lecteur.lire(export.versChaine(List.of(corrigee()))).get(1);

        assertThat(ligne.get(4)).as("tadarida_taxon inchangé").isEqualTo("Nyclei");
        assertThat(ligne.get(6)).as("tadarida_taxon_autre conservé").isEqualTo("Pippip");
        assertThat(ligne.get(7)).as("observateur_taxon corrigé").isEqualTo("Tadten");
    }

    @Test
    @DisplayName("Les colonnes validateur restent toujours vides")
    void colonnes_validateur_vides() {
        List<String> ligne = lecteur.lire(export.versChaine(List.of(validee()))).get(1);

        assertThat(ligne.get(9)).isEmpty();
        assertThat(ligne.get(10)).isEmpty();
    }

    @Test
    @DisplayName("R24 : la colonne validation_mode optionnelle trace manuel / auto / vide")
    void r24_colonne_validation_mode() {
        LigneObservation auto = new LigneObservation(
                "seq_003",
                0.0,
                5.0,
                60,
                "Pippip",
                0.7,
                null,
                "Pippip",
                0.7,
                ModeValidation.AUTO,
                null,
                null,
                null,
                null,
                null);

        List<List<String>> lignes = lecteur.lire(export.versChaine(List.of(nonTouchee(), validee(), auto), true));

        assertThat(lignes.get(0)).last().isEqualTo("validation_mode");
        assertThat(lignes.get(1)).last().as("NON_VALIDE → vide").isEqualTo("");
        assertThat(lignes.get(2)).last().as("manuel").isEqualTo("manuel");
        assertThat(lignes.get(3)).last().as("auto").isEqualTo("auto");
    }

    @Test
    @DisplayName("Sans inclureMode, aucune colonne validation_mode n'est émise")
    void sans_mode_pas_de_12e_colonne() {
        assertThat(lecteur.lire(export.versChaine(List.of(nonTouchee()))).get(0))
                .hasSize(11);
    }
}

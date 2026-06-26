package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.importation.model.LigneRapport;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests du [RapportImport] (#155) : comptes, résumé et exports texte / CSV (dont échappement RFC 4180).
class RapportImportTest {

    private static RapportImport rapport() {
        return new RapportImport(List.of(
                new LigneRapport("Car-PaRec_001.wav", StatutImportFichier.IMPORTE, "3 séquence(s)"),
                new LigneRapport(
                        "Car-PaRec_002.wav",
                        StatutImportFichier.REJETE,
                        "Fréquence source 2001 Hz non divisible par 10"),
                new LigneRapport("notes.txt", StatutImportFichier.IGNORE, "fichier non pertinent")));
    }

    @Test
    @DisplayName("comptes et résumé par statut")
    void comptes_et_resume() {
        RapportImport rapport = rapport();
        assertThat(rapport.compte(StatutImportFichier.IMPORTE)).isEqualTo(1);
        assertThat(rapport.compte(StatutImportFichier.IGNORE)).isEqualTo(1);
        assertThat(rapport.compte(StatutImportFichier.REJETE)).isEqualTo(1);
        assertThat(rapport.aDesRejets()).isTrue();
        assertThat(rapport.resume()).isEqualTo("1 importés · 1 ignorés · 1 rejetés");
    }

    @Test
    @DisplayName("export texte : en-tête résumé + une ligne par fichier avec son statut")
    void export_texte() {
        String texte = rapport().versTexte();
        assertThat(texte).startsWith("Rapport d'import : 1 importés · 1 ignorés · 1 rejetés");
        assertThat(texte)
                .contains("[IMPORTE] Car-PaRec_001.wav — 3 séquence(s)")
                .contains("[REJETE] Car-PaRec_002.wav — Fréquence source")
                .contains("[IGNORE] notes.txt — fichier non pertinent");
    }

    @Test
    @DisplayName("export CSV : en-tête + une ligne par fichier")
    void export_csv() {
        String csv = rapport().versCsv();
        assertThat(csv).startsWith("fichier;statut;detail\n");
        assertThat(csv).contains("Car-PaRec_001.wav;IMPORTE;3 séquence(s)\n");
        assertThat(csv).contains("notes.txt;IGNORE;fichier non pertinent\n");
    }

    @Test
    @DisplayName("#214/#147 : dimension doublon de nuit (aDoublonDeNuit + avertissements)")
    void doublon_de_nuit() {
        RapportImport neuf = rapport();
        assertThat(neuf.aDoublonDeNuit()).as("nuit neuve : pas de doublon").isFalse();

        RapportImport doublon = new RapportImport(
                List.of(new LigneRapport("a.wav", StatutImportFichier.IMPORTE, "3 séquence(s)")),
                List.of(new PassageExistant(2, 2026, "640380", "Z1")));
        assertThat(doublon.aDoublonDeNuit()).isTrue();
        assertThat(doublon.avertissements())
                .contains("Doublon")
                .contains("n° 2")
                .contains("carré 640380");
    }

    @Test
    @DisplayName("avertissements : fichiers ignorés et rejetés signalés, vide si import nominal")
    void avertissements_ignores_et_rejets() {
        assertThat(rapport().avertissements())
                .as("1 ignoré (non pertinent) + 1 rejeté sont signalés")
                .contains("non pertinent(s) ignoré(s)")
                .contains("rejeté(s)");
        RapportImport nominal =
                new RapportImport(List.of(new LigneRapport("a.wav", StatutImportFichier.IMPORTE, "3 séquence(s)")));
        assertThat(nominal.avertissements())
                .as("import nominal (rien d'anormal) : aucun avertissement")
                .isEmpty();
    }

    @Test
    @DisplayName("export CSV : un détail contenant le séparateur est échappé (RFC 4180)")
    void export_csv_echappe_le_separateur() {
        RapportImport rapport = new RapportImport(
                List.of(new LigneRapport("x.wav", StatutImportFichier.REJETE, "erreur ; avec point-virgule")));

        assertThat(rapport.versCsv()).contains("x.wav;REJETE;\"erreur ; avec point-virgule\"\n");
    }
}

package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.importation.model.LigneRapport;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import fr.univ_amu.iut.importation.viewmodel.CompteRenduImport;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
import fr.univ_amu.iut.passage.model.Passage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Restitution d'un import, séparée en deux natures (ADR 0028 / 0031) : la phrase **bornée** de la barre
/// de statut, et le **compte rendu** extensible (doublon, ignorés, rejets, anomalies du journal).
///
/// Ce qui était éprouvé sur `RapportImport.avertissements()` l'est désormais ici : la mise en forme a
/// quitté le modèle, et surtout la vue, où elle n'était pas testable.
class CompteRenduImportTest {

    private static ResultatImport resultat(RapportImport rapport, List<String> anomalies) {
        return new ResultatImport(null, null, "1925492", 1, 3, anomalies, rapport);
    }

    private static RapportImport nominal() {
        return new RapportImport(List.of(new LigneRapport("a.wav", StatutImportFichier.IMPORTE, "3 séquence(s)")));
    }

    @Test
    @DisplayName("statut : borné par construction, il ne dit que le volume traité")
    void statut_borne() {
        RapportImport charge = new RapportImport(
                List.of(
                        new LigneRapport("a.wav", StatutImportFichier.IMPORTE, "3 séquence(s)"),
                        new LigneRapport("notes.txt", StatutImportFichier.IGNORE, ""),
                        new LigneRapport("b.wav", StatutImportFichier.REJETE, "en-tête illisible")),
                List.of(new PassageExistant(2, 2026, "640380", "Z1")));

        String phrase = CompteRenduImport.statut(EtatImport.TERMINE, resultat(charge, List.of("Tension faible")), null);

        // Le défaut d'origine : cette phrase se terminait par `rapport().avertissements()`, de longueur
        // non bornée, dans la barre de statut. Un doublon y déversait la liste des passages existants.
        assertThat(phrase).isEqualTo("Import terminé : 3 séquence(s) produite(s) à partir de 1 original(aux).");
        // La coche a quitté la phrase (ADR 0035) : un pictogramme d'IHM est une icône, et une barre de
        // statut n'a pas de canal de sévérité. « terminé » se suffit.
        assertThat(phrase).doesNotContain("✓");
        assertThat(phrase).doesNotContain("Doublon", "ignoré", "rejeté", "Tension");
    }

    @Test
    @DisplayName("statut : annulation, et silence tant que l'import n'a pas abouti")
    void statut_selon_etat() {
        assertThat(CompteRenduImport.statut(EtatImport.ANNULE, null, null)).isEqualTo("Opération annulée.");
        assertThat(CompteRenduImport.statut(EtatImport.EN_COURS, resultat(nominal(), List.of()), null))
                .as("une barre de statut n'a pas à commenter l'attente")
                .isEmpty();
        assertThat(CompteRenduImport.statut(EtatImport.TERMINE, null, null)).isEmpty();
    }

    /// La branche **multi-nuits**, que rien n'exerçait (#3991).
    ///
    /// ⚠️ Une mesure de mutation de la clôture des suites a désigné `statutNuits` : ses deux lignes en
    /// `NO_COVERAGE`, y compris après élargissement du ciblage. Les cas ci-dessus couvraient le
    /// mono-nuit et les états ; le chemin d'un import **découpé** ne l'était pas, alors que c'est
    /// précisément celui que #3950 venait de modifier.
    @Test
    @DisplayName("#3991 : multi-nuits, la plage se lit d'une borne à l'autre, en français (#3950)")
    void statut_multi_nuits_dit_sa_plage() {
        ResultatImportMultiNuits trois = new ResultatImportMultiNuits(
                List.of(nuitDe("2026-04-22", 612), nuitDe("2026-04-23", 488), nuitDe("2026-04-24", 401)));

        assertThat(CompteRenduImport.statut(EtatImport.TERMINE, null, trois))
                .isEqualTo("Import terminé : 3 passage(s) créé(s) (nuits du 22/04/2026 au 24/04/2026),"
                        + " 1501 séquence(s) produite(s).");
    }

    @Test
    @DisplayName("#3991 : une SEULE nuit par le chemin multi-nuits dit « nuit du », pas « du X au X »")
    void statut_une_seule_nuit_n_est_pas_une_plage() {
        // La bordure que la mutation désignait : le conditionnel qui compare les deux bornes. Sans lui,
        // l'import d'une nuit passé par ce chemin annoncerait « nuits du 22/04/2026 au 22/04/2026 »,
        // ce qu'aucun utilisateur n'écrirait.
        ResultatImportMultiNuits une = new ResultatImportMultiNuits(List.of(nuitDe("2026-04-22", 612)));

        assertThat(CompteRenduImport.statut(EtatImport.TERMINE, null, une))
                .isEqualTo("Import terminé : 1 passage(s) créé(s) (nuit du 22/04/2026),"
                        + " 612 séquence(s) produite(s).");
    }

    @Test
    @DisplayName("#3991 : le multi-nuits PRIME sur le mono quand l'écran porte les deux")
    void statut_multi_nuits_prime_sur_le_mono() {
        // Sans cette priorité, un import découpé se raconterait comme un import simple - et la barre
        // annoncerait le volume d'UNE nuit pour un import qui en a créé plusieurs.
        ResultatImportMultiNuits deux =
                new ResultatImportMultiNuits(List.of(nuitDe("2026-04-22", 612), nuitDe("2026-04-23", 488)));

        assertThat(CompteRenduImport.statut(EtatImport.TERMINE, resultat(nominal(), List.of()), deux))
                .contains("2 passage(s) créé(s)")
                .doesNotContain("à partir de");
    }

    /// Une nuit importée, réduite à ce que la phrase de statut lit : sa date et son volume de séquences.
    private static ResultatImport nuitDe(String date, int sequences) {
        return new ResultatImport(
                new Passage(
                        1L,
                        1,
                        2026,
                        date,
                        "21:15",
                        "06:40",
                        null,
                        StatutWorkflow.IMPORTE,
                        Verdict.A_VERIFIER,
                        null,
                        null,
                        null,
                        1L,
                        "1925492",
                        null),
                null,
                "1925492",
                1,
                sequences,
                List.of(),
                new RapportImport(List.of()));
    }
}

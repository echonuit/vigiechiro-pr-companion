package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.importation.model.LigneRapport;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import fr.univ_amu.iut.importation.viewmodel.CompteRenduImport;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
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
}

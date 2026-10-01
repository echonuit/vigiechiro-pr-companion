package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.RapportAncrage;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.passage.model.RapportReactivation.AbsenceReactivation;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Le compte rendu textuel dit les perdants de collision pour ce qu'ils sont** (#5720) : c'est celui
/// que rend la commande `reactiver`. Ils avaient leur place parmi les séquences introuvables, en erreur,
/// avec un motif qui envoyait chercher un fichier jamais présent.
class CompteRenduPerdantsDeCollisionTest {

    private static final String PERDANT_1 = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342_001.wav";
    private static final String PERDANT_2 = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_210015_001.wav";
    private static final String INTROUVABLE = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205332_000.wav";

    @Test
    @DisplayName("#5720 : les perdants ont leur constat, en avertissement, avec la cause et le geste")
    void les_perdants_ont_leur_constat() {
        Constat constat = constatDesPerdants(rendu(rapport(1, List.of(introuvable()), List.of(PERDANT_1, PERDANT_2))));

        assertThat(constat.severite()).isEqualTo(Severite.AVERTISSEMENT);
        assertThat(constat.fait())
                .contains("2 séquence(s)")
                .contains("se chevauchent")
                .contains("enregistrements bruts")
                .contains("Kaleidoscope")
                .contains("n'auront pas d'observations");
        assertThat(constat.details()).extracting(Detail::sujet).containsExactly(PERDANT_1, PERDANT_2);
    }

    @Test
    @DisplayName("#5720 : le constat des introuvables ne compte et ne nomme que les autres absences")
    void les_introuvables_ne_comptent_que_les_autres() {
        CompteRendu rendu = rendu(rapport(1, List.of(introuvable()), List.of(PERDANT_1, PERDANT_2)));

        Constat introuvables = rendu.constats().stream()
                .filter(constat -> constat.fait().contains("introuvables"))
                .findFirst()
                .orElseThrow();
        assertThat(introuvables.fait()).startsWith("1 séquence(s) restent introuvables");
        assertThat(introuvables.details()).extracting(Detail::sujet).containsExactly(INTROUVABLE);
    }

    @Test
    @DisplayName("#5720 : quand seuls des perdants manquent, le compte rendu est un avertissement")
    void seuls_des_perdants_donnent_un_avertissement() {
        CompteRendu rendu = rendu(rapport(0, List.of(), List.of(PERDANT_1, PERDANT_2)));

        assertThat(rendu.severite()).isEqualTo(Severite.AVERTISSEMENT);
        assertThat(rendu.titre()).isEqualTo("Réactivation partielle");
        assertThat(rendu.constats())
                .as("aucun constat d'introuvables quand il n'y en a pas")
                .noneMatch(constat -> constat.fait().contains("introuvables"));
    }

    @Test
    @DisplayName("#5720 : sans perdant, le compte rendu ne parle pas de collision")
    void sans_perdant_pas_de_constat() {
        CompteRendu rendu = rendu(rapport(1, List.of(introuvable()), List.of()));

        assertThat(rendu.constats()).noneMatch(constat -> constat.fait().contains("se chevauchent"));
    }

    private static Constat constatDesPerdants(CompteRendu rendu) {
        return rendu.constats().stream()
                .filter(constat -> constat.fait().contains("se chevauchent"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucun constat des perdants de collision : " + rendu.constats()));
    }

    private static AbsenceReactivation introuvable() {
        return new AbsenceReactivation(INTROUVABLE, "aucun fichier de ce nom dans le dossier", 1);
    }

    /// Une réactivation depuis un dossier : 10 séquences attendues, `introuvables` et les perdants donnés
    /// manquent, le reste est revenu.
    private static RapportReactivation rapport(
            int introuvables, List<AbsenceReactivation> absences, List<String> perdants) {
        int manquantes = introuvables + perdants.size();
        return new RapportReactivation(
                10 - manquantes,
                0,
                manquantes,
                0,
                NiveauConfiance.CERTITUDE,
                List.of(),
                new DecompteAudio(10 - manquantes, 10),
                VoieReactivation.TRANSFORMES,
                null,
                RapportAncrage.aucun(),
                absences,
                perdants);
    }

    private static CompteRendu rendu(RapportReactivation rapport) {
        return CompteRenduReactivation.de(rapport);
    }
}

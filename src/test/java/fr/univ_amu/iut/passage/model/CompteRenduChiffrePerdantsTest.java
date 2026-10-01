package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.RapportAncrage;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import fr.univ_amu.iut.passage.model.RapportReactivation.AbsenceReactivation;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **La modale dit les perdants de collision pour ce qu'ils sont** (#5720). C'est le compte rendu
/// chiffré qu'elle affiche, et il les comptait dans le segment « Manquantes » et dans le motif « aucun
/// fichier de ce nom dans le dossier » : une proportion fausse de ce qui reste à chercher.
class CompteRenduChiffrePerdantsTest {

    private static final String PERDANT_1 = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342_001.wav";
    private static final String PERDANT_2 = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_210015_001.wav";
    private static final String INTROUVABLE = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205332_000.wav";
    private static final String AUCUN_FICHIER = "aucun fichier de ce nom dans le dossier";

    @Test
    @DisplayName("#5720 : les perdants ont leur segment, et « Manquantes » ne compte que les autres")
    void les_perdants_ont_leur_segment() {
        List<Segment> segments =
                rendu(rapport(1, List.of(PERDANT_1, PERDANT_2))).ventilation().segments();

        assertThat(segments)
                .extracting(Segment::libelle, Segment::quantite, Segment::teinte)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Réactivées", 7L, Teinte.RETENU),
                        org.assertj.core.groups.Tuple.tuple("Manquantes", 1L, Teinte.ECARTE),
                        org.assertj.core.groups.Tuple.tuple("Perdants de collision", 2L, Teinte.EXPLIQUE));
    }

    @Test
    @DisplayName("#5720 : les perdants forment un motif à part, qui les nomme")
    void les_perdants_forment_un_motif() {
        List<Motif> motifs = rendu(rapport(1, List.of(PERDANT_1, PERDANT_2))).motifs();

        assertThat(motifs)
                .filteredOn(motif -> motif.libelle().contains(AUCUN_FICHIER))
                .singleElement()
                .extracting(Motif::sujets)
                .isEqualTo(List.of(INTROUVABLE));
        assertThat(motifs)
                .filteredOn(motif -> motif.libelle().contains("collision"))
                .singleElement()
                .extracting(Motif::sujets)
                .isEqualTo(List.of(PERDANT_1, PERDANT_2));
    }

    @Test
    @DisplayName("#5720 : la phrase du terminal figure parmi les mentions, en avertissement")
    void la_phrase_figure_parmi_les_mentions() {
        RapportReactivation rapport = rapport(1, List.of(PERDANT_1, PERDANT_2));

        assertThat(rendu(rapport).avertissements())
                .contains(Avertissement.de(CompteRenduReactivation.faitPerdants(rapport)));
    }

    @Test
    @DisplayName("#5720 : seuls des perdants laissent un compte rendu en avertissement")
    void seuls_des_perdants_avertissent() {
        CompteRenduChiffre rendu = rendu(rapport(0, List.of(PERDANT_1, PERDANT_2)));

        assertThat(rendu.severite()).isEqualTo(Severite.AVERTISSEMENT);
        assertThat(rendu.ventilation().segments()).extracting(Segment::libelle).doesNotContain("Manquantes");
    }

    /// 10 séquences attendues : `introuvables` absentes du dossier, les perdants donnés, le reste revenu.
    private static RapportReactivation rapport(int introuvables, List<String> perdants) {
        int manquantes = introuvables + perdants.size();
        List<AbsenceReactivation> absences = introuvables == 0
                ? List.of()
                : List.of(new AbsenceReactivation(INTROUVABLE, AUCUN_FICHIER, introuvables));
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

    private static CompteRenduChiffre rendu(RapportReactivation rapport) {
        return CompteRenduChiffreReactivation.de(rapport, List.of());
    }
}

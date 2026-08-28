package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.CarroyageNational;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Proposer un carré depuis une position collée (#4577), sans réseau.
@DisplayName("Proposer un carré depuis une position collée (#4577)")
class PropositionCarreTest {

    /// Le point d'où [#surDeuxMailles] compte ses distances.
    private static final String POINT_INTERROGE = "45.0, 0.0";

    private final PropositionCarre proposition = new PropositionCarre(CarroyageNational.embarque());

    @Test
    @DisplayName("une position bien à l'intérieur d'un carré le propose, sur six chiffres")
    void position_interieure_propose_son_carre() {
        // Mesurée le 2026-08-27 : 374,9 m du centre de 040110, donc loin de tout bord.
        VerdictProposition verdict = proposition.pour("44.44674980384396, 6.298116860416506");

        assertThat(verdict).isEqualTo(new VerdictProposition.Propose("040110"));
    }

    @Test
    @DisplayName("sur une frontière, les deux carrés se nomment et AUCUN ne se dépose")
    void frontiere_nomme_sans_choisir() {
        // Milieu du côté commun, mesuré le 2026-08-27 : deux centres à 997,7 m chacun. « Le plus
        // proche » n'y désigne rien, et le tri du serveur comme le nôtre y départage un ex aequo.
        VerdictProposition verdict = proposition.pour("44.444990, 6.306335");

        assertThat(verdict).isInstanceOf(VerdictProposition.Frontiere.class);
        assertThat(verdict.numeroAProposer())
                .as("proposer un numéro sur une frontière revient à tirer au sort")
                .isEmpty();
        assertThat(verdict.message()).contains("040110").contains("040111").contains("frontière");
        assertThat(((VerdictProposition.Frontiere) verdict).numeros())
                .as("rangés par numéro : l'ordre ne doit pas suggérer une préférence que la phrase refuse")
                .containsExactly("040110", "040111");
    }

    @Test
    @DisplayName("à un coin, les quatre carrés se nomment")
    void coin_nomme_les_quatre() {
        VerdictProposition verdict = proposition.pour("44.453971, 6.306936");

        assertThat(verdict).isInstanceOf(VerdictProposition.Frontiere.class);
        assertThat(((VerdictProposition.Frontiere) verdict).numeros()).hasSize(4);
    }

    @Test
    @DisplayName("hors métropole : aucun carré, dit comme une réponse et non comme une panne")
    void hors_metropole_le_dit() {
        VerdictProposition verdict = proposition.pour("45.0, -20.0");

        assertThat(verdict).isEqualTo(new VerdictProposition.HorsGrille());
        assertThat(verdict.numeroAProposer()).isEmpty();
        assertThat(verdict.message()).contains("métropolitaine").contains("latitude");
    }

    @Test
    @DisplayName("un texte illisible porte le motif de la lecture, sans le réécrire")
    void texte_illisible_porte_le_motif_de_la_lecture() {
        VerdictProposition verdict = proposition.pour("mon jardin");

        assertThat(verdict.numeroAProposer()).isEmpty();
        assertThat(verdict.message())
                .as("le motif vient de LecturePosition, qui sait déjà quoi dire")
                .isEqualTo(PositionCollee.lire("mon jardin").message());
    }

    @Test
    @DisplayName("une URL de carte porte SON motif, distinct de l'illisible")
    void url_porte_son_propre_motif() {
        VerdictProposition verdict = proposition.pour("https://www.google.com/maps/@43.29,5.36,17z");

        assertThat(verdict.numeroAProposer()).isEmpty();
        assertThat(verdict.message()).contains("lien");
    }

    @Test
    @DisplayName("le verdict qui propose porte le numéro ET le dit")
    void le_verdict_qui_propose_porte_son_numero() {
        VerdictProposition verdict = proposition.pour("44.44674980384396, 6.298116860416506");

        assertThat(verdict.numeroAProposer()).contains("040110");
        assertThat(verdict.message()).contains("040110");
    }

    @Test
    @DisplayName("un second carré NETTEMENT plus loin ne rend pas la position frontalière")
    void second_carre_nettement_plus_loin_ne_gene_pas() {
        // Deux mailles à 100 m et 300 m : 200 m d'écart, bien au-delà du seuil. Sans ce cas, un filtre
        // qui laisserait tout passer rendrait « frontière » partout sans qu'un test ne bronche.
        VerdictProposition verdict = surDeuxMailles(100, 300).pour(POINT_INTERROGE);

        assertThat(verdict).isEqualTo(new VerdictProposition.Propose("010001"));
    }

    @Test
    @DisplayName("un second carré à moins de 50 m d'écart rend la position frontalière")
    void second_carre_indiscernable_rend_frontalier() {
        // 100 m et 140 m : 40 m d'écart, en deçà du seuil. Un point à 20 m d'un bord, donc.
        VerdictProposition verdict = surDeuxMailles(100, 140).pour(POINT_INTERROGE);

        assertThat(verdict).isInstanceOf(VerdictProposition.Frontiere.class);
        assertThat(((VerdictProposition.Frontiere) verdict).numeros()).containsExactly("010001", "010002");
    }

    /// Deux mailles alignées au nord du point interrogé, à `metres1` et `metres2` de lui.
    private static PropositionCarre surDeuxMailles(double metres1, double metres2) {
        return new PropositionCarre(CarroyageNational.depuis("010001;" + (45.0 + metres1 / 111_132.0) + ";0.0\n"
                + "010002;" + (45.0 + metres2 / 111_132.0) + ";0.0"));
    }
}

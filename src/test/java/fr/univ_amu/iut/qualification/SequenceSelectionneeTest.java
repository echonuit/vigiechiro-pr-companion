package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.qualification.model.SequenceSelectionnee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce que le rattachement d'une séquence garantit sur les **deux** verdicts qu'il porte : le nôtre
/// et celui d'un relecteur (#4624).
class SequenceSelectionneeTest {

    @Test
    @DisplayName("Un avis complet dit quoi ET qui")
    void un_avis_complet_dit_quoi_et_qui() {
        SequenceSelectionnee avec =
                new SequenceSelectionnee(1L, 2L, 0, false, VerdictFichier.BON, VerdictFichier.MAUVAIS, "pseudo");

        assertThat(avec.porteUnAvisDeRelecteur()).isTrue();
    }

    @Test
    @DisplayName("Un verdict sans pseudo n'est pas un avis : personne ne le signe")
    void un_verdict_sans_pseudo_n_est_pas_un_avis() {
        SequenceSelectionnee sansSignature =
                new SequenceSelectionnee(1L, 2L, 0, false, VerdictFichier.BON, VerdictFichier.MAUVAIS, null);

        assertThat(sansSignature.porteUnAvisDeRelecteur()).isFalse();
    }

    @Test
    @DisplayName("Un pseudo sans verdict n'est pas un avis : c'est une signature au bas d'une page blanche")
    void un_pseudo_sans_verdict_n_est_pas_un_avis() {
        SequenceSelectionnee sansJugement =
                new SequenceSelectionnee(1L, 2L, 0, false, VerdictFichier.BON, VerdictFichier.NON_JUGE, "pseudo");

        assertThat(sansJugement.porteUnAvisDeRelecteur()).isFalse();
    }

    @Test
    @DisplayName("Sans relecteur du tout, aucun avis, et le verdict local ne s'y recopie pas")
    void sans_relecteur_aucun_avis() {
        SequenceSelectionnee seul = new SequenceSelectionnee(1L, 2L, 0, false, VerdictFichier.BON);

        assertThat(seul.porteUnAvisDeRelecteur()).isFalse();
        assertThat(seul.verdictRelecteur())
                .as("l'absence d'avis se dit, elle ne se comble pas avec le nôtre")
                .isEqualTo(VerdictFichier.NON_JUGE);
        assertThat(seul.pseudoRelecteur()).isNull();
    }

    @Test
    @DisplayName("Poser son propre verdict n'efface pas l'avis d'un autre")
    void avec_verdict_preserve_l_avis_du_relecteur() {
        SequenceSelectionnee relue =
                new SequenceSelectionnee(1L, 2L, 0, false, VerdictFichier.BON, VerdictFichier.MAUVAIS, "pseudo");

        SequenceSelectionnee apres = relue.avecVerdict(VerdictFichier.INEXPLOITABLE);

        assertThat(apres.verdict()).isEqualTo(VerdictFichier.INEXPLOITABLE);
        assertThat(apres.verdictRelecteur())
                .as("l'avis du relecteur survit à un changement du nôtre")
                .isEqualTo(VerdictFichier.MAUVAIS);
        assertThat(apres.pseudoRelecteur()).isEqualTo("pseudo");
    }

    @Test
    @DisplayName("Un verdict de relecteur nul retombe sur NON_JUGE, il n'est jamais null")
    void verdict_relecteur_nul_retombe_sur_non_juge() {
        SequenceSelectionnee brut = new SequenceSelectionnee(1L, 2L, 0, false, VerdictFichier.BON, null, null);

        assertThat(brut.verdictRelecteur()).isEqualTo(VerdictFichier.NON_JUGE);
    }
}

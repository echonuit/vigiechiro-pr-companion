package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.validation.model.CriteresRevue;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les filtres de revue (#1311). Le point qui compte : un critère **absent** ne vaut pas « faux ».
class CriteresRevueTest {

    @Test
    @DisplayName("Aucun critère : tout passe")
    void aucun_critere_laisse_tout_passer() {
        assertThat(CriteresRevue.aucun().retient(ligne(StatutObservation.VALIDEE, "Pippip", true, true, Certitude.SUR)))
                .isTrue();
        assertThat(CriteresRevue.aucun().vide()).isTrue();
    }

    @Test
    @DisplayName("Un drapeau ABSENT ne filtre pas : il ne veut pas dire « seulement les non-douteuses »")
    void drapeau_absent_ne_filtre_pas() {
        // C'est la nuance qui rend l'option `--douteux` correcte : sans elle, on veut LES DEUX, pas
        // « celles qui ne sont pas douteuses ». Un booléen à deux états ne saurait pas l'exprimer.
        CriteresRevue sansAvis = new CriteresRevue(null, null, null, null, null, null);

        assertThat(sansAvis.retient(ligne(StatutObservation.NON_TOUCHEE, "Pippip", true, false, null)))
                .isTrue();
        assertThat(sansAvis.retient(ligne(StatutObservation.NON_TOUCHEE, "Pippip", false, false, null)))
                .isTrue();
    }

    @Test
    @DisplayName("--douteux ne garde que les douteuses ; --reference que les références")
    void drapeaux_poses_filtrent() {
        CriteresRevue douteuses = new CriteresRevue(null, null, Boolean.TRUE, null, null, null);

        assertThat(douteuses.retient(ligne(StatutObservation.NON_TOUCHEE, "Pippip", true, false, null)))
                .isTrue();
        assertThat(douteuses.retient(ligne(StatutObservation.NON_TOUCHEE, "Pippip", false, true, null)))
                .isFalse();

        CriteresRevue references = new CriteresRevue(null, null, null, Boolean.TRUE, null, null);
        assertThat(references.retient(ligne(StatutObservation.NON_TOUCHEE, "Pippip", false, true, null)))
                .isTrue();
        assertThat(references.retient(ligne(StatutObservation.NON_TOUCHEE, "Pippip", true, false, null)))
                .isFalse();
    }

    @Test
    @DisplayName("Le taxon se compare sans tenir compte de la casse (« pippip » trouve « Pippip »)")
    void taxon_insensible_a_la_casse() {
        CriteresRevue criteres = new CriteresRevue(null, "pippip", null, null, null, null);

        assertThat(criteres.retient(ligne(StatutObservation.NON_TOUCHEE, "Pippip", false, false, null)))
                .isTrue();
        assertThat(criteres.retient(ligne(StatutObservation.NON_TOUCHEE, "Nyclei", false, false, null)))
                .isFalse();
    }

    @Test
    @DisplayName("Les critères posés se combinent en ET : il faut les satisfaire tous")
    void criteres_combines_en_et() {
        CriteresRevue criteres =
                new CriteresRevue(StatutObservation.CORRIGEE, "Pippip", null, null, Certitude.PROBABLE, null);

        assertThat(criteres.retient(ligne(StatutObservation.CORRIGEE, "Pippip", false, false, Certitude.PROBABLE)))
                .isTrue();
        assertThat(criteres.retient(ligne(StatutObservation.CORRIGEE, "Pippip", false, false, Certitude.SUR)))
                .as("bon statut, bon taxon, mais la certitude ne correspond pas")
                .isFalse();
        assertThat(criteres.vide()).isFalse();
    }

    @Test
    @DisplayName("--statut ÉCARTE ce qui ne le porte pas (trou trouvé par PIT à la clôture #2353)")
    void statut_pose_ecarte_les_autres() {
        // Un mutant remplaçant `statutRetenu` par `true` survivait : aucun test ne vérifiait qu'un statut
        // posé EXCLUT. Or c'est ce filtre qui décide ce que les gestes de revue vont toucher.
        CriteresRevue validees = new CriteresRevue(StatutObservation.VALIDEE, null, null, null, null, null);

        assertThat(validees.retient(ligne(StatutObservation.VALIDEE, "Pippip", false, false, null)))
                .isTrue();
        assertThat(validees.retient(ligne(StatutObservation.NON_TOUCHEE, "Pippip", false, false, null)))
                .as("une observation à revoir n'a rien à faire dans un lot « validées »")
                .isFalse();
    }

    @Test
    @DisplayName("#2353 : « à enjeu » compte comme un filtre posé, sinon le geste croit viser tout le passage")
    void l_enjeu_est_un_filtre_pose() {
        // vide() commande le garde-fou --confirmer des gestes de revue : un critère qu'elle ignore ferait
        // réclamer une confirmation « passage ENTIER » alors que l'utilisateur a restreint sa cible.
        assertThat(new CriteresRevue(null, null, null, null, null, Boolean.TRUE).vide())
                .isFalse();
        assertThat(CriteresRevue.aucun().vide()).isTrue();
    }

    private static LigneObservationAudio ligne(
            StatutObservation statut, String taxonTadarida, boolean douteux, boolean reference, Certitude certitude) {
        return new LigneObservationAudio(
                1L,
                1L,
                1L,
                1,
                "2026-07-03",
                "130711",
                "Z41",
                "Test",
                taxonTadarida,
                0.9,
                null,
                null,
                statut,
                reference,
                null,
                45,
                null,
                null,
                null,
                null,
                "seq.wav",
                0.0,
                5.0,
                LocalDateTime.of(2026, 7, 3, 22, 0),
                douteux,
                certitude,
                null,
                null,
                null,
                0);
    }
}

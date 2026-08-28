package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.CarreCandidat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La règle du verdict, éprouvée sur des distances **choisies** (#4682).
///
/// [ControleCarreStoc] et [ControleCarreLocal] la partagent sans la connaître : l'un interroge la
/// plateforme, l'autre lit le carroyage, et tous deux passent par ici. C'est donc ici que le seuil se
/// tient, et nulle part ailleurs.
///
/// **Pourquoi des distances écrites à la main.** Le seuil vaut 100 m d'écart. Le construire depuis de
/// vraies coordonnées obligerait à viser cette valeur au mètre près en degrés, et le test parlerait
/// alors de trigonométrie plutôt que de la règle. Ici, l'écart **est** la donnée d'entrée.
class ConfrontationCarreTest {

    @Test
    @DisplayName("le carré déclaré est le plus proche : concorde")
    void le_plus_proche_concorde() {
        assertThat(confronter("040110", 500, 2500)).isInstanceOf(VerdictCarre.Concorde.class);
    }

    @Test
    @DisplayName("un écart de 60 m ne départage pas : le SECOND candidat concorde aussi")
    void sous_le_seuil_le_second_concorde() {
        // 60 m d'écart désignent un point à ~30 m d'un bord : personne ne vise à 30 m près en cliquant
        // sur une carte, et l'observateur, lui, sait où était son micro.
        assertThat(confronter("040111", 970, 1030)).isInstanceOf(VerdictCarre.Concorde.class);
    }

    @Test
    @DisplayName("un écart de 140 m départage : le second candidat DIVERGE")
    void au_dessus_du_seuil_le_second_diverge() {
        // Les deux cas se tiennent par paire et encadrent le seuil à 40 m près de chaque côté. Ce qui
        // se teste est sa VALEUR : PIT laisse survivre la mutation de la borne elle-même (`<` en `<=`),
        // et c'est un mutant ÉQUIVALENT - un écart de 100,0 m exact n'est pas atteignable sur des
        // distances calculées depuis des degrés. Le même constat vaut pour `PropositionCarre`.
        assertThat(confronter("040111", 930, 1070)).isEqualTo(new VerdictCarre.Diverge("040110", "040111"));
    }

    @Test
    @DisplayName("aucun candidat : hors grille, et ce n'est pas une erreur")
    void aucun_candidat_est_hors_grille() {
        assertThat(ConfrontationCarre.confronter("040110", List.of())).isInstanceOf(VerdictCarre.HorsGrille.class);
    }

    @Test
    @DisplayName("un carré qu'aucun candidat ne porte diverge, même dans la bande indiscernable")
    void un_carre_absent_diverge() {
        // La bande n'est pas un laissez-passer : elle élargit la liste des bonnes réponses, elle ne
        // supprime pas la question.
        assertThat(confronter("999999", 970, 1030)).isEqualTo(new VerdictCarre.Diverge("040110", "999999"));
    }

    /// Deux candidats, `040110` puis `040111`, aux distances données en mètres.
    private static VerdictCarre confronter(String carreDeclare, double premiere, double seconde) {
        return ConfrontationCarre.confronter(
                carreDeclare, List.of(new CarreCandidat("040110", premiere), new CarreCandidat("040111", seconde)));
    }
}

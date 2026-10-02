package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Un seul classement du résultat de recherche d'un carré** (#5607). Trois gestes le faisaient chacun
/// à sa façon, et seul le rapatriement distinguait le Point Fixe : la vérification annonçait « existe
/// déjà, récupérez-le » pour un carré Routier, qu'aucune récupération ne pouvait rattacher.
class PresenceDuCarreTest {

    private static final SiteVigieChiro POINT_FIXE = new SiteVigieChiro("pf", "Vigiechiro - Point Fixe-202013", false);
    private static final SiteVigieChiro ROUTIER = new SiteVigieChiro("rt", "Vigiechiro - Routier-202013", false);

    @Test
    @DisplayName("#5607 : aucun site, le carré est absent")
    void aucun_site_absent() {
        assertThat(PresenceDuCarre.de(List.of())).isInstanceOf(PresenceDuCarre.Absent.class);
    }

    @Test
    @DisplayName("#5607 : un site Routier seul, le carré est sous un autre protocole, et le classement le nomme")
    void routier_seul_autre_protocole() {
        assertThat(PresenceDuCarre.de(List.of(ROUTIER)))
                .asInstanceOf(InstanceOfAssertFactories.type(PresenceDuCarre.AutreProtocole.class))
                .extracting(PresenceDuCarre.AutreProtocole::titres)
                .isEqualTo(List.of("Vigiechiro - Routier-202013"));
    }

    @Test
    @DisplayName("#5607 : un site Point Fixe parmi d'autres, c'est lui que le classement retient")
    void point_fixe_parmi_d_autres() {
        assertThat(PresenceDuCarre.de(List.of(ROUTIER, POINT_FIXE)))
                .asInstanceOf(InstanceOfAssertFactories.type(PresenceDuCarre.PointFixe.class))
                .extracting(PresenceDuCarre.PointFixe::site)
                .isEqualTo(POINT_FIXE);
    }
}

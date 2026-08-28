package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.sites.model.RechercheCarreExistant;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Situer une position depuis la modale de déclaration (#4577) : ce que le geste dépose, et ce qu'il
/// ne déclenche pas.
class SiteEditSituerPositionTest {

    private final ServiceSites service = mock(ServiceSites.class);
    private final LienVigieChiroDao liens = mock(LienVigieChiroDao.class);
    private final RechercheCarreExistant recherche = mock(RechercheCarreExistant.class);

    private SiteEditViewModel neuf() {
        return new SiteEditViewModel(service, liens, "u-1", Optional.of(recherche), Optional.empty());
    }

    @Test
    @DisplayName("situer une position dépose son carré dans le champ des six chiffres")
    void situer_depose_le_carre() {
        SiteEditViewModel vm = neuf();
        vm.position().texte().set("44.44674980384396, 6.298116860416506");

        vm.situerPosition();

        assertThat(vm.numeroCarreProperty().get()).isEqualTo("040110");
    }

    @Test
    @DisplayName("situer n'interroge JAMAIS la plateforme : c'est l'autre bouton qui pose cette question")
    void situer_n_interroge_pas_la_plateforme() {
        SiteEditViewModel vm = neuf();
        vm.position().texte().set("44.44674980384396, 6.298116860416506");

        vm.situerPosition();

        assertThat(mockingDetails(recherche).getInvocations())
                .as("le carré se calcule sur le carroyage embarqué, et « Vérifier sur Vigie-Chiro »"
                        + " garde la seule question qui appelle le réseau")
                .isEmpty();
    }

    @Test
    @DisplayName("le dépôt efface le verdict d'existence, qui ne juge plus le numéro affiché")
    void le_depot_efface_le_verdict_d_existence() {
        SiteEditViewModel vm = neuf();
        vm.numeroCarreProperty().set("640380");
        vm.position().texte().set("44.44674980384396, 6.298116860416506");

        vm.situerPosition();

        assertThat(vm.carre().retourProperty().get()).isEqualTo(RetourOperation.AUCUN);
        assertThat(vm.position().retour().get().texte()).contains("640380");
    }

    @Test
    @DisplayName("sur une frontière, RIEN n'est déposé : le champ garde ce qu'il avait")
    void frontiere_ne_depose_rien() {
        SiteEditViewModel vm = neuf();
        vm.numeroCarreProperty().set("640380");
        vm.position().texte().set("44.444990, 6.306335");

        vm.situerPosition();

        assertThat(vm.numeroCarreProperty().get()).isEqualTo("640380");
        assertThat(vm.position().retour().get().texte()).contains("040110").contains("040111");
    }
}

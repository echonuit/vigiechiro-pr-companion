package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.sites.model.RechercheCarreExistant;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.VerdictCarre;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Le premier point se crée avec son site** (#5687). L'observateur a collé une position pour trouver
/// son carré : une case lui offre d'en faire aussi son premier point d'écoute, au lieu de la ressaisir
/// dans « Ajouter un point d'écoute ».
class SiteEditPremierPointTest {

    private static final String POSITION = "44.44674980384396, 6.298116860416506";
    private static final String CARRE = "040110";
    private static final String AUTRE_CARRE = "130711";
    private static final String FRONTIERE = "44.444990, 6.306335";
    private static final long ID_SITE = 7L;

    private final ServiceSites service = mock(ServiceSites.class);
    private final LienVigieChiroDao liens = mock(LienVigieChiroDao.class);
    private final RechercheCarreExistant recherche = mock(RechercheCarreExistant.class);

    private SiteEditViewModel neuf() {
        when(service.creerSite(anyString(), any(), any(), any(), anyString()))
                .thenReturn(new Site(ID_SITE, CARRE, null, Protocole.STANDARD, null, "2026-01-01", "u-1"));
        return new SiteEditViewModel(service, liens, "u-1", Optional.of(recherche), Optional.empty());
    }

    @Test
    @DisplayName("#5687 : la case n'est offerte que si une position collée se lit")
    void la_case_suit_la_position() {
        SiteEditViewModel vm = neuf();

        assertThat(vm.premierPoint().offert().get()).as("aucune position").isFalse();
        vm.position().texte().set("l'étang de la Tuilière");
        assertThat(vm.premierPoint().offert().get()).as("texte illisible").isFalse();
        vm.position().texte().set(POSITION);
        assertThat(vm.premierPoint().offert().get()).as("position lue").isTrue();
        assertThat(vm.premierPoint().demande().get()).as("décochée par défaut").isFalse();
    }

    @Test
    @DisplayName("#5687 : case cochée, la création du site crée aussi le point Z1 à la position collée")
    void case_cochee_cree_le_point() {
        SiteEditViewModel vm = neuf();
        vm.position().texte().set(POSITION);
        vm.situerPosition();
        vm.premierPoint().demande().set(true);

        assertThat(vm.enregistrer()).isTrue();

        verify(service).ajouterPoint(ID_SITE, "Z1", 44.44674980384396, 6.298116860416506, null);
    }

    @Test
    @DisplayName("#5687 : case décochée, le site est créé sans aucun point")
    void case_decochee_ne_cree_aucun_point() {
        SiteEditViewModel vm = neuf();
        vm.position().texte().set(POSITION);
        vm.situerPosition();

        assertThat(vm.enregistrer()).isTrue();

        verify(service, never()).ajouterPoint(anyLong(), anyString(), anyDouble(), anyDouble(), any());
    }

    @Test
    @DisplayName("#5687 : la position effacée après avoir coché, aucun point n'est créé")
    void position_effacee_apres_avoir_coche() {
        SiteEditViewModel vm = neuf();
        vm.position().texte().set(POSITION);
        vm.situerPosition();
        vm.premierPoint().demande().set(true);
        vm.position().texte().set("");

        assertThat(vm.enregistrer()).isTrue();

        verify(service, never()).ajouterPoint(anyLong(), anyString(), anyDouble(), anyDouble(), any());
    }

    /// Deux enregistrements successifs, non atomiques : le site reste, et le retour le dit (tâche 2.3).
    @Test
    @DisplayName("#5687 : le site créé et le point refusé, la création aboutit et dit que le point reste à ajouter")
    void le_point_refuse_se_dit() {
        SiteEditViewModel vm = neuf();
        when(service.ajouterPoint(eq(ID_SITE), anyString(), anyDouble(), anyDouble(), any()))
                .thenThrow(new RegleMetierException("base verrouillée"));
        vm.position().texte().set(POSITION);
        vm.situerPosition();
        vm.premierPoint().demande().set(true);

        assertThat(vm.enregistrer()).as("le site, lui, est créé").isTrue();

        assertThat(vm.premierPoint().annonce().texte())
                .contains("premier point")
                .contains("Ajouter un point d'écoute")
                .contains("base verrouillée");
        assertThat(vm.premierPoint().annonce().severite()).isEqualTo(Severite.AVERTISSEMENT);
    }

    /// La case crée un point à la position collée, et le site au numéro du champ. Rien ne les confrontait :
    /// qui tape un numéro, colle une position et coche sans cliquer « Situer » créait un point hors de son
    /// carré, sans un mot (#5860, garde-fou proposé au lot 19 et livré sans).
    @Test
    @DisplayName("#5860 : case cochée, la position dans un autre carré que le numéro saisi se dit, sans bloquer")
    void la_position_dans_un_autre_carre_se_dit() {
        SiteEditViewModel vm = neuf();
        vm.numeroCarreProperty().set(AUTRE_CARRE);
        vm.position().texte().set(POSITION);
        vm.premierPoint().demande().set(true);

        RetourOperation dit = vm.premierPoint().avertissement().getValue();
        assertThat(dit.severite()).isEqualTo(Severite.AVERTISSEMENT);
        // La phrase est celle de la modale de point, par le même type : une seule rédaction de la divergence.
        assertThat(dit.texte()).isEqualTo(new VerdictCarre.Diverge(CARRE, AUTRE_CARRE).message());
        assertThat(dit.texte()).contains(CARRE).contains(AUTRE_CARRE);

        assertThat(vm.enregistrer())
                .as("l'avertissement n'empêche pas de créer")
                .isTrue();
        verify(service).ajouterPoint(ID_SITE, "Z1", 44.44674980384396, 6.298116860416506, null);
    }

    @Test
    @DisplayName("#5860 : quand le numéro concorde, rien ne s'affiche")
    void le_numero_qui_concorde_ne_dit_rien() {
        SiteEditViewModel vm = neuf();
        vm.numeroCarreProperty().set(CARRE);
        vm.position().texte().set(POSITION);
        vm.premierPoint().demande().set(true);

        assertThat(vm.premierPoint().avertissement().getValue().present()).isFalse();
    }

    @Test
    @DisplayName("#5860 : case décochée, aucun point ne sera créé, donc rien à dire")
    void case_decochee_rien_a_dire() {
        SiteEditViewModel vm = neuf();
        vm.numeroCarreProperty().set(AUTRE_CARRE);
        vm.position().texte().set(POSITION);

        assertThat(vm.premierPoint().avertissement().getValue().present()).isFalse();

        vm.premierPoint().demande().set(true);
        assertThat(vm.premierPoint().avertissement().getValue().present())
                .as("cocher la case le fait paraître")
                .isTrue();
    }

    /// Sur une frontière, aucun carré ne l'emporte : dire « un autre carré » serait en choisir un.
    @Test
    @DisplayName("#5860 : une position sur une frontière ne dit rien")
    void une_frontiere_ne_dit_rien() {
        SiteEditViewModel vm = neuf();
        vm.numeroCarreProperty().set(AUTRE_CARRE);
        vm.position().texte().set(FRONTIERE);
        vm.premierPoint().demande().set(true);

        assertThat(vm.premierPoint().avertissement().getValue().present()).isFalse();
    }

    @Test
    @DisplayName("#5860 : un numéro incomplet n'est pas encore un carré, rien ne se dit")
    void un_numero_incomplet_ne_dit_rien() {
        SiteEditViewModel vm = neuf();
        vm.numeroCarreProperty().set("1307");
        vm.position().texte().set(POSITION);
        vm.premierPoint().demande().set(true);

        assertThat(vm.premierPoint().avertissement().getValue().present()).isFalse();

        vm.numeroCarreProperty().set(AUTRE_CARRE);
        assertThat(vm.premierPoint().avertissement().getValue().present())
                .as("le numéro complété, la divergence paraît")
                .isTrue();
    }
}

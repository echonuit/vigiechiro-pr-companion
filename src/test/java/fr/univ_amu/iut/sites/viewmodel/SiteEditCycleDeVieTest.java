package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le **cycle de vie** du ViewModel de la modale de site (#3801).
///
/// ## Ce que ces cas remplacent
///
/// `preparerCreation` reposait neuf champs un à un, et **neuf mutants y survivaient** : les supprimer ne
/// faisait rougir personne, parce qu'ils réécrivaient des valeurs déjà en place. Deux lectures étaient
/// possibles - le ViewModel est réutilisable et personne ne l'éprouve, ou il est mono-usage et ce code
/// ne sert à rien - et le choix n'était pas une question de couverture.
///
/// Le dépôt a tranché pour le **mono-usage** : `NavigationSites` recharge le FXML à chaque ouverture,
/// donc le ViewModel est toujours neuf. Ce choix **renverse** un contrat qui existait, éprouvé par deux
/// tests (#1380 sur la portée d'édition, #3458 sur le verdict) : ils sont ici, retournés en refus.
class SiteEditCycleDeVieTest {

    private final ServiceSites service = mock(ServiceSites.class);
    private final LienVigieChiroDao liens = mock(LienVigieChiroDao.class);

    private SiteEditViewModel neuf() {
        return new SiteEditViewModel(service, liens, "u-1", Optional.empty(), Optional.empty());
    }

    @Test
    @DisplayName("#3801 : un ViewModel neuf est DÉJÀ en état de déclaration")
    void un_view_model_neuf_est_deja_en_declaration() {
        SiteEditViewModel viewModel = neuf();

        // C'est ce que garantissait la réinitialisation retirée. Le garantir ici, sur l'état construit,
        // ferme le même trou sans écrire deux fois la même chose - et fait rougir quiconque changerait
        // une valeur par défaut en croyant que `preparerCreation` la rattrape.
        assertThat(viewModel.numeroCarreProperty().get()).isEmpty();
        assertThat(viewModel.nomProperty().get()).isEmpty();
        assertThat(viewModel.commentaireProperty().get()).isEmpty();
        assertThat(viewModel.protocoleProperty().get()).isEqualTo(Protocole.STANDARD);
        assertThat(viewModel.enCreation().get()).isTrue();
        assertThat(viewModel.libelleBoutonProperty().get()).isEqualTo("Créer");
        assertThat(viewModel.retourProperty().get()).isEqualTo(RetourOperation.AUCUN);
        assertThat(viewModel.porteeEditionProperty().get().present()).isFalse();
        assertThat(viewModel.carre().retourProperty().get()).isEqualTo(RetourOperation.AUCUN);
    }

    @Test
    @DisplayName("#3801 : préparer la déclaration pose le titre, seule chose qui diffère de l'état construit")
    void preparer_la_declaration_pose_son_titre() {
        SiteEditViewModel viewModel = neuf();

        viewModel.preparerCreation();

        // Sans ce cas, la seule ligne restante de `preparerCreation` pouvait être supprimée sans que
        // rien ne rougisse : la modale se serait ouverte sans titre.
        assertThat(viewModel.titreProperty().get()).isEqualTo("Nouveau site de suivi");
    }

    @Test
    @DisplayName("#3801 : préparer l'édition pré-remplit depuis le site, titre et protocole compris")
    void preparer_l_edition_pre_remplit_depuis_le_site() {
        SiteEditViewModel viewModel = neuf();

        viewModel.preparerEdition(new Site(7L, "640380", "Étang", Protocole.RECHERCHE, "notes", "2026-01-01", "u-1"));

        assertThat(viewModel.titreProperty().get()).isEqualTo("Modifier le site · Carré 640380");
        assertThat(viewModel.protocoleProperty().get()).isEqualTo(Protocole.RECHERCHE);
        assertThat(viewModel.nomProperty().get()).isEqualTo("Étang");
        assertThat(viewModel.commentaireProperty().get()).isEqualTo("notes");
        assertThat(viewModel.libelleBoutonProperty().get()).isEqualTo("Enregistrer");
        assertThat(viewModel.enCreation().get()).isFalse();
    }

    @Test
    @DisplayName("#3801 : un enregistrement réussi efface le retour d'un échec précédent")
    void un_enregistrement_reussi_efface_le_retour() {
        SiteEditViewModel viewModel = neuf();
        viewModel.preparerCreation();
        viewModel.numeroCarreProperty().set("640380");
        when(service.creerSite(any(), any(), any(), any(), any()))
                .thenThrow(new RegleMetierException("carré déjà déclaré"))
                .thenReturn(null);

        assertThat(viewModel.enregistrer()).isFalse();
        assertThat(viewModel.retourProperty().get()).isNotEqualTo(RetourOperation.AUCUN);

        // Le second essai réussit : le motif du premier ne doit pas survivre à l'écran.
        assertThat(viewModel.enregistrer()).isTrue();
        assertThat(viewModel.retourProperty().get()).isEqualTo(RetourOperation.AUCUN);
    }

    @Test
    @DisplayName("#3801 : préparer deux fois la même instance est REFUSÉ, au lieu de mentir en silence")
    void une_seconde_preparation_est_refusee() {
        SiteEditViewModel viewModel = neuf();
        viewModel.preparerCreation();

        assertThatThrownBy(viewModel::preparerCreation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ne sert qu'une");
    }

    @Test
    @DisplayName("#3801 : passer de l'édition à la déclaration sur la MÊME instance est refusé (renverse #1380)")
    void passer_de_l_edition_a_la_declaration_est_refuse() {
        SiteEditViewModel viewModel = neuf();
        viewModel.preparerEdition(new Site(7L, "640380", "Étang", Protocole.STANDARD, null, "2026-01-01", "u-1"));

        // #1380 exigeait que le message de portée disparaisse dans ce cas. La production ne le produit
        // pas - la modale est rechargée - et laisser ce chemin ouvert, c'est laisser croire qu'il est
        // sûr. Il l'était pour la portée, il ne l'était pour aucun autre champ.
        assertThatThrownBy(viewModel::preparerCreation).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("#3801 : le refus d'enregistrer un carré invalide protège l'appelant qui n'est pas la vue")
    void enregistrer_refuse_un_carre_invalide() {
        SiteEditViewModel viewModel = neuf();
        viewModel.preparerCreation();
        viewModel.numeroCarreProperty().set("64");

        // L'IHM ne peut pas l'atteindre - le bouton est grisé sur exactement ce prédicat (#790) - mais le
        // ViewModel s'appelle aussi directement. Sans ce cas, PIT remplaçait le `return false` par
        // `return true` sans que rien ne rougisse : la vue aurait fermé la modale sur un site non créé.
        assertThat(viewModel.enregistrer()).isFalse();
        verify(service, never()).creerSite(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("#3801 : fermer le bandeau efface le retour, sur CETTE modale")
    void effacer_le_retour_vide_le_bandeau() {
        SiteEditViewModel viewModel = neuf();
        viewModel.preparerCreation();
        viewModel.numeroCarreProperty().set("640380");
        when(service.creerSite(any(), any(), any(), any(), any()))
                .thenThrow(new RegleMetierException("carré déjà déclaré"));
        viewModel.enregistrer();
        // ⚠️ Il faut un retour à effacer. La première version de ce cas partait d'un carré invalide :
        // `enregistrer` refusait AVANT d'écrire quoi que ce soit, si bien que le test comparait AUCUN
        // à AUCUN. PIT l'a pris - la ligne d'effacement se supprimait sans faire rougir personne.
        assertThat(viewModel.retourProperty().get()).isNotEqualTo(RetourOperation.AUCUN);

        viewModel.effacerRetour();

        // Le composant est éprouvé au socle et le geste sur d'autres écrans ; c'est ce câblage-ci qui
        // n'était asserté nulle part.
        assertThat(viewModel.retourProperty().get()).isEqualTo(RetourOperation.AUCUN);
    }
}

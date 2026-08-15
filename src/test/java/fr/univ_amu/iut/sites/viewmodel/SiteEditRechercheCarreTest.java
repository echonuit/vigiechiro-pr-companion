package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.RechercheCarreExistant;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// « Ce carré existe-t-il déjà ? » vu depuis la **modale de déclaration** (#3458).
///
/// [RechercheCarreExistantTest] éprouve les verdicts ; ici on éprouve ce que le ViewModel en fait :
/// quand il interroge la plateforme, quand il s'en abstient, et ce qu'il laisse à l'écran.
///
/// L'enjeu tient en une phrase, celle du retour utilisateur qui a ouvert #3458 : **ne jamais laisser
/// croire qu'un carré a été vérifié quand il ne l'a pas été.** Une réponse absente, périmée ou portant
/// sur un autre numéro produit la même panne - une déclaration en double, et un dépôt qui échoue loin
/// de sa cause.
class SiteEditRechercheCarreTest {

    private static final String CARRE = "640380";

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final ServiceSites service = mock(ServiceSites.class);
    private final LienVigieChiroDao liens = mock(LienVigieChiroDao.class);
    private final RapatriementCarre rapatriement = mock(RapatriementCarre.class);

    /// Modale montée **avec** la vérification et la récupération : le cas de l'application complète.
    ///
    /// Le rapatriement est **mocké** : ce qu'il fait en base est éprouvé par `RapatriementCarreTest`, sur
    /// une vraie base. Ici on n'éprouve que ce que le ViewModel en fait - quand il l'appelle, quand il
    /// offre le geste, et ce qu'il laisse à l'écran.
    private SiteEditViewModel avecRecherche() {
        SiteEditViewModel viewModel = new SiteEditViewModel(
                service, liens, "u-1", Optional.of(new RechercheCarreExistant(client)), Optional.of(rapatriement));
        viewModel.preparerCreation();
        return viewModel;
    }

    /// Modale montée **sans** : injecteur partiel, feature éteinte, capture d'écran.
    private SiteEditViewModel sansRecherche() {
        SiteEditViewModel viewModel = new SiteEditViewModel(service, liens, "u-1", Optional.empty(), Optional.empty());
        viewModel.preparerCreation();
        return viewModel;
    }

    /// Demande le verdict et l'applique, comme le fait le controller : appel bloquant, puis application
    /// sur le fil JavaFX.
    private static void verifier(SiteEditViewModel viewModel) {
        viewModel.appliquerRechercheCarre(viewModel.chercherCarreExistant());
    }

    @Test
    @DisplayName("#3458 : sans vérification installée, le geste n'est pas offert - et n'appelle rien")
    void sans_recherche_installee_le_geste_n_est_pas_offert() {
        SiteEditViewModel viewModel = sansRecherche();
        viewModel.numeroCarreProperty().set(CARRE);

        assertThat(viewModel.rechercheCarreDisponible())
                .as("la modale doit pouvoir masquer le bouton plutôt que d'en afficher un mort")
                .isFalse();
        assertThat(viewModel.chercherCarreExistant().verdict())
                .as("et le verdict reste « on ne sait pas » : surtout pas « il est libre »")
                .isInstanceOf(RechercheCarreExistant.Verdict.Indisponible.class);
        verify(client, never()).chercherCarre(anyString());
    }

    @Test
    @DisplayName("#3458 : un carré incomplet ne fait partir aucune requête")
    void carre_incomplet_n_interroge_pas_la_plateforme() {
        SiteEditViewModel viewModel = avecRecherche();
        viewModel.numeroCarreProperty().set("6403");

        assertThat(viewModel.chercherCarreExistant().verdict())
                .isInstanceOf(RechercheCarreExistant.Verdict.Indisponible.class);
        // `$text` cherche des mots ENTIERS : « 6403 » ne ramènerait pas « 640380 » mais un zéro qui se
        // lirait « ce carré est libre ». Le garde est ici, pas seulement dans le grisage du bouton.
        verify(client, never()).chercherCarre(anyString());
    }

    @Test
    @DisplayName("#3458 : carré libre : la modale le dit, en succès")
    void carre_libre_le_verdict_le_dit() {
        SiteEditViewModel viewModel = avecRecherche();
        viewModel.numeroCarreProperty().set(CARRE);
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.succes(List.of()));

        verifier(viewModel);

        assertThat(viewModel.retourCarreExistantProperty().get().texte()).contains("n'existe pas encore");
        assertThat(viewModel.retourCarreExistantProperty().get().severite()).isEqualTo(Severite.SUCCES);
    }

    @Test
    @DisplayName("#3458 : carré déjà déclaré : le message nomme le site trouvé et avertit")
    void carre_deja_declare_avertit_et_nomme() {
        SiteEditViewModel viewModel = avecRecherche();
        viewModel.numeroCarreProperty().set(CARRE);
        when(client.chercherCarre(CARRE))
                .thenReturn(
                        ReponseApi.succes(List.of(new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-640380", true))));

        verifier(viewModel);

        // Le titre porte le protocole : « il existe » sans dire lequel n'aide pas à décider quoi faire.
        assertThat(viewModel.retourCarreExistantProperty().get().texte())
                .contains("Vigiechiro - Point Fixe-640380")
                // Depuis #3806, le message propose de récupérer le carré ICI, avec son rattachement : le
                // renvoi vers la synchronisation ne ramenait pas un carré sans nuit déposée.
                .contains("rattaché");
        assertThat(viewModel.retourCarreExistantProperty().get().severite()).isEqualTo(Severite.AVERTISSEMENT);
    }

    @Test
    @DisplayName("#3458 : hors connexion, la modale dit qu'elle n'a PAS vérifié")
    void hors_connexion_la_modale_dit_qu_elle_n_a_pas_verifie() {
        SiteEditViewModel viewModel = avecRecherche();
        viewModel.numeroCarreProperty().set(CARRE);
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.nonConnecte());

        verifier(viewModel);

        // L'utilisateur a CLIQUÉ : se taire lui ferait croire au silence de l'absence.
        assertThat(viewModel.retourCarreExistantProperty().get().texte()).contains("PAS été vérifié");
    }

    @Test
    @DisplayName("#3458 : changer le carré PÉRIME le verdict : il portait sur un autre numéro")
    void changer_le_carre_perime_le_verdict() {
        SiteEditViewModel viewModel = avecRecherche();
        viewModel.numeroCarreProperty().set(CARRE);
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.succes(List.of()));
        verifier(viewModel);
        assertThat(viewModel.retourCarreExistantProperty().get().texte()).isNotEmpty();

        viewModel.numeroCarreProperty().set("640381");

        // Sans cela, « ce carré n'existe pas encore » resterait affiché sous un numéro que personne n'a
        // vérifié - le pire des deux mondes, puisque l'utilisateur a la preuve visuelle du contraire.
        assertThat(viewModel.retourCarreExistantProperty().get().texte())
                .as("un verdict ne survit pas au numéro sur lequel il portait")
                .isEmpty();
    }

    @Test
    @DisplayName("#3458 : une réponse arrivée APRÈS une correction ne juge plus ce qui est à l'écran")
    void un_verdict_arrive_en_retard_est_ecarte() {
        SiteEditViewModel viewModel = avecRecherche();
        viewModel.numeroCarreProperty().set(CARRE);
        when(client.chercherCarre(CARRE))
                .thenReturn(
                        ReponseApi.succes(List.of(new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-640380", true))));

        // La requête part (en production elle tourne hors du fil JavaFX, donc l'utilisateur garde la main)…
        var resultat = viewModel.chercherCarreExistant();
        // … il corrige sa saisie pendant ce temps…
        viewModel.numeroCarreProperty().set("640381");
        // … et la réponse arrive enfin.
        viewModel.appliquerRechercheCarre(resultat);

        // C'est le jumeau du verdict périmé, pris par l'autre bout : là c'était la saisie qui bougeait
        // après la réponse, ici c'est la réponse qui arrive après la saisie. Même panne à l'écran - un
        // avertissement affiché sous un carré qu'il ne juge pas.
        assertThat(viewModel.retourCarreExistantProperty().get().texte())
                .as("un verdict ne s'applique qu'au numéro qui l'a demandé")
                .isEmpty();
    }

    @Test
    @DisplayName("#3458 : rouvrir la modale en déclaration repart sans verdict")
    void preparer_creation_efface_le_verdict() {
        SiteEditViewModel viewModel = avecRecherche();
        viewModel.numeroCarreProperty().set(CARRE);
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.succes(List.of()));
        verifier(viewModel);

        viewModel.preparerCreation();

        assertThat(viewModel.retourCarreExistantProperty().get().texte()).isEmpty();
    }

    @Test
    @DisplayName("#3806 : le geste « récupérer » n'apparaît QUE quand le carré existe déjà")
    void le_geste_recuperer_suit_le_verdict() {
        SiteEditViewModel viewModel = avecRecherche();
        viewModel.numeroCarreProperty().set(CARRE);

        assertThat(viewModel.carreRecuperable().get())
                .as("rien n'a été demandé : il n'y a rien à récupérer")
                .isFalse();

        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.succes(List.of()));
        verifier(viewModel);
        assertThat(viewModel.carreRecuperable().get())
                .as("carré libre : le récupérer n'aurait aucun sens")
                .isFalse();

        when(client.chercherCarre(CARRE))
                .thenReturn(
                        ReponseApi.succes(List.of(new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-640380", true))));
        verifier(viewModel);
        assertThat(viewModel.carreRecuperable().get())
                .as("le carré est là-bas : c'est le moment de proposer de le rapatrier")
                .isTrue();

        viewModel.numeroCarreProperty().set("640381");
        assertThat(viewModel.carreRecuperable().get())
                .as("le verdict s'efface avec le numéro, le geste qu'il ouvrait aussi")
                .isFalse();
    }
}

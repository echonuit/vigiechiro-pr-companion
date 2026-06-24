package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.ResultatVerification;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.viewmodel.CarteSite;
import fr.univ_amu.iut.sites.viewmodel.SitesViewModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Test E2E de parcours (P1 - Déclarer un site de suivi)**, version **sans IHM** : on rejoue le
/// parcours de Marie **uniquement par la couche métier** (services réels + ViewModel d'accueil),
/// sur le **vrai chrome d'injection** (`RacineInjecteur`) adossé à une **base SQLite jetable**
/// (workspace temporaire + [MigrationSchema]). Aucun `FXMLLoader`, aucun `Stage`, aucun `FxRobot` :
/// les écrans sont remplacés par leurs ViewModels/services, ce qui suffit à vérifier le **résultat
/// métier** de chaque jalon du parcours.
///
/// Jalons du brief P1 vérifiés de bout en bout :
///
/// 1. **État vide** : à la première ouverture, l'accueil M-Sites détecte qu'aucun site n'est déclaré
///    ([SitesViewModel] `videProperty` à `true`, liste de cartes vide).
/// 2. **Déclaration** : Marie crée son premier site (n° de carré + nom convivial + protocole). Le
///    site est **enregistré localement** (id technique généré, date de création posée) et l'accueil
///    **bascule vers la vue des sites** (`videProperty` repasse à `false`, une carte apparaît).
/// 3. **Points d'écoute** : Marie ajoute ses codes de points (R2 : lettre + chiffre). Ils sont
///    persistés et la carte récapitulative du site reflète leur nombre et leurs codes.
///
/// Deux tests complémentaires couvrent les **règles métier visibles** du parcours : les refus de
/// saisie (R1 n° de carré, R2 code de point, R5 unicité du carré) et les **rappels de protocole**
/// (R3 : actifs sur `PointFixeStandard`, muets sur `PointFixeRecherche`).
class ParcoursDeclarerSiteE2ETest {

    private static final String ID_USER = "u-e2e-p1";

    private Injector injector;
    private ServiceSites service;

    @BeforeEach
    void preparer() throws Exception {
        // Base jetable : un workspace temporaire par test → injecteur et DB SQLite isolés.
        Path workspace = Files.createTempDirectory("vc-e2e-p1");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        // Application mono-utilisateur : le seul utilisateur en base désigne « idUtilisateurCourant »
        // (singleton Guice résolu à la première demande de ViewModel) ; le site créé lui sera rattaché.
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Marie"));
        service = injector.getInstance(ServiceSites.class);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("P1 : depuis l'état vide, déclarer un site bascule la vue des sites, puis ses points sont enregistrés")
    void declarer_premier_site_puis_ses_points() {
        // Jalon 1 : à l'ouverture, aucun site → l'accueil M-Sites est en état vide.
        SitesViewModel mesSites = injector.getInstance(SitesViewModel.class);
        mesSites.rafraichir();
        assertThat(mesSites.videProperty().get()).isTrue();
        assertThat(mesSites.cartes()).isEmpty();

        // Jalon 2 : Marie déclare son premier site (n° de carré + nom convivial + protocole standard).
        Site site = mesSites.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null);

        // Le site est enregistré localement (id technique généré, date de création posée) et rattaché
        // à l'utilisateur courant ; l'accueil bascule vers la vue des sites (n'est plus vide).
        assertThat(site.id()).isNotNull();
        assertThat(site.idUtilisateur()).isEqualTo(ID_USER);
        assertThat(site.dateCreation()).isNotNull();
        assertThat(mesSites.videProperty().get()).isFalse();
        assertThat(mesSites.cartes()).hasSize(1);

        // Persistance confirmée par une relecture indépendante du ViewModel (côté service).
        List<Site> sites = service.listerSites(ID_USER);
        assertThat(sites).hasSize(1);
        Site persiste = sites.get(0);
        assertThat(persiste.numeroCarre()).isEqualTo("640380");
        assertThat(persiste.nomConvivial()).isEqualTo("Étang de la Tuilière");
        assertThat(persiste.protocole()).isEqualTo(Protocole.STANDARD);

        // Jalon 3 : Marie ajoute ses points d'écoute (GPS et descriptif optionnels).
        PointDEcoute a1 = service.ajouterPoint(site.id(), "A1", 43.5298, 5.4474, "Près du chêne");
        PointDEcoute b2 = service.ajouterPoint(site.id(), "B2", null, null, null);
        assertThat(a1.id()).isNotNull();
        assertThat(b2.id()).isNotNull();

        // Les points sont persistés et listés triés par code.
        List<PointDEcoute> points = service.listerPoints(site.id());
        assertThat(points).extracting(PointDEcoute::code).containsExactly("A1", "B2");

        // La vue des sites reflète les points après rafraîchissement (carte récapitulative).
        mesSites.rafraichir();
        CarteSite carte = mesSites.cartes().get(0);
        assertThat(carte.site().numeroCarre()).isEqualTo("640380");
        assertThat(carte.nombrePoints()).isEqualTo(2);
        assertThat(carte.codesPoints()).contains("A1").contains("B2");
    }

    @Test
    @DisplayName(
            "P1 - règles visibles : R1 (n° de carré), R5 (unicité) et R2 (code de point) refusent les saisies invalides")
    void regles_de_saisie_visibles() {
        // R1 (dur) : un n° de carré sans zéro de tête (5 chiffres) est refusé à la saisie.
        assertThatThrownBy(() -> service.creerSite("40962", "Carré sans zéro", Protocole.STANDARD, null, ID_USER))
                .isInstanceOf(IllegalArgumentException.class);

        // Un n° bien formé passe et fixe la précondition de R5/R2.
        Site site = service.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        assertThat(site.id()).isNotNull();

        // R5 (dur) : le même carré pour le même utilisateur est refusé (règle métier).
        assertThatThrownBy(() -> service.creerSite("640380", "Doublon", Protocole.STANDARD, null, ID_USER))
                .isInstanceOf(RegleMetierException.class);

        // R2 (dur) : un code de point mal formé (pas « lettre majuscule + chiffre ») est refusé.
        assertThatThrownBy(() -> service.ajouterPoint(site.id(), "AA", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("P1 - rappels de protocole : R3 active sur PointFixeStandard, muette sur PointFixeRecherche")
    void rappels_de_protocole_selon_le_type_de_site() {
        // R3 (soft) : sur PointFixeStandard, l'application rappelle (sans bloquer) les 2 passages annuels.
        ResultatVerification standard = service.rappelsProtocole(Protocole.STANDARD);
        assertThat(standard.estConforme()).isFalse();
        assertThat(standard.estBloquant()).isFalse();
        assertThat(standard.messages()).anyMatch(message -> message.contains("2 passages"));

        // R3 muette sur PointFixeRecherche (cas Samuel : dates/fréquences libres) : aucun rappel.
        ResultatVerification recherche = service.rappelsProtocole(Protocole.RECHERCHE);
        assertThat(recherche.estConforme()).isTrue();
        assertThat(recherche.messages()).isEmpty();
    }
}

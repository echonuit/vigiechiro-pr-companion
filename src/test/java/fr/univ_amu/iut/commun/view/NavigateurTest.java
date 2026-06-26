package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.util.List;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Tests unitaires du [Navigateur] (socle) : historique de navigation (empiler / revenir / dépiler),
/// anti-ré-entrance, fil d'Ariane (emplacement sinon historique), garde de saisie et verrou (#54).
/// [ApplicationExtension] initialise le toolkit JavaFX (construction de nœuds) ; aucune scène affichée.
@ExtendWith(ApplicationExtension.class)
class NavigateurTest {

    @Start
    void start(Stage stage) {
        // Toolkit JavaFX initialisé ; aucune scène nécessaire pour ces tests.
    }

    private Navigateur navigateur(NavigationViewModel navigation, Parent accueil) {
        Navigateur navigateur = new Navigateur(navigation);
        navigateur.memoriserAccueil(accueil);
        return navigateur;
    }

    @Test
    @DisplayName("memoriserAccueil initialise l'historique à [Accueil] ; pas de retour possible")
    void memoriser_accueil_initialise_la_pile() {
        NavigationViewModel navigation = new NavigationViewModel();
        Parent accueil = new Group();
        Navigateur navigateur = navigateur(navigation, accueil);

        assertThat(navigateur.getVueCentrale()).isSameAs(accueil);
        assertThat(navigateur.peutRevenir()).isFalse();
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("accueil");
        assertThat(navigateur.filActuel()).extracting(Lieu::libelle).containsExactly("Accueil");
    }

    @Test
    @DisplayName("ouvrirRacine réinitialise la pile à [Accueil, écran]")
    void ouvrir_racine_reinitialise() {
        NavigationViewModel navigation = new NavigationViewModel();
        Navigateur navigateur = navigateur(navigation, new Group());

        navigateur.empiler(new Group(), "site-detail", "Carré 1", null);
        navigateur.ouvrirRacine(new Group(), "multisite", "Vue multi-sites", null);

        assertThat(navigateur.historique()).extracting(EtapeNavigation::id).containsExactly("accueil", "multisite");
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("multisite");
    }

    @Test
    @DisplayName("libelleRetour() indique l'écran précédent (destination du ← Retour)")
    void libelle_retour_indique_l_ecran_precedent() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        assertThat(navigateur.libelleRetour()).isNull(); // à l'accueil

        navigateur.ouvrirRacine(new Group(), "multisite", "Vue multi-sites", null);
        assertThat(navigateur.libelleRetour()).isEqualTo("Accueil");

        navigateur.empiler(new Group(), "passage", "Détails du passage N° 1", null);
        assertThat(navigateur.libelleRetour()).isEqualTo("Vue multi-sites");
    }

    @Test
    @DisplayName("empiler puis revenir préserve l'instance des écrans (état conservé)")
    void empiler_puis_revenir_preserve_les_vues() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        Parent sites = new Group();
        Parent detail = new Group();

        navigateur.ouvrirRacine(sites, "sites", "Mes sites", null);
        navigateur.empiler(detail, "site-detail", "Carré 640380", null);
        assertThat(navigateur.getVueCentrale()).isSameAs(detail);
        assertThat(navigateur.peutRevenir()).isTrue();

        navigateur.revenir();
        assertThat(navigateur.getVueCentrale()).isSameAs(sites);

        navigateur.revenir();
        assertThat(navigateur.peutRevenir()).isFalse();
    }

    @Test
    @DisplayName("revenirAIndex dépile jusqu'au niveau visé")
    void revenir_a_index() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        Parent sites = new Group();
        navigateur.ouvrirRacine(sites, "sites", "Mes sites", null);
        navigateur.empiler(new Group(), "site-detail", "Carré 1", null);
        navigateur.empiler(new Group(), "passage", "Passage", null);

        navigateur.revenirAIndex(1); // -> "sites"

        assertThat(navigateur.getVueCentrale()).isSameAs(sites);
        assertThat(navigateur.historique()).extracting(EtapeNavigation::id).containsExactly("accueil", "sites");
    }

    @Test
    @DisplayName("anti-ré-entrance : empiler un id déjà présent dépile jusqu'à lui (pas de doublon)")
    void anti_reentrance() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        navigateur.ouvrirRacine(new Group(), "sites", "Mes sites", null);
        navigateur.empiler(new Group(), "site-detail", "Carré 1", null);
        navigateur.empiler(new Group(), "passage", "Passage", null);
        Parent detail2 = new Group();

        navigateur.empiler(detail2, "site-detail", "Carré 2", null); // ré-entrance

        assertThat(navigateur.getVueCentrale()).isSameAs(detail2);
        assertThat(navigateur.filActuel()).extracting(Lieu::libelle).containsExactly("Accueil", "Mes sites", "Carré 2");
    }

    @Test
    @DisplayName("filActuel utilise l'emplacement déclaré (préfixé d'Accueil) plutôt que l'historique")
    void fil_suit_emplacement_declare() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        Object controleur = (EmplacementNavigation) () ->
                List.of(Lieu.vers("Mes sites", () -> {}), Lieu.vers("Carré 640380", () -> {}), Lieu.courant("Passage"));

        navigateur.empiler(new Group(), "passage", "Passage", controleur);

        assertThat(navigateur.filActuel())
                .extracting(Lieu::libelle)
                .containsExactly("Accueil", "Mes sites", "Carré 640380", "Passage");
    }

    @Test
    @DisplayName("afficherAccueil revient à la vue mémorisée et réinitialise le fil d'Ariane")
    void afficher_accueil_revient_a_l_accueil() {
        NavigationViewModel navigation = new NavigationViewModel();
        Parent accueil = new Group();
        Navigateur navigateur = navigateur(navigation, accueil);
        navigateur.empiler(new Group(), "sites", "Mes sites", null);

        navigateur.afficherAccueil();

        assertThat(navigateur.getVueCentrale()).isSameAs(accueil);
        assertThat(navigation.filArianeProperty().get()).isEqualTo("Accueil");
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("accueil");
    }

    @Test
    @DisplayName("afficherAccueil est sans effet tant qu'aucun accueil n'a été mémorisé")
    void afficher_accueil_sans_memorisation_est_neutre() {
        Navigateur navigateur = new Navigateur(new NavigationViewModel());
        Parent vue = new Group();
        navigateur.empiler(vue, "x", "X", null);

        navigateur.afficherAccueil();

        assertThat(navigateur.getVueCentrale()).isSameAs(vue);
    }

    @Test
    @DisplayName("#54 : le verrou de navigation bloque retour et accueil")
    void verrou_bloque_la_navigation() {
        NavigationViewModel navigation = new NavigationViewModel();
        Navigateur navigateur = navigateur(navigation, new Group());
        Parent ecran = new Group();
        navigateur.empiler(ecran, "import", "Importer une nuit", null);

        navigation.setNavigationVerrouillee(true);
        navigateur.revenir();
        navigateur.afficherAccueil();

        assertThat(navigateur.getVueCentrale()).isSameAs(ecran);
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("import");

        navigation.setNavigationVerrouillee(false);
        navigateur.afficherAccueil();
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("accueil");
    }

    @Test
    @DisplayName("garde de saisie : confirmation refusée → on reste ; acceptée → on quitte")
    void garde_de_saisie_confirme_avant_de_quitter() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        Parent formulaire = new Group();
        GardeQuitter gardeSale = () -> true; // saisie non enregistrée
        navigateur.ouvrirRacine(formulaire, "form", "Formulaire", gardeSale);

        navigateur.setConfirmateurQuitter(message -> false); // l'utilisateur annule
        navigateur.revenir();
        assertThat(navigateur.getVueCentrale()).isSameAs(formulaire);

        navigateur.setConfirmateurQuitter(message -> true); // l'utilisateur confirme
        navigateur.revenir();
        assertThat(navigateur.peutRevenir()).isFalse();
    }

    /// Faux controller d'écran qui compte les appels au hook de départ (#230).
    private static final class EcranAvecDepart implements AuDepartEcran {
        private int departs;

        @Override
        public void auDepartEcran() {
            departs++;
        }
    }

    @Test
    @DisplayName("#230 : revenir notifie le hook de départ de l'écran quitté")
    void revenir_notifie_le_depart() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        navigateur.ouvrirRacine(new Group(), "sites", "Mes sites", null);
        EcranAvecDepart importEcran = new EcranAvecDepart();
        navigateur.empiler(new Group(), "import", "Importer une nuit", importEcran);

        navigateur.revenir(); // on quitte l'import (dépilé)

        assertThat(importEcran.departs).isEqualTo(1);
    }

    @Test
    @DisplayName("#230 : empiler par-dessus ne notifie pas (l'écran reste vivant dans l'historique)")
    void empiler_par_dessus_ne_notifie_pas() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        EcranAvecDepart importEcran = new EcranAvecDepart();
        navigateur.ouvrirRacine(new Group(), "import", "Importer une nuit", importEcran);

        navigateur.empiler(new Group(), "passage", "Passage", null); // drill-down : import conservé

        assertThat(importEcran.departs).isZero();
    }

    @Test
    @DisplayName("#230 : afficherAccueil et ouvrirRacine notifient le départ des écrans retirés")
    void accueil_et_racine_notifient_le_depart() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        EcranAvecDepart importEcran = new EcranAvecDepart();
        navigateur.ouvrirRacine(new Group(), "import", "Importer une nuit", importEcran);
        navigateur.afficherAccueil(); // retour accueil → import retiré
        assertThat(importEcran.departs).isEqualTo(1);

        EcranAvecDepart autre = new EcranAvecDepart();
        navigateur.ouvrirRacine(new Group(), "import2", "Importer", autre);
        navigateur.ouvrirRacine(new Group(), "sites", "Mes sites", null); // nouvelle racine → autre retiré
        assertThat(autre.departs).isEqualTo(1);
    }
}

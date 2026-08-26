package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.commun.viewmodel.RevisionDonnees;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import java.util.List;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
        Navigateur navigateur = new Navigateur(navigation, new RevisionDonnees(Runnable::run));
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
        navigateur.ouvrirRacine(new Group(), "multisite", "Carte & passages", null);

        assertThat(navigateur.historique()).extracting(EtapeNavigation::id).containsExactly("accueil", "multisite");
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("multisite");
    }

    @Test
    @DisplayName("libelleRetour() indique l'écran précédent (destination du ← Retour)")
    void libelle_retour_indique_l_ecran_precedent() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        assertThat(navigateur.libelleRetour()).isNull(); // à l'accueil

        navigateur.ouvrirRacine(new Group(), "multisite", "Carte & passages", null);
        assertThat(navigateur.libelleRetour()).isEqualTo("Accueil");

        navigateur.empiler(new Group(), "passage", "Détails du passage N° 1", null);
        assertThat(navigateur.libelleRetour()).isEqualTo("Carte & passages");
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
    @DisplayName("#3519 : revenir() sur un historique d'une seule entrée ne le vide pas")
    void revenir_a_la_racine_ne_vide_pas_l_historique() {
        // Le cas était couvert par `peutRevenir()).isFalse()`, qui vérifie le DRAPEAU et non le GESTE :
        // rien n'appelait `revenir()` à la racine. La borne `historique.size() <= 1` passait donc pour
        // tenue alors que la remplacer par `<` laissait `historique.remove(0)` vider la pile.
        Parent accueil = new Group();
        Navigateur navigateur = navigateur(new NavigationViewModel(), accueil);

        navigateur.revenir();

        assertThat(navigateur.historique()).extracting(EtapeNavigation::id).containsExactly("accueil");
        assertThat(navigateur.getVueCentrale()).isSameAs(accueil);
        assertThat(navigateur.peutRevenir()).isFalse();
    }

    @Test
    @DisplayName("#3519 : revenirAIndex refuse l'écran courant, un index négatif et un index hors pile")
    void revenir_a_index_hors_bornes_est_sans_effet() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        navigateur.ouvrirRacine(new Group(), "sites", "Mes sites", null);
        Parent passage = new Group();
        EcranQuiSeRafraichit ecran = new EcranQuiSeRafraichit();
        navigateur.empiler(passage, "passage", "Passage", ecran);
        List<String> attendu = List.of("accueil", "sites", "passage");

        // `size() - 1` désigne l'écran DÉJÀ affiché : y « revenir » n'a pas de sens. Le test existant
        // n'exerçait qu'un index médian, si bien que les deux bornes de la garde restaient libres.
        navigateur.revenirAIndex(2);
        assertThat(navigateur.historique()).extracting(EtapeNavigation::id).containsExactlyElementsOf(attendu);

        navigateur.revenirAIndex(-1);
        assertThat(navigateur.historique()).extracting(EtapeNavigation::id).containsExactlyElementsOf(attendu);

        navigateur.revenirAIndex(3);
        assertThat(navigateur.historique()).extracting(EtapeNavigation::id).containsExactlyElementsOf(attendu);

        assertThat(navigateur.getVueCentrale()).isSameAs(passage);

        // L'assertion qui MORD est celle-ci, et elle a été trouvée en mutant à la main : relâcher la
        // borne (`>=` en `>`) laisse passer l'index de l'écran courant, mais la boucle de dépilage ne
        // retire alors rien - l'historique reste identique et les assertions ci-dessus restent vertes.
        // Le seul effet observable est le hook de retour, déclenché sur un écran qu'on n'a jamais quitté :
        // cliquer le segment de fil de l'écran affiché le rechargerait.
        assertThat(ecran.retours).isZero();
    }

    @Test
    @DisplayName("#3519 : l'anti-ré-entrance s'applique aussi à l'accueil, en index 0")
    void anti_reentrance_a_l_index_zero() {
        // `existant >= 0` : avec `>`, l'étape d'index 0 - l'accueil - échappait à l'anti-ré-entrance et
        // se dédoublait dans l'historique. Le test d'anti-ré-entrance existant porte sur l'index 1.
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        navigateur.ouvrirRacine(new Group(), "sites", "Mes sites", null);
        navigateur.empiler(new Group(), "passage", "Passage", null);
        Parent accueilRevisite = new Group();

        navigateur.empiler(accueilRevisite, "accueil", "Accueil", null);

        assertThat(navigateur.historique()).extracting(EtapeNavigation::id).containsExactly("accueil");
        assertThat(navigateur.getVueCentrale()).isSameAs(accueilRevisite);
    }

    @Test
    @DisplayName("#3519 : le fil marque le DERNIER segment comme courant, les précédents restent cliquables")
    void fil_marque_le_dernier_segment_comme_courant() {
        // `i == historique.size() - 1` portait un mutant arithmétique survivant : le marqueur « courant »
        // pouvait désigner le mauvais segment sans qu'aucune assertion ne le voie, les tests existants
        // ne comparant que les LIBELLÉS du fil.
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        navigateur.ouvrirRacine(new Group(), "sites", "Mes sites", null);
        navigateur.empiler(new Group(), "passage", "Passage", null);

        List<Lieu> fil = navigateur.filActuel();

        assertThat(fil).extracting(Lieu::libelle).containsExactly("Accueil", "Mes sites", "Passage");
        assertThat(fil.get(fil.size() - 1).ouvrir()).isNull();
        assertThat(fil.subList(0, fil.size() - 1))
                .allSatisfy(lieu -> assertThat(lieu.ouvrir()).isNotNull());
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
        Navigateur navigateur = new Navigateur(new NavigationViewModel(), new RevisionDonnees(Runnable::run));
        Parent vue = new Group();
        navigateur.empiler(vue, "x", "X", null);

        navigateur.afficherAccueil();

        assertThat(navigateur.getVueCentrale()).isSameAs(vue);
    }

    @Test
    @DisplayName("#906 : une opération critique en cours avertit avant de quitter (refuser reste, accepter part)")
    void operation_critique_avertit_avant_de_quitter() {
        NavigationViewModel navigation = new NavigationViewModel();
        Navigateur navigateur = navigateur(navigation, new Group());
        Parent ecran = new Group();
        navigateur.empiler(ecran, "import", "Importer une nuit", null);
        navigation.setOperationCritique("l'import");

        // Refus de la confirmation : on reste sur l'écran (retour et accueil neutralisés).
        navigateur.setConfirmateurQuitter(message -> false);
        navigateur.revenir();
        navigateur.afficherAccueil();
        assertThat(navigateur.getVueCentrale()).isSameAs(ecran);
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("import");

        // Confirmation : on peut partir malgré la tâche en cours (elle se poursuit en arrière-plan).
        navigateur.setConfirmateurQuitter(message -> true);
        navigateur.afficherAccueil();
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("accueil");
    }

    @Test
    @DisplayName("#906 : fermer l'app est confirmé si une opération critique est en cours")
    void fermeture_confirmee_si_operation_critique() {
        NavigationViewModel navigation = new NavigationViewModel();
        Navigateur navigateur = navigateur(navigation, new Group());

        // Rien en cours : la fermeture se poursuit sans question.
        assertThat(navigateur.confirmerFermeture()).isTrue();

        // Opération critique : refuser la confirmation annule la fermeture, l'accepter l'autorise.
        navigation.setOperationCritique("le dépôt");
        navigateur.setConfirmateurQuitter(message -> false);
        assertThat(navigateur.confirmerFermeture()).isFalse();
        navigateur.setConfirmateurQuitter(message -> true);
        assertThat(navigateur.confirmerFermeture()).isTrue();
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

    /// Faux controller qui compte les rechargements demandés par la **donnée** (contrat
    /// [SuitLaRevision]) : l'abonnement est posé et rendu par le `Navigateur`, pas par l'écran.
    private static final class EcranQuiSuitLaDonnee implements SuitLaRevision {
        private int rechargements;

        @Override
        public void rafraichirDepuisLaDonnee() {
            rechargements++;
        }
    }

    @Test
    @DisplayName("Un écran de l'historique qui déclare le contrat suit la révision des données")
    void un_ecran_empile_suit_la_revision() {
        RevisionDonnees revision = new RevisionDonnees(Runnable::run);
        Navigateur navigateur = new Navigateur(new NavigationViewModel(), revision);
        navigateur.memoriserAccueil(new Group());
        EcranQuiSuitLaDonnee saison = new EcranQuiSuitLaDonnee();
        navigateur.empiler(new Group(), "saison", "Ma saison", saison);

        revision.mutationStructurelleValidee();

        assertThat(saison.rechargements)
                .as("l'écran n'a rien fait pour s'abonner : c'est le Navigateur qui l'a posé")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Relibeller l'étape courante ne RÉ-abonne pas : une mutation reste un rechargement")
    void relibeller_ne_reabonne_pas() {
        RevisionDonnees revision = new RevisionDonnees(Runnable::run);
        Navigateur navigateur = new Navigateur(new NavigationViewModel(), revision);
        navigateur.memoriserAccueil(new Group());
        EcranQuiSuitLaDonnee passage = new EcranQuiSuitLaDonnee();
        navigateur.empiler(new Group(), "passage", "Détails du passage", passage);

        // #1213 : le chargement asynchrone se termine, l'étape est REMPLACÉE par sa jumelle relibellée.
        // La vue, elle, n'a pas bougé : l'écran n'a été ni quitté ni rouvert.
        navigateur.actualiserLibelleCourant(passage, "Détails du passage N° 2");
        revision.mutationStructurelleValidee();

        assertThat(passage.rechargements)
                .as("un abonnement de plus ferait relire la base deux fois pour un seul import")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Un écran sorti de l'historique ne suit plus la révision")
    void un_ecran_sorti_ne_suit_plus_la_revision() {
        RevisionDonnees revision = new RevisionDonnees(Runnable::run);
        Navigateur navigateur = new Navigateur(new NavigationViewModel(), revision);
        navigateur.memoriserAccueil(new Group());
        EcranQuiSuitLaDonnee saison = new EcranQuiSuitLaDonnee();
        navigateur.empiler(new Group(), "saison", "Ma saison", saison);

        navigateur.revenir();
        revision.mutationStructurelleValidee();

        assertThat(saison.rechargements)
                .as("`RevisionDonnees` est un singleton : sans retrait, l'écoute survit à l'écran")
                .isZero();
    }

    /// Faux controller qui compte les rafraîchissements au retour (#3519). C'est le **seul** effet
    /// observable d'un `revenirAIndex` qui accepterait l'index de l'écran courant : la boucle de
    /// dépilage ne retire rien, mais le hook se déclenche sur un écran qu'on n'a jamais quitté.
    private static final class EcranQuiSeRafraichit implements RafraichirAuRetour {
        private int retours;

        @Override
        public void rafraichirAuRetour() {
            retours++;
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

    @Test
    @DisplayName("#1213 : actualiserLibelleCourant relibelle l'étape au sommet sans notifier son départ")
    void actualiser_libelle_courant_relibelle_le_sommet() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        EcranAvecDepart passage = new EcranAvecDepart();
        navigateur.ouvrirRacine(new Group(), "sites", "Mes sites", null);
        navigateur.empiler(new Group(), "passage", "Détails du passage", passage);

        // Chargement asynchrone terminé : l'écran connaît enfin son numéro.
        navigateur.actualiserLibelleCourant(passage, "Détails du passage N° 2");

        assertThat(navigateur
                        .historique()
                        .get(navigateur.historique().size() - 1)
                        .libelle())
                .isEqualTo("Détails du passage N° 2");
        assertThat(passage.departs)
                .as("le remplacement d'étape (même vue) n'est pas un départ d'écran")
                .isZero();
        // Le ← Retour d'un écran empilé par-dessus montre le libellé actualisé.
        navigateur.empiler(new Group(), "lot", "Préparer le dépôt", null);
        assertThat(navigateur.libelleRetour()).isEqualTo("Détails du passage N° 2");
    }

    @Test
    @DisplayName("#1213 : actualiserLibelleCourant est sans effet si l'écran n'est pas au sommet")
    void actualiser_libelle_courant_ignore_un_autre_ecran() {
        Navigateur navigateur = navigateur(new NavigationViewModel(), new Group());
        EcranAvecDepart passage = new EcranAvecDepart();
        navigateur.ouvrirRacine(new Group(), "passage", "Détails du passage", passage);
        navigateur.empiler(new Group(), "lot", "Préparer le dépôt", null);

        // Exécution synchrone (tests, captures) : le chargement se conclut avant l'empilement, le
        // sommet appartient encore à l'écran précédent - aucune étape ne doit être relibellée.
        navigateur.actualiserLibelleCourant(passage, "Détails du passage N° 2");

        assertThat(navigateur
                        .historique()
                        .get(navigateur.historique().size() - 1)
                        .libelle())
                .isEqualTo("Préparer le dépôt");
        assertThat(navigateur
                        .historique()
                        .get(navigateur.historique().size() - 2)
                        .libelle())
                .isEqualTo("Détails du passage");
    }

    @Test
    @DisplayName("La barre de statut suit le résumé zoné de l'écran au sommet (ResumeStatut), défaut sinon")
    void pied_suit_le_resume_de_l_ecran() {
        NavigationViewModel navigation = new NavigationViewModel();
        Navigateur navigateur = navigateur(navigation, new Group());
        assertThat(navigation.getZonesStatut())
                .as("accueil sans résumé → zones par défaut (toutes vides → barre masquée)")
                .isEqualTo(NavigationViewModel.ZONES_DEFAUT);
        assertThat(navigation.getZonesStatut().estVide()).isTrue();

        // Écran déclarant un résumé zoné (centre + droite), la gauche laissée vide.
        ReadOnlyObjectWrapper<ZonesStatut> resume =
                new ReadOnlyObjectWrapper<>(ZonesStatut.centreEtDroite("42 observation(s)", "3 / 42 revues"));
        ResumeStatut ecranAudio = resume::getReadOnlyProperty;
        navigateur.empiler(new Group(), "audio", "Sons & validation", ecranAudio);
        // Le résumé de l'écran occupe centre + droite ; la gauche reste vide (aucun défaut à afficher).
        assertThat(navigation.getZonesStatut().gauche()).isEmpty();
        assertThat(navigation.getZonesStatut().centre()).isEqualTo("42 observation(s)");
        assertThat(navigation.getZonesStatut().droite()).isEqualTo("3 / 42 revues");

        // Mise à jour en direct du résumé → le pied suit.
        resume.set(ZonesStatut.centreEtDroite("42 observation(s)", "10 / 42 revues"));
        assertThat(navigation.getZonesStatut().droite()).isEqualTo("10 / 42 revues");

        // Écran sans résumé au sommet → retour aux zones par défaut (et plus de lien vivant).
        navigateur.empiler(new Group(), "autre", "Autre écran", null);
        assertThat(navigation.getZonesStatut()).isEqualTo(NavigationViewModel.ZONES_DEFAUT);
        resume.set(ZonesStatut.centre("ne doit plus être reflété"));
        assertThat(navigation.getZonesStatut()).isEqualTo(NavigationViewModel.ZONES_DEFAUT);
    }

    @Test
    @DisplayName("Un écran peut renseigner la zone gauche (contexte) ; les zones vides restent vides")
    void ecran_peut_renseigner_zone_gauche() {
        NavigationViewModel navigation = new NavigationViewModel();
        Navigateur navigateur = navigateur(navigation, new Group());

        // L'écran renseigne gauche (contexte) + centre, mais laisse la droite vide.
        ReadOnlyObjectWrapper<ZonesStatut> resume =
                new ReadOnlyObjectWrapper<>(new ZonesStatut("Carré 640380 · A1", "60 observation(s)", ""));
        ResumeStatut ecran = resume::getReadOnlyProperty;
        navigateur.empiler(new Group(), "audio", "Sons & validation", ecran);

        assertThat(navigation.getZonesStatut().gauche()).as("contexte à gauche").isEqualTo("Carré 640380 · A1");
        assertThat(navigation.getZonesStatut().centre()).isEqualTo("60 observation(s)");
        assertThat(navigation.getZonesStatut().droite())
                .as("zone non renseignée → vide")
                .isEmpty();
    }
}

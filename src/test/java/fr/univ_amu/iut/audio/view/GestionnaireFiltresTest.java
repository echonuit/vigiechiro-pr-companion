package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.view.ClesCriteres;
import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Barre de filtres « à la Notion » (#470/#471) : ajouter/retirer une puce via « + Filtre », recherche
/// texte permanente, réinitialisation. Vérifie le câblage sur la [FilteredList] via [Filtres].
@ExtendWith(ApplicationExtension.class)
class GestionnaireFiltresTest {

    private TextField recherche;
    private MenuButton menu;
    private FlowPane puces;
    private FilteredList<LigneObservationAudio> affichees;
    private GestionnaireFiltres<LigneObservationAudio> gestionnaire;

    @Start
    void start(Stage stage) {
        recherche = new TextField();
        menu = new MenuButton("+ Filtre");
        puces = new FlowPane();
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligne(1, "Pippip", "PaRec_1.wav", StatutObservation.NON_TOUCHEE),
                ligne(2, "Nyclei", "PaRec_2.wav", StatutObservation.VALIDEE),
                // Observation CORRIGÉE : Tadarida disait « Bruit », l'observateur a retenu « Grand Rhinolophe ».
                ligneCorrigee(3, "Bruit", "Rhifer", "Grand Rhinolophe", "PaRec_3.wav"));
        affichees = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtres = new Filtres<>(affichees, () -> {});
        gestionnaire = new GestionnaireFiltres<>(
                recherche, menu, puces, filtres, List.of(CriteresAudio.statut()), CriteresAudio.rechercheTexte());
        FenetreAjustable.poser(stage, new VBox(recherche, menu, puces), 400, 200);
        FenetreAjustable.afficher(stage);
    }

    @Test
    @DisplayName("« + Filtre » ajoute la puce Statut (À revoir par défaut), filtre, et vide le menu ; ✕ restaure")
    void ajouter_puis_retirer_la_puce_statut(FxRobot robot) {
        assertThat(affichees).hasSize(3);
        assertThat(menu.getItems()).extracting(MenuItem::getText).containsExactly("Statut");
        assertThat(puces.getChildren()).isEmpty();

        // Ajouter le filtre Statut → défaut À revoir → seule la ligne 1 reste ; menu vidé ; une puce.
        robot.interact(() -> menu.getItems().get(0).fire());
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(1L);
        assertThat(menu.getItems()).isEmpty();
        assertThat(puces.getChildren()).hasSize(1);

        // Retirer la puce (✕) → tout revisible ; le critère revient au menu.
        robot.interact(() -> boutonRetirer().fire());
        assertThat(affichees).hasSize(3);
        assertThat(menu.getItems()).hasSize(1);
        assertThat(puces.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("Recherche texte : filtre sur fichier / taxon (insensible casse/accents)")
    void recherche_texte(FxRobot robot) {
        robot.interact(() -> recherche.setText("nyclei"));
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(2L);
        robot.interact(() -> recherche.clear());
        assertThat(affichees).hasSize(3);
    }

    @Test
    @DisplayName("Recherche texte : trouve l'espèce RETENUE d'une observation corrigée (taxon + vernaculaire)")
    void recherche_texte_espece_corrigee(FxRobot robot) {
        // La ligne 3 est « Bruit » côté Tadarida mais corrigée en « Grand Rhinolophe » (code Rhifer) :
        // chercher l'espèce retenue doit la trouver, même si Tadarida ne l'a pas proposée.
        robot.interact(() -> recherche.setText("rhinolophe"));
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(3L);
        robot.interact(() -> recherche.setText("rhifer")); // code taxon observateur
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(3L);
    }

    @Test
    @DisplayName("reinitialiser efface la recherche et les puces (tout redevient visible)")
    void reinitialiser(FxRobot robot) {
        robot.interact(() -> {
            menu.getItems().get(0).fire(); // Statut = À revoir
            recherche.setText("zzz"); // ne matche rien
        });
        assertThat(affichees).isEmpty();

        robot.interact(() -> gestionnaire.reinitialiser());
        assertThat(affichees).hasSize(3);
        assertThat(puces.getChildren()).isEmpty();
        assertThat(recherche.getText()).isEmpty();
    }

    @Test
    @DisplayName("Critère Groupe : choix = groupes présents (distincts, triés) ; défaut « Chiroptères » ; re-filtre")
    void filtre_groupe(FxRobot robot) {
        // Jeu mixte chauves-souris / oiseaux (deux Chiroptères pour vérifier la déduplication).
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligne(1, "Pippip", "PaRec_1.wav", StatutObservation.VALIDEE), // Chiroptères
                ligneGroupe(2, "Turmer", "PaRec_2.wav", StatutObservation.VALIDEE, "Oiseaux"),
                ligne(3, "Nyclei", "PaRec_3.wav", StatutObservation.VALIDEE)); // Chiroptères
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<LigneObservationAudio> ignore = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresAudio.groupe(() -> source)),
                CriteresAudio.rechercheTexte());
        assertThat(ignore).isNotNull();

        // Ajouter la puce Groupe : le sélecteur liste les groupes présents (distincts, triés) et défaut
        // « Chiroptères » → seules les chauves-souris restent.
        robot.interact(() -> menuLocal.getItems().get(0).fire());
        ComboBox<Object> choixGroupe = comboDe(pucesLocales);
        assertThat(choixGroupe.getItems()).containsExactly("Chiroptères", "Oiseaux");
        assertThat(choixGroupe.getValue()).isEqualTo("Chiroptères");
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(1L, 3L);

        // Le même critère sert tout groupe : basculer sur « Oiseaux » → seule l'observation d'oiseau reste.
        robot.interact(() -> choixGroupe.setValue("Oiseaux"));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(2L);
    }

    @Test
    @DisplayName("Critère Espèce : liste les espèces présentes (taxon retenu) ; sélectionner restreint à l'espèce")
    void filtre_taxon(FxRobot robot) {
        // Ligne 1 retenue Pippip (sans vernaculaire → libellé = code) ; ligne 2 corrigée : Tadarida dit
        // « Bruit » mais l'observateur a retenu « Rhifer » (Grand Rhinolophe) → le taxon retenu est Rhifer.
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligne(1, "Pippip", "PaRec_1.wav", StatutObservation.VALIDEE),
                ligneCorrigee(2, "Bruit", "Rhifer", "Grand Rhinolophe", "PaRec_2.wav"));
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<LigneObservationAudio> ignore = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresAudio.taxon(() -> source)),
                CriteresAudio.rechercheTexte());
        assertThat(ignore).isNotNull();

        // Ajouter la puce Espèce : espèces présentes triées par libellé, aucune présélection (rien masqué).
        robot.interact(() -> menuLocal.getItems().get(0).fire());
        ComboBox<Object> choix = comboDe(pucesLocales);
        List<String> libelles = choix.getItems().stream()
                .map(espece -> choix.getConverter().toString(espece))
                .toList();
        assertThat(libelles).containsExactly("Grand Rhinolophe", "Pippip");
        assertThat(vues).hasSize(2);

        // Sélectionner « Grand Rhinolophe » (taxon retenu Rhifer, corrigé depuis Bruit) → seule cette obs.
        robot.interact(() -> choix.setValue(choix.getItems().get(0)));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(2L);
    }

    @Test
    @DisplayName("#3336 : la puce Certitude filtre celle de l'OBSERVATEUR, pas celle du validateur")
    void filtre_certitude(FxRobot robot) {
        // Le piege que ce cas épingle : les deux champs existent (`certitude` et `certitudeValidateur`)
        // et une puce qui viserait le second donnerait une parité de façade - meme nom que
        // `lister-observations --certitude`, résultats différents. Les deux lignes portent donc des
        // valeurs CROISÉES : seule celle de l'observateur doit décider.
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligneCertitude(1, Certitude.SUR, Certitude.POSSIBLE),
                ligneCertitude(2, Certitude.POSSIBLE, Certitude.SUR),
                ligneCertitude(3, null, Certitude.SUR));
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<LigneObservationAudio> ignore = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresAudio.certitude()),
                CriteresAudio.rechercheTexte());
        assertThat(ignore).isNotNull();

        // Ajouter la puce n'écarte rien tant qu'aucun niveau n'est choisi.
        robot.interact(() -> menuLocal.getItems().get(0).fire());
        assertThat(vues).as("une puce ajoutée n'écarte rien").hasSize(3);

        ComboBox<Object> choix = comboDe(pucesLocales);
        List<String> libelles = choix.getItems().stream()
                .map(niveau -> choix.getConverter().toString(niveau))
                .toList();
        assertThat(libelles).containsExactly("Sûr", "Probable", "Possible");

        // « Sur » : la ligne 1 (observateur SUR), et surtout PAS la ligne 2 dont seul le validateur l'est.
        robot.interact(() -> choix.setValue(choix.getItems().get(0)));
        assertThat(vues)
                .as("la ligne 2 est SUR cote validateur : la retenir prouverait qu'on filtre le mauvais champ")
                .extracting(LigneObservationAudio::idObservation)
                .containsExactly(1L);

        // « Possible » : la ligne 2. La ligne 3 n'a aucune certitude déclarée et n'est retenue par aucun
        // niveau - ne rien déclarer n'est pas un quatrième niveau.
        robot.interact(() -> choix.setValue(choix.getItems().get(2)));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(2L);
    }

    @Test
    @DisplayName("Critère Références : booléen sans éditeur ; activé, seules les observations en référence restent")
    void filtre_references(FxRobot robot) {
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligne(1, "Pippip", "PaRec_1.wav", StatutObservation.VALIDEE), // pas en référence
                ligneReference(2, "Nyclei", "PaRec_2.wav")); // en référence
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<LigneObservationAudio> ignore = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresAudio.references()),
                CriteresAudio.rechercheTexte());
        assertThat(ignore).isNotNull();

        // Ajouter la puce Références : critère booléen (puce = Label + ✕, sans éditeur) → filtre actif.
        robot.interact(() -> menuLocal.getItems().get(0).fire());
        assertThat(((HBox) pucesLocales.getChildren().get(0)).getChildren()).hasSize(2);
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(2L);

        // Retirer la puce → tout redevient visible.
        robot.interact(() -> boutonRetirerDe(pucesLocales).fire());
        assertThat(vues).hasSize(2);
    }

    @Test
    @DisplayName("Critère Proba : garde les proba ≥ seuil ; les observations SANS proba restent toujours visibles")
    void filtre_probabilite(FxRobot robot) {
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligneProba(1, "Pippip", 0.9), // sûre
                ligneProba(2, "Nyclei", 0.3), // peu sûre
                ligneProba(3, "Tadten", null)); // sans proba
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<LigneObservationAudio> ignore = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresAudio.probabilite()),
                CriteresAudio.rechercheTexte());
        assertThat(ignore).isNotNull();

        // Ajouter la puce Proba : défaut 50 % → garde 0.9 (≥ 0.5) et la ligne sans proba ; 0.3 masquée.
        robot.interact(() -> menuLocal.getItems().get(0).fire());
        Slider curseur = sliderDe(pucesLocales);
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(1L, 3L);

        // Monter le seuil à 95 % → 0.9 exclue, seule la ligne sans proba reste (toujours conservée).
        robot.interact(() -> curseur.setValue(0.95));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(3L);

        // Descendre à 0 % → toutes visibles.
        robot.interact(() -> curseur.setValue(0.0));
        assertThat(vues).hasSize(3);
    }

    @Test
    @DisplayName("Critère Heure : plage nuit (21h→6h) par défaut ; heures de jour masquées ; passage à minuit géré")
    void filtre_heure(FxRobot robot) {
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligneHeure(1, LocalDateTime.of(2026, 4, 22, 22, 0)), // 22:00 nuit
                ligneHeure(2, LocalDateTime.of(2026, 4, 22, 13, 0)), // 13:00 jour
                ligneHeure(3, LocalDateTime.of(2026, 4, 23, 3, 0)), // 03:00 après minuit (nuit)
                ligneHeure(4, null)); // sans heure
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<LigneObservationAudio> ignore = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresAudio.heure()),
                CriteresAudio.rechercheTexte());
        assertThat(ignore).isNotNull();

        // Ajouter la puce Heure : défaut nuit 21h→6h (à cheval sur minuit) → garde 22:00, 03:00 et la ligne
        // sans heure ; masque 13:00 (jour).
        robot.interact(() -> menuLocal.getItems().get(0).fire());
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(1L, 3L, 4L);

        // Basculer sur les heures de JOUR (6h→20h, sans passage à minuit) → garde 13:00 + sans heure.
        ComboBox<Integer> de = comboHeure(pucesLocales, 1);
        ComboBox<Integer> a = comboHeure(pucesLocales, 3);
        robot.interact(() -> {
            de.setValue(6);
            a.setValue(20);
        });
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(2L, 4L);
    }

    @Test
    @DisplayName("#549 : critère Heure, le défaut suit la nuit fournie (coucher/lever) plutôt que 21h→6h")
    void filtre_heure_defaut_depuis_ephemeride(FxRobot robot) {
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(FXCollections.observableArrayList());
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<LigneObservationAudio> ignore = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresAudio.heure(() -> Optional.of(new PlageNuit(19, 7)))),
                CriteresAudio.rechercheTexte());
        assertThat(ignore).isNotNull();

        // Ajouter la puce Heure : le défaut vient de la plage nuit fournie (19 h → 7 h), pas du fixe 21→6.
        robot.interact(() -> menuLocal.getItems().get(0).fire());

        assertThat(comboHeure(pucesLocales, 1).getValue()).isEqualTo(19);
        assertThat(comboHeure(pucesLocales, 3).getValue()).isEqualTo(7);
    }

    @Test
    @DisplayName("#537 : decrire() produit un descripteur sémantique transportable des filtres actifs")
    void decrire_produit_un_descripteur_semantique(FxRobot robot) {
        ObservableList<LigneObservationAudio> source =
                FXCollections.observableArrayList(ligne(1, "Pippip", "PaRec_1.wav", StatutObservation.VALIDEE));
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        TextField rechercheLocale = new TextField();
        GestionnaireFiltres<LigneObservationAudio> gestion = new GestionnaireFiltres<>(
                rechercheLocale,
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(
                        CriteresAudio.statut(),
                        CriteresAudio.probabilite(),
                        CriteresAudio.heure(),
                        CriteresAudio.references()),
                CriteresAudio.rechercheTexte());

        // Recherche texte + les 4 puces (ordre du menu : statut, proba, heure, références), valeurs par défaut.
        robot.interact(() -> {
            rechercheLocale.setText("bruant");
            for (int i = 0; i < 4; i++) {
                menuLocal.getItems().get(0).fire();
            }
        });

        // Valeurs SÉMANTIQUES (pas des index d'IHM) : statut À revoir, proba 50 %, nuit 21→6, référence booléenne.
        assertThat(gestion.decrire())
                .isEqualTo(new DescripteurFiltre(
                        "bruant",
                        List.of(
                                new DescripteurCritere("statut", List.of("NON_TOUCHEE")),
                                new DescripteurCritere("proba", List.of("0.5")),
                                new DescripteurCritere("heure", List.of("21", "6")),
                                new DescripteurCritere("references", List.of()))));
    }

    @Test
    @DisplayName("#623 : restaurer(descripteur) reconstruit les puces ET leurs valeurs (rejoue une vue mémorisée)")
    void restaurer_reconstruit_les_puces_depuis_un_descripteur(FxRobot robot) {
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligne(1, "Pippip", "PaRec_1.wav", StatutObservation.NON_TOUCHEE),
                ligne(2, "Nyclei", "PaRec_2.wav", StatutObservation.VALIDEE));
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        TextField rechercheLocale = new TextField();
        GestionnaireFiltres<LigneObservationAudio> gestion = new GestionnaireFiltres<>(
                rechercheLocale,
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresAudio.statut(), CriteresAudio.heure()),
                CriteresAudio.rechercheTexte());

        // Rejouer une vue décrite sémantiquement : recherche « pa » + statut Validée + heures de jour 6→20.
        robot.interact(() -> gestion.restaurer(new DescripteurFiltre(
                "pa",
                List.of(
                        new DescripteurCritere("statut", List.of("VALIDEE")),
                        new DescripteurCritere("heure", List.of("6", "20"))))));

        // Recherche + deux puces reconstruites, dans l'ordre du descripteur.
        assertThat(rechercheLocale.getText()).isEqualTo("pa");
        assertThat(pucesLocales.getChildren()).hasSize(2);
        // La puce Statut porte la valeur RESTAURÉE (Validée), pas le défaut À revoir.
        assertThat(comboDe(pucesLocales).getValue()).isEqualTo(StatutObservation.VALIDEE);
        // Le filtre reflète les valeurs restaurées : seule la ligne 2 (VALIDEE, fichier « PaRec ») reste.
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(2L);
        // Round-trip sémantique : decrire() reproduit exactement le descripteur restauré.
        assertThat(gestion.decrire())
                .isEqualTo(new DescripteurFiltre(
                        "pa",
                        List.of(
                                new DescripteurCritere("statut", List.of("VALIDEE")),
                                new DescripteurCritere("heure", List.of("6", "20")))));
    }

    @Test
    @DisplayName("#476 : poser(nom, valeurs) ajoute la puce absente et la règle (filtre piloté par la vue)")
    void poser_ajoute_et_regle_une_puce(FxRobot robot) {
        assertThat(puces.getChildren()).isEmpty(); // aucune puce au départ

        robot.interact(() -> gestionnaire.poser("statut", List.of("VALIDEE")));

        assertThat(puces.getChildren()).hasSize(1); // la puce Statut a été ajoutée
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(2L); // VALIDEE

        // Re-poser une autre valeur met à jour la puce EXISTANTE (pas de doublon).
        robot.interact(() -> gestionnaire.poser("statut", List.of("NON_TOUCHEE")));
        assertThat(puces.getChildren()).hasSize(1);
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(1L); // À revoir
    }

    @Test
    @DisplayName(
            "#2794 : critère Lieu : valeurs présentes groupées par dimension ; chaque dimension filtre ; cocher = appartenance")
    void filtre_lieu(FxRobot robot) {
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligneLieu(1, "Rhifer", "Aix-en-Provence", "640380", "A1", "Jardin de Serge"),
                ligneLieu(2, "Pippip", "Venelles", "870150", "B2", "Le pré"));
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<LigneObservationAudio> ignore = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresAudio.lieu(() -> source)),
                CriteresAudio.rechercheTexte());
        assertThat(ignore).isNotNull();

        // Ajouter la puce Lieu : valeurs distinctes groupées par dimension (communes, carrés, points),
        // triées au sein de chacune ; rien de coché n'écarte rien.
        robot.interact(() -> menuLocal.getItems().get(0).fire());
        MenuButton choixLieu = menuBoutonDe(pucesLocales, 0);
        // Les EN-TÊTES nomment les dimensions, dans l'ordre (#2992) : sans eux, rien ne dit si
        // « Aix-en-Provence » est une commune ou un carré.
        assertThat(entetes(choixLieu)).containsExactly("Communes", "Carrés", "Points");
        // Les valeurs COCHABLES, groupe par groupe. Le point est qualifié par son carré : le schéma pose
        // UNIQUE(site_id, code), donc « A1 » seul désignerait autant de lieux qu'il y a de carrés. Le
        // carré, lui, porte son NOM CONVIVIAL (#3157) : il n'y a plus de groupe « Sites » offrant une
        // seconde fois les mêmes lieux sous leur autre étiquette.
        assertThat(cochables(choixLieu))
                .containsExactly(
                        "Aix-en-Provence",
                        "Venelles",
                        "640380 · Jardin de Serge",
                        "870150 · Le pré",
                        "640380 · A1",
                        "870150 · B2");
        assertThat(vues).hasSize(2);

        // Cocher la COMMUNE « Aix-en-Provence » (lot 0) → seule la ligne 1 reste.
        robot.interact(() -> coche(choixLieu, "Aix-en-Provence").setSelected(true));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(1L);

        // Cocher AUSSI le CARRÉ de l'autre ligne → appartenance : chaque ligne passe par l'une de ses
        // dimensions.
        robot.interact(() -> coche(choixLieu, "870150 · Le pré").setSelected(true));
        assertThat(vues).hasSize(2);

        // Le POINT seul, puis le CARRÉ seul : chaque dimension filtre. Le carré retient par son entrée
        // unique la ligne que son numéro retenait et celle que son nom retenait.
        robot.interact(() -> {
            coche(choixLieu, "Aix-en-Provence").setSelected(false);
            coche(choixLieu, "870150 · Le pré").setSelected(false);
            coche(choixLieu, "870150 · B2").setSelected(true);
        });
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(2L);
        robot.interact(() -> {
            coche(choixLieu, "870150 · B2").setSelected(false);
            coche(choixLieu, "640380 · Jardin de Serge").setSelected(true);
        });
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(1L);
    }

    @Test
    @DisplayName("#2794 : espèce × lieu : la puce Lieu compose avec la puce Espèce (grand Rhinolophe à Aix)")
    void filtre_espece_et_lieu(FxRobot robot) {
        // Le scénario Samuel : source ParEspece = « le grand Rhinolophe partout » (Aix ET Venelles) ; une
        // pipistrelle à Aix sert de bruit. Espèce × lieu doit isoler la ligne 1.
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligneLieu(1, "Rhifer", "Aix-en-Provence", "640380", "A1", null),
                ligneLieu(2, "Rhifer", "Venelles", "870150", "B2", null),
                ligneLieu(3, "Pippip", "Aix-en-Provence", "640380", "A1", null));
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<LigneObservationAudio> ignore = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresAudio.taxon(() -> source), CriteresAudio.lieu(() -> source)),
                CriteresAudio.rechercheTexte());
        assertThat(ignore).isNotNull();

        // Puce Espèce → « Rhifer » : le grand Rhinolophe partout (lignes 1 et 2).
        robot.interact(() -> menuLocal.getItems().get(0).fire());
        ComboBox<Object> choixEspece = comboDe(pucesLocales);
        robot.interact(() -> choixEspece.setValue(choixEspece.getItems().get(1))); // Pippip, Rhifer (triés)
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(1L, 2L);

        // Puce Lieu → « Aix-en-Provence » : l'intersection espèce × lieu isole la ligne 1.
        robot.interact(() -> menuLocal.getItems().get(0).fire());
        robot.interact(
                () -> coche(menuBoutonDe(pucesLocales, 1), "Aix-en-Provence").setSelected(true));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(1L);
    }

    @Test
    @DisplayName("#2794 : la puce Lieu se mémorise et se rejoue (vues nommées), restaurer()/decrire() round-trip")
    void lieu_round_trip_vue_memorisee(FxRobot robot) {
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligneLieu(1, "Rhifer", "Aix-en-Provence", "640380", "A1", null),
                ligneLieu(2, "Pippip", "Venelles", "870150", "B2", null));
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<LigneObservationAudio> gestion = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresAudio.lieu(() -> source)),
                CriteresAudio.rechercheTexte());

        // Rejouer une vue mémorisée portant le lieu « Aix-en-Provence » : la puce se reconstruit cochée.
        robot.interact(() -> gestion.restaurer(
                new DescripteurFiltre("", List.of(new DescripteurCritere("lieu", List.of("Aix-en-Provence"))))));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(1L);

        // Round-trip sémantique : decrire() reproduit exactement le descripteur restauré.
        assertThat(gestion.decrire())
                .isEqualTo(
                        new DescripteurFiltre("", List.of(new DescripteurCritere("lieu", List.of("Aix-en-Provence")))));
    }

    @Test
    @DisplayName("#2794 : la recherche texte couvre carré, point, site et commune (insensible casse/accents)")
    void recherche_texte_geographique(FxRobot robot) {
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligneLieu(1, "Pippip", "Aix-en-Provence", "640380", "A1", "Jardin de Serge"),
                ligneLieu(2, "Nyclei", "Venelles", "870150", "B2", "Le pré"));
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        TextField rechercheLocale = new TextField();
        GestionnaireFiltres<LigneObservationAudio> ignore = new GestionnaireFiltres<>(
                rechercheLocale,
                new MenuButton(),
                new FlowPane(),
                filtresLocaux,
                List.of(),
                CriteresAudio.rechercheTexte());
        assertThat(ignore).isNotNull();

        // Le SITE (« Jardin ») puis la COMMUNE (« venelles », casse ignorée) : la vue audio rejoint Analyse.
        robot.interact(() -> rechercheLocale.setText("jardin"));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(1L);
        robot.interact(() -> rechercheLocale.setText("venelles"));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(2L);

        // Le CARRÉ puis le POINT : les colonnes affichées deviennent cherchables.
        robot.interact(() -> rechercheLocale.setText("640380"));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(1L);
        robot.interact(() -> rechercheLocale.setText("b2"));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(2L);
    }

    private Button boutonRetirer() {
        return (Button) puces.lookupAll(".puce-filtre-retirer").iterator().next();
    }

    /// L'éditeur du critère Lieu est un [MenuButton] à cases à cocher, 2e enfant de sa puce.
    /// Les intitulés des **en-têtes** de groupe : les items désactivés, qui nomment sans se cocher.
    private static List<String> entetes(MenuButton bouton) {
        return bouton.getItems().stream()
                .filter(item -> !(item instanceof CheckMenuItem) && item.isDisable())
                .map(MenuItem::getText)
                .toList();
    }

    /// Les valeurs **cochables**, en-têtes et séparateurs exclus.
    private static List<String> cochables(MenuButton bouton) {
        return bouton.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(MenuItem::getText)
                .toList();
    }

    private static MenuButton menuBoutonDe(FlowPane puces, int indexPuce) {
        HBox puce = (HBox) puces.getChildren().get(indexPuce);
        return (MenuButton) puce.getChildren().get(1);
    }

    /// La case à cocher d'un lieu, retrouvée par son libellé exact dans le menu de la puce Lieu.
    private static CheckMenuItem coche(MenuButton bouton, String texte) {
        return bouton.getItems().stream()
                .filter(item -> item instanceof CheckMenuItem && texte.equals(item.getText()))
                .map(CheckMenuItem.class::cast)
                .findFirst()
                .orElseThrow();
    }

    /// Le bouton ✕ est toujours le **dernier** enfant de la puce (après un éventuel éditeur).
    private static Button boutonRetirerDe(FlowPane puces) {
        HBox puce = (HBox) puces.getChildren().get(0);
        return (Button) puce.getChildren().get(puce.getChildren().size() - 1);
    }

    /// L'éditeur d'un critère à liste déroulante est le 2e enfant de sa puce (Label, ComboBox, bouton ✕).
    @SuppressWarnings("unchecked")
    private static ComboBox<Object> comboDe(FlowPane puces) {
        HBox puce = (HBox) puces.getChildren().get(0);
        return (ComboBox<Object>) puce.getChildren().get(1);
    }

    /// L'éditeur du critère Proba est un HBox (curseur + valeur), 2e enfant de la puce ; le curseur en tête.
    private static Slider sliderDe(FlowPane puces) {
        HBox puce = (HBox) puces.getChildren().get(0);
        HBox editeur = (HBox) puce.getChildren().get(1);
        return (Slider) editeur.getChildren().get(0);
    }

    /// L'éditeur du critère Heure est un HBox [Label « de », ComboBox de, Label « à », ComboBox à] : le
    /// combo « de » est en position 1, le combo « à » en position 3.
    @SuppressWarnings("unchecked")
    private static ComboBox<Integer> comboHeure(FlowPane puces, int position) {
        HBox puce = (HBox) puces.getChildren().get(0);
        HBox editeur = (HBox) puce.getChildren().get(1);
        return (ComboBox<Integer>) editeur.getChildren().get(position);
    }

    private static LigneObservationAudio ligne(long id, String taxon, String fichier, StatutObservation statut) {
        return ligneGroupe(id, taxon, fichier, statut, "Chiroptères");
    }

    /// Variante fixant le **groupe taxon parent** (pour le critère Groupe) : Chiroptères, Oiseaux…
    @Test
    @DisplayName("#3095 : la puce Lieu n'offre que les lieux que les AUTRES critères laissent passer")
    void le_domaine_de_lieu_cascade_sur_le_groupe() {
        // Le câblage réel de l'écran (`FiltresVuesAudio`) : chaque domaine se calcule via
        // `saufLui`. Ici on le reproduit sur deux critères pour éprouver la cascade elle-même.
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligneAuCarre(1, "Pippip", "Chiroptères", "640380"), ligneAuCarre(2, "Turmer", "Oiseaux", "870150"));
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<LigneObservationAudio> local = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(
                        CriteresAudio.groupe(() -> filtresLocaux.saufLui(ClesCriteres.GROUPE)),
                        CriteresAudio.lieu(() -> filtresLocaux.saufLui(ClesCriteres.LIEU))),
                CriteresAudio.rechercheTexte());

        // La puce Lieu, seule, offre les lieux des deux lignes. Le carré porte son nom convivial (#3157) :
        // une entrée par carré, pas deux.
        local.poser(ClesCriteres.LIEU, List.of());
        MenuButton lieu = (MenuButton) pucesLocales.lookup(".critere-multiple");
        assertThat(entreesDe(lieu)).contains("640380 · Site", "870150 · Site");

        // 1. Le « tous sauf lui ». On coche un lieu : la table ne montre plus que lui, mais la puce doit
        // continuer d'offrir les AUTRES lieux. Lire la liste déjà filtrée la ferait s'auto-effondrer sur
        // le seul choix déjà fait, et l'on ne pourrait jamais en cocher un second.
        //
        // La valeur posée est le NUMÉRO NU, comme le transport « Voir sur la carte » l'envoie : le
        // rattrapage de #3158 la replace sur l'entrée qualifiée, un seul carré la reprenant.
        local.poser(ClesCriteres.LIEU, List.of("640380"));
        rouvrir(lieu);

        assertThat(entreesDe(lieu))
                .as("cocher 640380 ne doit pas retirer 870150 du menu : sinon la puce ne sait plus"
                        + " désigner qu'une seule valeur, celle déjà retenue")
                .contains("640380 · Site", "870150 · Site");

        // 2. Le cascadage proprement dit, sur un AUTRE critère. Isoler les chiroptères vide le carré
        // 870150 : le proposer ferait cliquer sur un choix qui ne ramène rien.
        local.poser(ClesCriteres.LIEU, List.of());
        local.poser(ClesCriteres.GROUPE, List.of("Chiroptères"));
        rouvrir(lieu);

        assertThat(entreesDe(lieu)).contains("640380 · Site").doesNotContain("870150 · Site");
    }

    /// Rejoue l'ouverture du menu, moment où le domaine se recalcule (#3095).
    private static void rouvrir(MenuButton bouton) {
        bouton.getOnShowing().handle(new javafx.event.Event(javafx.event.Event.ANY));
    }

    private static List<String> entreesDe(MenuButton bouton) {
        return bouton.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(MenuItem::getText)
                .toList();
    }

    /// Une ligne dont le **carré** et le **groupe** varient : les deux dimensions que la cascade doit
    /// croiser. Calquée sur [#ligneGroupe], dont elle ne diffère que par le carré.
    private static LigneObservationAudio ligneAuCarre(long id, String taxon, String groupe, String carre) {
        return new LigneObservationAudio(
                id,
                10 + id,
                7L,
                1,
                "2026-06-20",
                carre,
                "A1",
                "Site",
                taxon,
                0.9,
                null,
                null,
                StatutObservation.VALIDEE,
                false,
                null,
                45,
                null,
                taxon,
                null,
                groupe,
                "PaRec_" + id + ".wav",
                0.2,
                0.4,
                null,
                false,
                null,
                null,
                null,
                null,
                0,
                null);
    }

    private static LigneObservationAudio ligneGroupe(
            long id, String taxon, String fichier, StatutObservation statut, String groupe) {
        return new LigneObservationAudio(
                id,
                10 + id,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Site",
                taxon,
                0.9,
                null,
                null,
                statut,
                false,
                null,
                45,
                null,
                taxon,
                null,
                groupe,
                fichier,
                0.2,
                0.4,
                null,
                false,
                null,
                null,
                null,
                null,
                0,
                null);
    }

    /// Observation portant des certitudes **croisées** : celle de l'observateur et celle du validateur
    /// différentes, pour qu'un filtre visant le mauvais champ se voie (#3336).
    private static LigneObservationAudio ligneCertitude(long id, Certitude observateur, Certitude validateur) {
        return new LigneObservationAudio(
                id,
                10 + id,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Site",
                "Pippip",
                0.9,
                null,
                null,
                StatutObservation.VALIDEE,
                false,
                null,
                45,
                null,
                "Pippip",
                null,
                "Chiroptères",
                "PaRec_" + id + ".wav",
                0.2,
                0.4,
                null,
                false,
                observateur,
                "Pippip",
                validateur,
                null,
                0,
                null);
    }

    /// Observation **localisée** (commune du lot 0 + carré/point/site), pour le critère Lieu (#2794) et
    /// la recherche texte géographique.
    private static LigneObservationAudio ligneLieu(
            long id, String taxon, String commune, String carre, String point, String site) {
        return new LigneObservationAudio(
                id,
                10 + id,
                7L,
                1,
                "2026-06-20",
                carre,
                point,
                site,
                taxon,
                0.9,
                null,
                null,
                StatutObservation.VALIDEE,
                false,
                null,
                45,
                null,
                taxon,
                null,
                "Chiroptères",
                "f" + id + ".wav",
                0.2,
                0.4,
                null,
                false,
                null,
                null,
                null,
                null,
                0,
                commune);
    }

    /// Observation **corrigée** : Tadarida a proposé `taxonTadarida`, l'observateur a retenu le taxon
    /// `codeObservateur` (vernaculaire `nomEspece`). Sert à vérifier que la recherche trouve l'espèce
    /// **retenue** (côté observateur), pas seulement la proposition Tadarida.
    private static LigneObservationAudio ligneCorrigee(
            long id, String taxonTadarida, String codeObservateur, String nomEspece, String fichier) {
        return new LigneObservationAudio(
                id,
                10 + id,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Site",
                taxonTadarida,
                0.9,
                codeObservateur,
                0.95,
                StatutObservation.CORRIGEE,
                false,
                null,
                45,
                nomEspece,
                taxonTadarida,
                null,
                "Chiroptères",
                fichier,
                0.2,
                0.4,
                null,
                false,
                null,
                null,
                null,
                null,
                0,
                null);
    }

    /// Observation portant une **probabilité Tadarida** donnée (ou `null` = sans proba), pour le critère Proba.
    private static LigneObservationAudio ligneProba(long id, String taxon, Double proba) {
        return new LigneObservationAudio(
                id,
                10 + id,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Site",
                taxon,
                proba,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                45,
                null,
                taxon,
                null,
                "Chiroptères",
                "f" + id + ".wav",
                0.2,
                0.4,
                null,
                false,
                null,
                null,
                null,
                null,
                0,
                null);
    }

    /// Observation **archivée en référence** (`is_reference` = true), pour le critère Références.
    private static LigneObservationAudio ligneReference(long id, String taxon, String fichier) {
        return new LigneObservationAudio(
                id,
                10 + id,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Site",
                taxon,
                0.9,
                null,
                null,
                StatutObservation.VALIDEE,
                true,
                null,
                45,
                null,
                taxon,
                null,
                "Chiroptères",
                fichier,
                0.2,
                0.4,
                null,
                false,
                null,
                null,
                null,
                null,
                0,
                null);
    }

    /// Observation portant un **instant de capture** donné (ou `null` = sans heure), pour le critère Heure.
    private static LigneObservationAudio ligneHeure(long id, LocalDateTime heure) {
        return new LigneObservationAudio(
                id,
                10 + id,
                7L,
                1,
                "2026-04-22",
                "640380",
                "A1",
                "Site",
                "Pippip",
                0.9,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                45,
                null,
                "Pippip",
                null,
                "Chiroptères",
                "f" + id + ".wav",
                0.2,
                0.4,
                heure,
                false,
                null,
                null,
                null,
                null,
                0,
                null);
    }
}

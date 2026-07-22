package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.commun.model.CompteurValidations;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ModeRebranchement;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le **geste** de réactivation, cliqué pour de vrai (#1431, suite de #1302). Depuis #1780, il **ouvre la
/// modale** de réactivation (deux barres, phases distinctes) plutôt que la modale de progression générique :
/// ce test vérifie donc que le clic ouvre bien la modale avec le **dossier désigné** et un travail qui
/// **délègue** au service pour le passage courant. Le contenu de la modale (compte rendu, phases, erreurs)
/// est couvert par [ReactivationModaleViewTest].
///
/// La désignation du dossier passe par [SelecteurFichier], porté par l'écran : sans ce point de
/// remplacement, le clic ouvrirait un `DirectoryChooser` natif et le test ne reviendrait jamais.
///
/// La fixture est un passage dont les **séquences sont importées mais l'audio absent du disque** (archivé,
/// ou fichiers effacés) : c'est exactement l'état où la réactivation s'ouvre.
@ExtendWith(ApplicationExtension.class)
class PassageReactivationViewTest {

    private static final long ID_PASSAGE = 42L;
    private static final Path DOSSIER = Path.of("/media/carte-sd/Car640380");

    /// Titres des sélecteurs réellement ouverts (vide = l'action n'a même pas demandé de dossier).
    private final List<String> demandes = new ArrayList<>();

    /// Ce que le double de sélection répondra : `Optional.empty()` = l'utilisateur a **annulé**.
    private Optional<Path> choix = Optional.of(DOSSIER);

    /// Les questions posées avant le rebranchement (#2255), capturées pour être vérifiées.
    private final List<String> questions = new ArrayList<>();

    /// Réponse du double : « non » par défaut, soit le geste historique (copier). Les tests qui
    /// veulent le mode référence la basculent explicitement.
    private boolean laisserSurPlace;

    private ServiceReactivationPassage reactivation;
    private NavigationPassage navigation;

    @Start
    void start(Stage stage) throws Exception {
        ServicePassage service = mock(ServicePassage.class);
        reactivation = mock(ServiceReactivationPassage.class);
        navigation = mock(NavigationPassage.class);
        // Séquences importées (30) mais aucune présente sur le disque : la réactivation est ouverte.
        when(service.detailPassage(anyLong()))
                .thenReturn(new DetailPassage(
                        2,
                        2026,
                        "2026-06-22",
                        "20:25:00",
                        "07:47:00",
                        "1925492",
                        StatutWorkflow.DEPOSE,
                        Verdict.OK,
                        null,
                        0L,
                        0L,
                        30,
                        150.0,
                        null,
                        new DecompteAudio(0, 30)));

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(NavigationPassage.class).toInstance(navigation);
                OptionalBinder.newOptionalBinder(binder(), OuvrirDiagnostic.class)
                        .setBinding()
                        .toInstance(passage -> {});
                OptionalBinder.newOptionalBinder(binder(), OuvrirVerification.class)
                        .setBinding()
                        .toInstance(passage -> {});
                OptionalBinder.newOptionalBinder(binder(), OuvrirLot.class)
                        .setBinding()
                        .toInstance(passage -> {});
            }

            @Provides
            PassageViewModel viewModel() {
                return new PassageViewModel(service, reactivation);
            }

            @Provides
            OuvrirValidation ouvrirValidation() {
                return passage -> {};
            }

            @Provides
            OuvrirMultisite ouvrirMultisite() {
                return carre -> {};
            }

            @Provides
            OuvrirSite ouvrirSite() {
                return new OuvrirSite() {
                    @Override
                    public void ouvrirListe() {}

                    @Override
                    public void ouvrirDetail(String numeroCarre) {}
                };
            }

            @Provides
            CompteurValidations compteurValidations() {
                return idPassage -> 0;
            }

            @Provides
            PortailVigieChiro portail() {
                return mock(PortailVigieChiro.class);
            }

            @Provides
            OuvreurDeLien ouvreurDeLien() {
                return url -> {};
            }
        });
        FXMLLoader loader = new FXMLLoader(PassageController.class.getResource("Passage.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        PassageController controleur = loader.getController();
        // Sans ce double de désignation, le clic ouvrirait un DirectoryChooser natif et le test figerait.
        controleur.selecteur().definir(new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                demandes.add(titre);
                return choix;
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                throw new AssertionError("la réactivation demande un dossier, pas un fichier");
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                throw new AssertionError("la réactivation lit un dossier, elle n'écrit aucun fichier");
            }
        });
        // Même raison que ci-dessus : la question « laisser les fichiers où ils sont ? » (#2255) passe
        // par un dialogue qui figerait le test. Le double la capture, ce qui la rend vérifiable.
        controleur.confirmateur().definir(question -> {
            questions.add(question);
            return laisserSurPlace;
        });
        controleur.ouvrirSur(ID_PASSAGE, new ContexteSite("640380", "A1", "Étang de la Tuilière"));
        stage.setScene(new Scene(vue, 1100, 700));
        stage.show();
    }

    private void cliquerReactiver(FxRobot robot) {
        robot.interact(() -> robot.lookup("#boutonReactiver").queryButton().fire());
    }

    @Test
    @DisplayName("#1780 : le geste ouvre la modale avec le dossier choisi, et le travail délègue au service")
    void geste_ouvre_la_modale_avec_le_dossier(FxRobot robot) {
        Button reactiver = robot.lookup("#boutonReactiver").queryAs(Button.class);
        assertThat(reactiver.isDisabled())
                .as("séquences importées, audio absent du disque : le geste est ouvert")
                .isFalse();

        // Sans ce stub, le mock répondrait « false » et l'on testerait la formulation courte en croyant
        // tester l'autre : le dossier désigné ici est CELUI DE L'UTILISATEUR (NAS, disque externe).
        when(reactivation.horsEspaceDeTravail(DOSSIER)).thenReturn(true);

        cliquerReactiver(robot);

        assertThat(demandes).containsExactly("Dossier des fichiers d'origine à réimporter");
        ArgumentCaptor<ReactivationModaleController.Travail> travail =
                ArgumentCaptor.forClass(ReactivationModaleController.Travail.class);
        verify(navigation).ouvrirModaleReactivation(any(), travail.capture(), any());
        // Le travail confié à la modale réactive le PASSAGE COURANT depuis le DOSSIER DÉSIGNÉ.
        travail.getValue().executer(point -> {}, point -> {}, new JetonAnnulation());
        verify(reactivation).reactiver(eq(ID_PASSAGE), eq(DOSSIER), eq(ModeRebranchement.COPIE), any(), any(), any());
        assertThat(questions)
                .as("#2255 : le choix est proposé, et sa conséquence dite avant de choisir")
                .singleElement(as(STRING))
                .contains("Les laisser où ils sont")
                .contains("plus écoutable si ce support n'est pas accessible");
    }

    @Test
    @DisplayName("#2255 : répondre « oui » laisse l'audio où il est - la base suivra, rien ne sera copié")
    void repondre_oui_reactive_par_reference(FxRobot robot) {
        laisserSurPlace = true;

        cliquerReactiver(robot);

        ArgumentCaptor<ReactivationModaleController.Travail> travail =
                ArgumentCaptor.forClass(ReactivationModaleController.Travail.class);
        verify(navigation).ouvrirModaleReactivation(any(), travail.capture(), any());
        travail.getValue().executer(point -> {}, point -> {}, new JetonAnnulation());
        verify(reactivation)
                .reactiver(eq(ID_PASSAGE), eq(DOSSIER), eq(ModeRebranchement.REFERENCE), any(), any(), any());
    }

    @Test
    @DisplayName("#1431 : sélecteur annulé : la modale ne s'ouvre pas, rien n'est réactivé")
    void selecteur_annule_n_ouvre_pas_la_modale(FxRobot robot) {
        choix = Optional.empty();

        cliquerReactiver(robot);

        verify(navigation, never()).ouvrirModaleReactivation(any(), any(), any());
    }
}

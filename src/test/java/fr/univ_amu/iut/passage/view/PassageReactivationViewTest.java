package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.NiveauNotification;
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
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.ServiceArchivagePassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import fr.univ_amu.iut.passage.model.VoieReactivation;
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
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le **geste** de réactivation, cliqué pour de vrai (#1431, suite de #1302).
///
/// Ce geste était resté hors de portée alors même que son compte rendu passait déjà par le port
/// `Notificateur` (#1405). La raison est celle qui a structuré tout le chantier : **une action ne
/// devient testable que si tous ses dialogues sont remplaçables**. Ici il en restait un, et c'était le
/// **premier** - un `DirectoryChooser` natif, qui **ouvre** l'action. Le test s'arrêtait donc à la
/// première ligne, et « Réactiver ce passage » ne se vérifiait que par le grisage de son bouton.
///
/// La désignation du dossier passe maintenant par [SelecteurFichier], porté par l'écran.
///
/// La fixture est un passage dont les **séquences sont importées mais l'audio absent du disque**
/// (archivé, ou fichiers effacés) : c'est exactement l'état où la réactivation s'ouvre.
@ExtendWith(ApplicationExtension.class)
class PassageReactivationViewTest {

    private static final long ID_PASSAGE = 42L;
    private static final Path DOSSIER = Path.of("/media/carte-sd/Car640380");

    /// Ce que le notificateur a **dit**, au lieu de l'afficher.
    private final List<String> annonces = new ArrayList<>();

    private final List<NiveauNotification> niveaux = new ArrayList<>();

    /// Titres des sélecteurs réellement ouverts (vide = l'action n'a même pas demandé de dossier).
    private final List<String> demandes = new ArrayList<>();

    /// Ce que le double de sélection répondra : `Optional.empty()` = l'utilisateur a **annulé**.
    private Optional<Path> choix = Optional.of(DOSSIER);

    private ServiceReactivationPassage reactivation;

    @Start
    void start(Stage stage) throws Exception {
        ServicePassage service = mock(ServicePassage.class);
        reactivation = mock(ServiceReactivationPassage.class);
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
        ServicePurgeOriginaux purge = mock(ServicePurgeOriginaux.class);
        ServiceArchivagePassage archivage = mock(ServiceArchivagePassage.class);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
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
                return new PassageViewModel(service, purge, archivage, reactivation, Optional.empty());
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
        // Les deux dialogues du geste : la désignation du dossier, et le compte rendu. Sans le premier,
        // le clic ouvrirait un DirectoryChooser natif et le test ne reviendrait jamais.
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
        controleur.notificateur().definir((niveau, entete, message) -> {
            niveaux.add(niveau);
            annonces.add(entete + " | " + message);
        });
        controleur.ouvrirSur(ID_PASSAGE, new ContexteSite("640380", "A1", "Étang de la Tuilière"));
        stage.setScene(new Scene(vue, 1100, 700));
        stage.show();
    }

    private void cliquerReactiver(FxRobot robot) {
        robot.interact(() -> robot.lookup("#boutonReactiver").queryButton().fire());
    }

    @Test
    @DisplayName("#1431 : le dossier désigné est réactivé, et le rapport dit sur quelle preuve")
    void reactivation_rebranche_et_rend_compte(FxRobot robot) {
        when(reactivation.reactiver(anyLong(), any(), any(), any()))
                .thenReturn(new RapportReactivation(
                        28,
                        0,
                        2,
                        0,
                        NiveauConfiance.CERTITUDE,
                        List.of(),
                        new DecompteAudio(28, 30),
                        VoieReactivation.TRANSFORMES));

        Button reactiver = robot.lookup("#boutonReactiver").queryAs(Button.class);
        assertThat(reactiver.isDisabled())
                .as("séquences importées, audio absent du disque : le geste est ouvert")
                .isFalse();
        cliquerReactiver(robot);

        assertThat(demandes).containsExactly("Dossier des fichiers d'origine à réimporter");
        verify(reactivation).reactiver(anyLong(), any(), any(), any());
        assertThat(niveaux).containsExactly(NiveauNotification.INFORMATION);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce -> assertThat(annonce).contains("28"));
    }

    @Test
    @DisplayName("#1431 : sélecteur annulé : rien n'est réactivé, rien n'est annoncé")
    void selecteur_annule_ne_reactive_rien(FxRobot robot) {
        choix = Optional.empty();

        cliquerReactiver(robot);

        verify(reactivation, never()).reactiver(anyLong(), any(), any(), any());
        assertThat(annonces).as("renoncer n'est pas un événement").isEmpty();
    }

    @Test
    @DisplayName("#1431 : des séquences refusées : c'est un AVERTISSEMENT, pas une réussite")
    void sequences_refusees_avertissent(FxRobot robot) {
        // Un fichier homonyme au contenu différent n'est jamais rebranché en silence (#1309) : le rapport
        // doit le dire, et il ne doit pas ressembler à un succès.
        when(reactivation.reactiver(anyLong(), any(), any(), any()))
                .thenReturn(new RapportReactivation(
                        20,
                        10,
                        0,
                        0,
                        NiveauConfiance.FORTE,
                        List.of(),
                        new DecompteAudio(20, 30),
                        VoieReactivation.TRANSFORMES));

        cliquerReactiver(robot);

        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
    }

    @Test
    @DisplayName("#1431 : dossier illisible : l'utilisateur est averti, l'écran ne bouge pas")
    void echec_est_annonce(FxRobot robot) {
        when(reactivation.reactiver(anyLong(), any(), any(), any()))
                .thenThrow(new IllegalStateException("dossier illisible"));

        cliquerReactiver(robot);

        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce ->
                        assertThat(annonce).contains("Réactivation impossible").contains("dossier illisible"));
    }
}

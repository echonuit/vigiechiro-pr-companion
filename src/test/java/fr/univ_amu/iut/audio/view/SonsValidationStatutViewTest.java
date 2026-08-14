package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.DiscussionValidateur;
import fr.univ_amu.iut.audio.viewmodel.ExporteurAudio;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.ExportObservationsEtSons;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// La barre de statut de la **vue audio** dit sur quoi l'écran est ouvert, y compris quand l'ouverture
/// échoue (#3752).
///
/// `ChromeAudio.zonesStatut` rend `ZonesStatut.VIDE` tant que la source est nulle - et elle l'est au
/// `bind()`, qui évalue le calcul tout de suite. Les trois zones sont alors vides, et le chrome **retire
/// la barre du layout** (`BarreStatut` lie `visible` et `managed` à « une zone au moins est remplie »).
///
/// Le calcul lit trois choses et le binding n'en déclarait qu'une : le champ `source` et la liste
/// `observationsFiltrees()` n'y figuraient pas. Sur le chemin d'erreur, `signalerErreur` repose
/// `ComptageAudio.VIDE`, la **même instance constante** : `ObjectPropertyBase.set` compare par
/// référence, rien ne s'invalide, et la barre ne revient jamais.
@ExtendWith(ApplicationExtension.class)
class SonsValidationStatutViewTest {

    private static final SourceObservations SOURCE = new SourceObservations.References("u-1");

    @TempDir
    Path dossierReglages;

    private ServiceValidation service;
    private ProjectionsAudioDao projections;
    private SonsValidationController controleur;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceValidation.class);
        projections = mock(ProjectionsAudioDao.class);
        ServiceBibliotheque bibliotheque = mock(ServiceBibliotheque.class);
        // Chemin d'erreur par défaut : le chargement d'ouverture lève dès son premier appel.
        doThrow(new IllegalStateException("Sons de référence indisponibles"))
                .when(service)
                .taxonsDisponibles();
        when(service.cheminAudio(anyLong())).thenReturn(Optional.empty());
        DepotVues depotVues = mock(DepotVues.class);
        when(depotVues.findByFeature("audio")).thenReturn(List.of());

        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    EspecesPrioritaires especesPrioritaires() {
                        return () -> Set.of("Pippip");
                    }

                    @Provides
                    AudioViewModel viewModel() {
                        return new AudioViewModel(
                                service,
                                projections,
                                mock(PlageNuitPassage.class),
                                mock(ValidationManuelle.class),
                                mock(MarquageDouteux.class),
                                mock(SaisieCertitude.class),
                                mock(RevueEnLot.class),
                                new ExporteurAudio(
                                        service,
                                        bibliotheque,
                                        new ExportObservationsEtSons(mock(SequenceDao.class), mock(SessionDao.class))),
                                mock(ServiceDisponibiliteAudio.class),
                                chemin -> true,
                                mock(DiscussionValidateur.class));
                    }

                    @Provides
                    DepotVues depotVues() {
                        return depotVues;
                    }

                    @Provides
                    ImportVigieChiroViewModel importVigieChiro() {
                        return new ImportVigieChiroViewModel(Optional.empty());
                    }

                    @Provides
                    PublicationCorrectionsViewModel publicationCorrections() {
                        return new PublicationCorrectionsViewModel(Optional.empty());
                    }

                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return url -> {};
                    }

                    @Provides
                    PortailVigieChiro portail() {
                        return mock(PortailVigieChiro.class);
                    }

                    // Base de réglages jetable et migrée : sans elle, la lecture des options de lecture échoue.
                    @Provides
                    ReglagesReactifs reglagesReactifs() {
                        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossierReglages));
                        new MigrationSchema(source).migrer();
                        return new ReglagesReactifs(new Reglages(new ReglagesDao(source)));
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        // On n'ouvre pas ici : chaque test choisit son chemin.
        stage.setScene(new Scene(vue, 1000, 700));
        stage.show();
    }

    @Test
    @DisplayName("#3752 : l'ouverture échoue, la barre de statut dit quand même sur quoi on est")
    void la_barre_dit_sur_quoi_on_est_apres_une_erreur(FxRobot robot) {
        robot.interact(() -> controleur.ouvrirSur(SOURCE));

        assertThat(controleur.zonesStatutProperty().get().gauche())
                .as("la source doit être une dépendance DÉCLARÉE du binding")
                .isEqualTo("Sons de référence");

        // Ce que l'utilisateur perd vraiment : `BarreStatut` lie `visible` ET `managed` du conteneur à
        // « une zone au moins est remplie ». Trois zones vides ne font pas une barre pâle, elles la
        // retirent du layout - constat de l'artéfact de clôture du lot 3, que l'assertion précédente,
        // posée sur une seule zone, ne dit pas.
        assertThat(controleur.zonesStatutProperty().get().estVide())
                .as("trois zones vides retirent la barre de statut de la fenêtre")
                .isFalse();
    }

    @Test
    @DisplayName("Témoin : sur le chemin de succès, le dispositif voit bien la zone gauche")
    void temoin_le_dispositif_voit_la_zone_gauche(FxRobot robot) {
        doReturn(List.of()).when(service).taxonsDisponibles();
        when(projections.lignesAudioReferences(anyString())).thenReturn(List.of());
        when(service.publicationImpossible(any())).thenReturn(false);

        robot.interact(() -> controleur.ouvrirSur(SOURCE));

        assertThat(controleur.zonesStatutProperty().get().gauche()).isEqualTo("Sons de référence");
    }
}

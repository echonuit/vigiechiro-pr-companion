package fr.univ_amu.iut.analyse.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.analyse.model.ServiceSynthese;
import fr.univ_amu.iut.analyse.view.SyntheseController;
import fr.univ_amu.iut.analyse.viewmodel.SyntheseViewModel;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.ReferentielActivite;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Aperçu de l'écran **Synthèse de la nuit** quand le référentiel d'activité est **absent**.
///
/// Cet état était le seul que rien n'exerçait, et le seul où l'écran ne s'ouvrait pas : le libellé du
/// référentiel, lié puis réécrit, levait `A bound value cannot be set`. Le rendre en image est ce qui
/// prouve qu'il s'affiche désormais, et qu'il s'affiche **utilement** : les colonnes qu'on ne peut plus
/// fonder sont retirées, l'écran dit pourquoi, et les comptages restent entiers.
///
/// ## Ce qui est substitué, et ce qui ne l'est plus (#3018)
///
/// Cet outil remplaçait autrefois le `ServiceSynthese` **entier** par une sous-classe anonyme à lignes
/// fixes. Il ne reste qu'une substitution, et elle porte sur ce que l'aperçu doit précisément montrer :
/// le **référentiel est vide**.
///
/// Les espèces, les contacts et les fichiers viennent maintenant de la nuit semée par [CaptureSynthese] -
/// même nuit, même agrégation, seul le référentiel change. Les deux aperçus se comparent donc ligne à
/// ligne, ce qui est tout l'intérêt : on voit ce que l'absence de référentiel retire, et rien d'autre.
///
/// Le référentiel devient un **collaborateur** de `ServiceSynthese`, pour que cet état soit atteignable
/// autrement qu'en supprimant une ressource du jar.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureSyntheseSansReferentiel {

    private CaptureSyntheseSansReferentiel() {}

    public static void main() throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException | SQLException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        if (erreur.get() != null) {
            erreur.get().printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    /// Injecteur de cet outil : la composition **complète** de l'application, surchargée par des
    /// exécuteurs synchrones et un référentiel vide. Exposé pour le garde-fou de câblage
    /// (`CablageInjecteursCaptureTest`), qui construit chaque injecteur sans rendre de PNG.
    public static Injector creerInjecteur() {
        return Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(ModuleCaptureCommun.executeursSynchrones(), new ModuleSansReferentiel()));
    }

    private static void capturer() throws IOException, SQLException {
        Path workspace = Files.createTempDirectory("vc-capture-synthese-sans-ref");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        injecteur.getInstance(MigrationSchema.class).migrer();
        long idPassage = CaptureSynthese.semerLaNuit(source);

        FXMLLoader loader = ChargeurFxml.chargeur(SyntheseController.class, "Synthese.fxml");
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        SyntheseController controleur = loader.getController();
        controleur.ouvrirSur(new ContextePassage(
                idPassage, 3, new ContexteSite(CaptureSynthese.CARRE, CaptureSynthese.POINT, CaptureSynthese.SITE)));
        Path fichier = sortie.resolve("apercu-synthese-sans-referentiel.png");
        ApercuFx.enregistrerPng(new Scene(vue, 1180, 700), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// La seule substitution : un référentiel **vide**, celui que l'aperçu doit montrer.
    private static final class ModuleSansReferentiel extends AbstractModule {

        @Provides
        SyntheseViewModel viewModel(ProjectionsAudioDao projections) {
            return new SyntheseViewModel(new ServiceSynthese(projections, referentielVide()));
        }

        /// Espèces prioritaires, identiques à [CaptureSynthese] : les deux aperçus doivent se comparer
        /// sans qu'un bouclier apparaisse ou disparaisse pour une autre raison que le référentiel.
        @Provides
        EspecesPrioritaires especesPrioritaires() {
            return () -> Set.of("Pippip");
        }

        /// Un référentiel lu depuis une source **sans aucune ligne** : `taille()` vaut zéro, et c'est
        /// exactement ce que `referentielDisponible()` interroge.
        private static ReferentielActivite referentielVide() {
            try {
                return ReferentielActivite.lire(new StringReader(""));
            } catch (IOException impossible) {
                throw new UncheckedIOException("Lecture d'un referentiel vide", impossible);
            }
        }
    }
}

package fr.univ_amu.iut.analyse.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.analyse.model.LigneSynthese;
import fr.univ_amu.iut.analyse.model.ServiceSynthese;
import fr.univ_amu.iut.analyse.view.SyntheseController;
import fr.univ_amu.iut.analyse.viewmodel.SyntheseViewModel;
import fr.univ_amu.iut.commun.model.ContexteActivite;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
public final class CaptureSyntheseSansReferentiel {

    private CaptureSyntheseSansReferentiel() {}

    public static void main() throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException probleme) {
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

    /// Injecteur (partiel) de cet outil. Exposé pour le garde-fou de câblage
    /// (`CablageInjecteursCaptureTest`), qui construit chaque injecteur sans rendre de PNG.
    public static Injector creerInjecteur() {
        return Guice.createInjector(new ModuleDemo());
    }

    private static void capturer() throws IOException {
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));
        Injector injecteur = creerInjecteur();
        FXMLLoader loader = ChargeurFxml.chargeur(SyntheseController.class, "Synthese.fxml");
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        SyntheseController controleur = loader.getController();
        controleur.ouvrirSur(new ContextePassage(1L, 3, new ContexteSite("640380", "A1", "Étang de Biguglia")));
        Path fichier = sortie.resolve("apercu-synthese-sans-referentiel.png");
        ApercuFx.enregistrerPng(new Scene(vue, 1100, 700), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    private static final String CHIROPTERES = "Chiroptères";

    /// Les mêmes espèces que l'aperçu nominal, **sans aucune classe** : c'est tout l'intérêt de l'image.
    private static List<LigneSynthese> lignesDemo() {
        return List.of(
                ligne("Pipkuh", "Pipistrelle de Kuhl", CHIROPTERES, 718, 402),
                ligne("Pippip", "Pipistrelle commune", CHIROPTERES, 159, 96),
                ligne("Barbar", "Barbastelle d'Europe", CHIROPTERES, 39, 31),
                ligne("Tetvir", "Grande sauterelle verte", "Orthoptères et cigales", 244, 88));
    }

    private static LigneSynthese ligne(String code, String nom, String groupe, int contacts, int fichiers) {
        return new LigneSynthese(code, nom, groupe, contacts, fichiers, Optional.empty(), Optional.empty(), false);
    }

    private static final class ModuleDemo extends AbstractModule {

        @Provides
        SyntheseViewModel viewModel() {
            return new SyntheseViewModel(serviceDemo());
        }

        @Provides
        OuvrirSite ouvrirSite() {
            return new OuvrirSite() {
                @Override
                public void ouvrirListe() {
                    // Inerte : la capture est rendue hors-chrome.
                }

                @Override
                public void ouvrirDetail(String numeroCarre) {
                    // Inerte.
                }
            };
        }

        @Provides
        OuvrirPassage ouvrirPassage() {
            return (idPassage, contexte) -> {
                // Inerte.
            };
        }

        /// Le référentiel d'ACTIVITÉ manque, pas celui de CONSERVATION : deux sources distinctes, et
        /// l'écran doit continuer à marquer les espèces prioritaires. L'image le montre.
        @Provides
        EspecesPrioritaires especesPrioritaires() {
            return () -> Set.of("Pippip");
        }

        private static ServiceSynthese serviceDemo() {
            return new ServiceSynthese(new ProjectionsAudioDao(null)) {
                @Override
                public List<LigneSynthese> pour(
                        long idPassage, boolean validesSeulement, String numeroCarre, String milieu) {
                    return lignesDemo();
                }

                @Override
                public ContexteActivite contexte(long idPassage, String numeroCarre, String milieu) {
                    return ContexteActivite.NATIONAL;
                }

                @Override
                public List<String> milieuxDisponibles() {
                    return List.of();
                }

                @Override
                public boolean referentielDisponible() {
                    return false;
                }
            };
        }
    }
}

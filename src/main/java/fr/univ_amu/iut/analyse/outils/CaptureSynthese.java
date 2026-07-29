package fr.univ_amu.iut.analyse.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.analyse.model.LigneSynthese;
import fr.univ_amu.iut.analyse.model.ServiceSynthese;
import fr.univ_amu.iut.analyse.view.SyntheseController;
import fr.univ_amu.iut.analyse.viewmodel.SyntheseViewModel;
import fr.univ_amu.iut.commun.model.ClasseActivite;
import fr.univ_amu.iut.commun.model.ConfianceReferentiel;
import fr.univ_amu.iut.commun.model.ContexteActivite;
import fr.univ_amu.iut.commun.model.SaisonActivite;
import fr.univ_amu.iut.commun.model.SeuilsActivite;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Rend l'écran **Synthèse de la nuit** (#2351) hors écran en PNG. La nuit de démonstration montre ce
/// que le lot apporte : une classe d'activité par espèce, **ses quantiles à côté**, une mention
/// *(indicatif)* sur une déclinaison peu fiable, et un orthoptère qui **dit** qu'il n'est pas couvert
/// plutôt que de laisser une cellule vide.
///
/// Les codes de taxon suivent la **casse du référentiel** (`Pipkuh`, et non `PIPKUH`) : c'est le piège
/// relevé à la clôture du lot #2353, où une démo en majuscules montrait un écran sans son repère.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureSynthese {

    private CaptureSynthese() {}

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

    private static void capturer() throws IOException {
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));
        Injector injecteur = creerInjecteur();
        FXMLLoader loader = ChargeurFxml.chargeur(SyntheseController.class, "Synthese.fxml");
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        SyntheseController controleur = loader.getController();
        controleur.ouvrirSur(new ContextePassage(1L, 3, new ContexteSite("640380", "A1", "Étang de Biguglia")));
        Path fichier = sortie.resolve("apercu-synthese.png");
        // 1100 × 700 : à 620 px, le bloc de mise en garde débordait de quelques pixels et ApercuFx
        // refusait la capture (ADR 0042). Il avait raison — un avertissement tronqué ne prévient
        // personne, et c'est justement ce que cette capture doit montrer comme lisible.
        ApercuFx.enregistrerPng(new Scene(vue, 1100, 700), fichier);
        System.out.println("Apercu de la synthese ecrit dans " + fichier.toAbsolutePath());
    }

    /// Injecteur (partiel) de cet outil. Exposé pour le garde-fou de câblage
    /// (`CablageInjecteursCaptureTest`).
    public static Injector creerInjecteur() {
        return Guice.createInjector(new ModuleDemo());
    }

    /// Groupe des chiroptères, tel que le référentiel taxonomique le nomme.
    private static final String CHIROPTERES = "Chiroptères";

    /// Une nuit de démonstration qui montre **les quatre cas** que l'écran sait rendre.
    private static List<LigneSynthese> lignesDemo() {
        return List.of(
                ligne("Pipkuh", "Pipistrelle de Kuhl", CHIROPTERES, 718, 402, ClasseActivite.FORTE, false, true),
                ligne("Pippip", "Pipistrelle commune", CHIROPTERES, 159, 96, ClasseActivite.MOYENNE, false, true),
                // Déclinaison peu fiable : la classe est rendue, mais marquée indicative.
                ligne("Barbar", "Barbastelle d'Europe", CHIROPTERES, 39, 31, ClasseActivite.FAIBLE, true, true),
                // Hors référentiel : la cellule le DIT, plutôt que de rester vide.
                ligne("Tetvir", "Grande sauterelle verte", "Orthoptères et cigales", 244, 88, null, false, false));
    }

    private static LigneSynthese ligne(
            String code,
            String nom,
            String groupe,
            int contacts,
            int fichiers,
            ClasseActivite classe,
            boolean indicatif,
            boolean couvert) {
        SeuilsActivite seuils = new SeuilsActivite(
                12,
                480,
                1240,
                indicatif ? 14 : 8600,
                indicatif ? ConfianceReferentiel.FAIBLE : ConfianceReferentiel.TRES_BONNE,
                "region Corse",
                "ete");
        return new LigneSynthese(
                code,
                nom,
                groupe,
                contacts,
                fichiers,
                Optional.ofNullable(classe),
                classe == null ? Optional.empty() : Optional.of(seuils),
                couvert);
    }

    /// Module de démonstration : un [ServiceSynthese] à lignes fixes, sans base ni référentiel réel.
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

        /// Service à lignes fixes : les deux lectures sont surchargées, les DAO (nuls) ne sont jamais
        /// touchés. Un seul but, montrer un tableau déterministe sans base.
        private static ServiceSynthese serviceDemo() {
            // DAO à source nulle : jamais interrogé, les deux lectures étant surchargées. Le service
            // exige un DAO non nul — garde légitime, c'est à la démo de fournir de quoi la satisfaire.
            return new ServiceSynthese(new ProjectionsAudioDao(null)) {
                @Override
                public List<LigneSynthese> pour(
                        long idPassage, boolean validesSeulement, String numeroCarre, String milieu) {
                    return lignesDemo();
                }

                @Override
                public ContexteActivite contexte(long idPassage, String numeroCarre, String milieu) {
                    return new ContexteActivite(
                            Optional.of(SaisonActivite.ETE), Optional.of("Corse"), Optional.ofNullable(milieu));
                }

                @Override
                public List<String> milieuxDisponibles() {
                    return List.of("Agricole", "Foret", "Riviere", "Urbain");
                }

                @Override
                public boolean referentielDisponible() {
                    return true;
                }
            };
        }
    }
}

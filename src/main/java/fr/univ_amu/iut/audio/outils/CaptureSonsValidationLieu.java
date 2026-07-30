package fr.univ_amu.iut.audio.outils;

import com.google.inject.Injector;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture la **puce « Lieu »** de la barre de filtres (`apercu-sons-validation-lieu.png`, #2794,
/// EPIC #2790) : le critère est ajouté par le menu « + Filtre », puis la **commune** du point est
/// cochée dans sa liste. C'est le geste qui rend le scénario « cette espèce, sur cette commune »
/// jouable en direct, et la seule capture où la commune dérivée du GPS (#2791) se voit à l'écran.
///
/// Le seed et le rendu sont factorisés dans [GraineSonsValidation]. Un `main` distinct (donc une JVM
/// par PNG) car un seul état « audio chargé » est possible par processus.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureSonsValidationLieu {

    private CaptureSonsValidationLieu() {}

    public static void main(String[] args) throws InterruptedException {
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
        Injector injecteur = GraineSonsValidation.preparer();
        GraineSonsValidation.rendre(
                injecteur,
                GraineSonsValidation.dossierSortie().resolve("apercu-sons-validation-lieu.png"),
                1100,
                vue -> {
                    activerFiltreLieu(vue);
                    GraineSonsValidation.selectionner(vue, 0);
                });
    }

    /// Ajoute la puce **Lieu** par le menu « + Filtre », puis coche la **commune** dans sa liste : la
    /// puce affiche alors le lieu retenu, et la table ne montre plus que ses observations.
    private static void activerFiltreLieu(javafx.scene.Parent vue) {
        if (vue.lookup("#menuAjoutFiltre") instanceof MenuButton menu) {
            menu.getItems().stream()
                    .filter(item -> "Lieu".equals(item.getText()))
                    .findFirst()
                    .ifPresent(MenuItem::fire);
        }
        cocherCommune(vue);
    }

    /// Coche la commune dans le [MenuButton] de la puce (celui qui porte la classe `critere-multiple`).
    private static void cocherCommune(javafx.scene.Parent vue) {
        vue.lookupAll(".critere-multiple").stream()
                .filter(MenuButton.class::isInstance)
                .map(MenuButton.class::cast)
                .findFirst()
                .ifPresent(bouton -> bouton.getItems().stream()
                        .filter(item ->
                                item instanceof CheckMenuItem && GraineSonsValidation.COMMUNE.equals(item.getText()))
                        .map(CheckMenuItem.class::cast)
                        .findFirst()
                        .ifPresent(coche -> coche.setSelected(true)));
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return GraineSonsValidation.creerInjecteur();
    }
}

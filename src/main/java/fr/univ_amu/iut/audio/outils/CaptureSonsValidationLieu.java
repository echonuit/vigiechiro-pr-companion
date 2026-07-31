package fr.univ_amu.iut.audio.outils;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import java.io.IOException;
import java.util.List;
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
            ApercuFx.exigerParLibelle("le menu « + Filtre »", menu.getItems(), MenuItem::getText, "Lieu")
                    .fire();
        }
        cocherCommune(vue);
    }

    /// Coche la commune dans le [MenuButton] de la puce (celui qui porte la classe `critere-multiple`).
    private static void cocherCommune(javafx.scene.Parent vue) {
        MenuButton puce = vue.lookupAll(".critere-multiple").stream()
                .filter(MenuButton.class::isInstance)
                .map(MenuButton.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Puce « Lieu » absente après son ajout : la capture montrerait une table non filtrée."));
        List<CheckMenuItem> coches = puce.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .toList();
        ApercuFx.exigerParLibelle(
                        "la liste de la puce « Lieu »", coches, MenuItem::getText, GraineSonsValidation.COMMUNE)
                .setSelected(true);
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return GraineSonsValidation.creerInjecteur();
    }
}

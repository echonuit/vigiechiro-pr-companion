package fr.univ_amu.iut.audio.outils;

import com.google.inject.Injector;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture la **barre de filtres active** de la vue « Sons & validation » (`apercu-sons-validation-filtres.png`,
/// #470/#471/#512) : on ajoute la puce **Groupe** via le menu « + Filtre », présélectionnée sur
/// **Chiroptères** (les chauves-souris seulement). La table ne montre plus que le sous-ensemble filtré et les
/// **compteurs** de la barre de statut se recalculent dessus.
///
/// Le seed et le rendu sont factorisés dans [GraineSonsValidation]. Un `main` distinct (donc une JVM par PNG)
/// car un seul état « audio chargé » est possible par processus (cf. [GraineSonsValidation]).
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureSonsValidationFiltres {

    private CaptureSonsValidationFiltres() {}

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
                GraineSonsValidation.dossierSortie().resolve("apercu-sons-validation-filtres.png"),
                1100,
                CaptureSonsValidationFiltres::activerFiltreGroupe);
    }

    /// Ajoute la puce **Groupe** en actionnant l'entrée correspondante du menu « + Filtre » : la puce
    /// s'active sur Chiroptères par défaut et filtre la table.
    private static void activerFiltreGroupe(javafx.scene.Parent vue) {
        if (vue.lookup("#menuAjoutFiltre") instanceof MenuButton menu) {
            menu.getItems().stream()
                    .filter(item -> "Groupe".equals(item.getText()))
                    .findFirst()
                    .ifPresent(MenuItem::fire);
        }
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return GraineSonsValidation.creerInjecteur();
    }
}

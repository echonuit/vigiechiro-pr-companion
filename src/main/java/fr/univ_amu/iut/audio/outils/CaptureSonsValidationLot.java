package fr.univ_amu.iut.audio.outils;

import com.google.inject.Injector;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture la **sélection multiple** de la vue « Sons & validation » (`apercu-sons-validation-lot.png`,
/// #479) : plusieurs lignes sont retenues d'un coup (surbrillance accentuée) pour appliquer une action
/// **groupée** — valider, corriger ou marquer en référence tout le lot. Illustre le confort de revue
/// « à la volée » sur un ensemble d'observations.
///
/// Le seed et le rendu sont factorisés dans [GraineSonsValidation]. Un `main` distinct (une JVM par PNG)
/// car un seul état « audio chargé » est possible par processus (cf. [GraineSonsValidation]).
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureSonsValidationLot {

    private CaptureSonsValidationLot() {}

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
                GraineSonsValidation.dossierSortie().resolve("apercu-sons-validation-lot.png"),
                1100,
                vue -> GraineSonsValidation.selectionner(vue, 0, 1, 2)); // trois lignes → action groupée
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return GraineSonsValidation.creerInjecteur();
    }
}

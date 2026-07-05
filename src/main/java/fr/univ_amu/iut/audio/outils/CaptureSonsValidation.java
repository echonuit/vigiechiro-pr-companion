package fr.univ_amu.iut.audio.outils;

import com.google.inject.Injector;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'**aperçu général** de la vue audio unifiée (« Sons & validation ») sur la source `References`
/// en PNG (`apercu-sons-validation.png`) : la table des sons de référence (disposition empilée, colonnes de
/// contexte passage/carré/point visibles car la source n'est pas un unique passage), la première ligne
/// sélectionnée pour peupler le panneau d'écoute pleine largeur, et l'`AudioView` affichant un
/// **spectrogramme** réel calculé depuis un WAV de démonstration (cris FM de synthèse, cf. `SonDemo`).
///
/// Le seed et le rendu sont factorisés dans [GraineSonsValidation] (partagés avec
/// [CaptureSonsValidationFiltres] et [CaptureSonsValidationColonnes]).
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureSonsValidation {

    private CaptureSonsValidation() {}

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
                injecteur, GraineSonsValidation.dossierSortie().resolve("apercu-sons-validation.png"), 1100, vue -> {});
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return GraineSonsValidation.creerInjecteur();
    }
}

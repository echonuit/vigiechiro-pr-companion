package fr.univ_amu.iut.audio.outils;

import com.google.inject.Injector;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture les **trois avis** portés par une même détection (`apercu-sons-validation-avis-validateur.png`,
/// #1417) : Tadarida **propose**, l'observateur **corrige**, le validateur du MNHN **tranche**, et, sur la
/// détection sélectionnée, il **contredit** l'observateur (badge de désaccord). Le **fil de discussion**
/// qui en découle est ouvert à droite du lecteur.
///
/// **Pourquoi une capture à part.** L'aperçu général ([CaptureSonsValidation]) cadre la gauche de la table
/// et coupe **avant** la colonne « Avis du validateur » : la fonctionnalité y serait invisible. On fait
/// donc défiler la table jusqu'à elle : c'est ce qu'un utilisateur fait lui-même pour lire le verdict de
/// l'expert.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureAvisValidateur {

    private CaptureAvisValidateur() {}

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
                GraineSonsValidation.dossierSortie().resolve("apercu-sons-validation-avis-validateur.png"),
                1100,
                vue -> {
                    GraineSonsValidation.selectionner(vue, 0);
                    GraineSonsValidation.amenerLaColonne(vue, "colValidateur");
                });
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return GraineSonsValidation.creerInjecteur();
    }
}

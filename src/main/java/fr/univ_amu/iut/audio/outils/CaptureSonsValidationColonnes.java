package fr.univ_amu.iut.audio.outils;

import com.google.inject.Injector;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.TableView;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture la table avec **toutes les colonnes affichées** (`apercu-sons-validation-colonnes.png`,
/// #468/#480/#481/#491/#500) : nom de fichier, date, heure de capture, fréquence médiane, indicateur de
/// commentaire, et les mesures d'identification **FME** / **fréquence terminale** (calculées sur le cri de la
/// ligne sélectionnée). La scène est **élargie** pour laisser voir la richesse des colonnes.
///
/// Le sélecteur de colonnes (menu ☰) est un **popup** que le `snapshot` de scène ne capture pas : on illustre
/// donc directement les colonnes rendues dans la table plutôt que le menu ouvert.
///
/// Le seed et le rendu sont factorisés dans [GraineSonsValidation]. Un `main` distinct (une JVM par PNG).
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureSonsValidationColonnes {

    private CaptureSonsValidationColonnes() {}

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
                GraineSonsValidation.dossierSortie().resolve("apercu-sons-validation-colonnes.png"),
                1500,
                CaptureSonsValidationColonnes::afficherToutesLesColonnes);
    }

    /// Rend visibles toutes les colonnes de la table (celles masquées par défaut : FME, fréquence terminale,
    /// nom de fichier…), pour illustrer ce que propose le sélecteur de colonnes.
    private static void afficherToutesLesColonnes(javafx.scene.Parent vue) {
        if (vue.lookup("#tableObservations") instanceof TableView<?> table) {
            table.getColumns().forEach(colonne -> colonne.setVisible(true));
        }
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return GraineSonsValidation.creerInjecteur();
    }
}

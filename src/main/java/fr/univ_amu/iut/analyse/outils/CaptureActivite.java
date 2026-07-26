package fr.univ_amu.iut.analyse.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.analyse.model.ServiceActivite;
import fr.univ_amu.iut.analyse.view.ActiviteController;
import fr.univ_amu.iut.analyse.viewmodel.ActiviteViewModel;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
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
/// Capture l'écran **M-Activite** (#2352, lot 2 du chantier #2348) en PNG (`apercu-activite.png`) : la
/// courbe d'activité horaire d'une nuit, une série par espèce sur l'axe nocturne 18 h → 8 h, avec le
/// sélecteur de tranche et les cinq espèces les plus contactées cochées.
///
/// Contrairement à [fr.univ_amu.iut.diagnostic.outils.CaptureDiagnostic], **aucune base n'est seedée** :
/// l'écran se lit sur un [ServiceActivite] de démonstration (contacts fixes, forme de nuit réaliste,
/// montée après le coucher puis décroissance). L'injecteur reste au patron des captures (exécuteurs
/// **synchrones** de [ModuleCaptureCommun], garde-fou #510) ; les contrats de fil d'Ariane
/// ([OuvrirSite], [OuvrirPassage]) sont inertes, la capture étant rendue hors-chrome.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureActivite {

    /// Date du soir de la nuit de démonstration ; les contacts vont du soir 20 h au matin 05 h.
    private static final LocalDate SOIR = LocalDate.of(2026, 6, 21);

    /// Fenêtre nocturne de démonstration (coucher 21 h, lever 6 h), portée par le ViewModel.
    private static final PlageNuit NUIT = new PlageNuit(21, 6);

    private CaptureActivite() {}

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
        Path workspace = Files.createTempDirectory("vc-capture-activite");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));
        rendre(creerInjecteur(), sortie.resolve("apercu-activite.png"));
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// (`CablageInjecteursCaptureTest`).
    public static Injector creerInjecteur() {
        return Guice.createInjector(ModuleCaptureCommun.communSynchrone(), new PersistenceModule(), new ModuleDemo());
    }

    /// Charge `Activite.fxml`, l'ouvre sur le passage de démonstration puis rend la scène hors-écran en
    /// PNG. Scène bornée sous 1000 px (le rendu headless plafonne le blit à 1000×1000).
    private static void rendre(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(ActiviteController.class.getResource("Activite.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ActiviteController controleur = loader.getController();
        controleur.ouvrirSur(new ContextePassage(1L, 2, new ContexteSite("640380", "A1", null)));
        ApercuFx.enregistrerPng(new Scene(vue, 980, 620), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Contacts de démonstration : cinq espèces avec une forme de nuit plausible (comptes par heure de
    /// 20 h à 05 h). Déterministe, aucune base.
    private static List<ContactHoraire> contactsDemo() {
        List<ContactHoraire> contacts = new ArrayList<>();
        ajouter(contacts, "PIPKUH", "Pipistrelle de Kuhl", new int[] {2, 9, 16, 22, 18, 12, 7, 4, 2, 1});
        ajouter(contacts, "PIPPIP", "Pipistrelle commune", new int[] {6, 13, 10, 8, 6, 4, 3, 2, 1, 0});
        ajouter(contacts, "NYCNOC", "Noctule commune", new int[] {7, 5, 3, 2, 1, 1, 0, 0, 0, 0});
        ajouter(contacts, "BARBAR", "Barbastelle d'Europe", new int[] {0, 1, 2, 3, 3, 2, 1, 1, 0, 0});
        ajouter(contacts, "MYODAU", "Murin de Daubenton", new int[] {0, 1, 1, 2, 2, 1, 1, 0, 0, 0});
        return List.copyOf(contacts);
    }

    private static void ajouter(List<ContactHoraire> contacts, String taxon, String nom, int[] parHeure) {
        for (int index = 0; index < parHeure.length; index++) {
            int heure = (20 + index) % 24;
            LocalDate jour = (20 + index) >= 24 ? SOIR.plusDays(1) : SOIR;
            for (int rang = 0; rang < parHeure[index]; rang++) {
                contacts.add(new ContactHoraire(taxon, nom, "Chiroptères", jour.atTime(heure, (rang * 7) % 60)));
            }
        }
    }

    /// Module de démonstration : un [ActiviteViewModel] alimenté par un [ServiceActivite] à contacts
    /// fixes, et les contrats de fil d'Ariane inertes (la capture est rendue hors-chrome).
    private static final class ModuleDemo extends AbstractModule {

        @Provides
        ActiviteViewModel viewModel() {
            return new ActiviteViewModel(serviceDemo());
        }

        @Provides
        OuvrirSite ouvrirSite() {
            return new OuvrirSite() {
                @Override
                public void ouvrirListe() {
                    // Inerte : la capture est rendue hors-chrome, le fil d'Ariane n'est pas sollicité.
                }

                @Override
                public void ouvrirDetail(String numeroCarre) {
                    // Inerte (voir ouvrirListe).
                }
            };
        }

        @Provides
        OuvrirPassage ouvrirPassage() {
            return (idPassage, contexte) -> {
                // Inerte : la capture est rendue hors-chrome.
            };
        }

        /// Service à contacts fixes : les deux lectures sont surchargées, les DAO (nuls) ne sont jamais
        /// touchés. Un seul but, montrer une courbe déterministe sans base.
        private static ServiceActivite serviceDemo() {
            return new ServiceActivite(null, null) {
                @Override
                public List<ContactHoraire> contactsDuPassage(long idPassage) {
                    return contactsDemo();
                }

                @Override
                public Optional<PlageNuit> plageNuit(long idPassage) {
                    return Optional.of(NUIT);
                }
            };
        }
    }
}

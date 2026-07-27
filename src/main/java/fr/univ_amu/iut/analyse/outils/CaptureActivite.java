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
import fr.univ_amu.iut.passage.model.FenetreObserveeNuit;
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
        Path workspace = Files.createTempDirectory("vc-capture-activite");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));
        rendre(creerInjecteur(), sortie.resolve("apercu-activite.png"));
        rendre(injecteurAvec(List.of()), sortie.resolve("apercu-activite-vide.png"));
        exporter(creerInjecteur(), sortie.resolve("apercu-activite-export.png"));
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// (`CablageInjecteursCaptureTest`).
    public static Injector creerInjecteur() {
        return injecteurAvec(contactsDemo());
    }

    /// Injecteur de démonstration alimenté par les `contacts` donnés : la liste vide sert l'aperçu de
    /// l'**état vide**, qui nomme la cause de l'absence.
    private static Injector injecteurAvec(List<ContactHoraire> contacts) {
        return Guice.createInjector(
                ModuleCaptureCommun.communSynchrone(), new PersistenceModule(), new ModuleDemo(contacts));
    }

    /// Charge `Activite.fxml`, l'ouvre sur le passage de démonstration puis rend la scène hors-écran en
    /// PNG. Scène bornée sous 1000 px (le rendu headless plafonne le blit à 1000×1000).
    private static void rendre(Injector injecteur, Path fichier) throws IOException {
        ApercuFx.enregistrerPng(new Scene(ouvrir(injecteur), 980, 620), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Aperçu de l'**image exportée** (#2352) : passe par le vrai geste d'export du controller, qui
    /// redessine le graphe hors écran et l'estampille de son contexte. Reconstruire ici une imitation de
    /// l'export produirait une capture qui dériverait du produit (ADR 0025).
    private static void exporter(Injector injecteur, Path fichier) throws IOException {
        ActiviteController controleur = ouvrirControleur(injecteur);
        // Date d'export FIXE : les PNG sont versionnés, et un `LocalDate.now()` reverserait une capture
        // différente à chaque jour de CI.
        controleur.exporterVers(fichier, SOIR.plusDays(1));
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    private static Parent ouvrir(Injector injecteur) throws IOException {
        FXMLLoader loader = chargeur(injecteur);
        Parent vue = loader.load();
        ouvrirSurLaDemo(loader.getController());
        return vue;
    }

    private static ActiviteController ouvrirControleur(Injector injecteur) throws IOException {
        FXMLLoader loader = chargeur(injecteur);
        loader.load();
        ActiviteController controleur = loader.getController();
        ouvrirSurLaDemo(controleur);
        return controleur;
    }

    private static FXMLLoader chargeur(Injector injecteur) {
        FXMLLoader loader = new FXMLLoader(ActiviteController.class.getResource("Activite.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        return loader;
    }

    private static void ouvrirSurLaDemo(ActiviteController controleur) {
        controleur.ouvrirSur(new ContextePassage(1L, 2, new ContexteSite("640380", "A1", null)));
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
                // Trois nuits consécutives : la démo exerce le repliement sur l'axe nocturne (#2352), la
                // vue transverse étant le cas où plusieurs nuits se superposent.
                for (int nuit = 0; nuit < 3; nuit++) {
                    // Répartis sur toute l'heure (et non groupés en tête) : sinon les demi-heures paires
                    // ressortent vides et l'aperçu montre des creux à zéro qui viennent de la fixture,
                    // non du produit.
                    int minute = (rang * 59) / parHeure[index];
                    contacts.add(new ContactHoraire(
                            taxon, nom, "Chiroptères", jour.plusDays(nuit).atTime(heure, minute)));
                }
            }
        }
    }

    /// Module de démonstration : un [ActiviteViewModel] alimenté par un [ServiceActivite] à contacts
    /// fixes, et les contrats de fil d'Ariane inertes (la capture est rendue hors-chrome).
    private static final class ModuleDemo extends AbstractModule {

        /// Contacts que le service de démonstration renverra : la nuit type, ou **rien** pour l'aperçu de
        /// l'état vide.
        private final List<ContactHoraire> contacts;

        private ModuleDemo(List<ContactHoraire> contacts) {
            this.contacts = List.copyOf(contacts);
        }

        @Provides
        ActiviteViewModel viewModel() {
            return new ActiviteViewModel(serviceDemo(contacts));
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
        private static ServiceActivite serviceDemo(List<ContactHoraire> contacts) {
            return new ServiceActivite(null, null, null) {
                @Override
                public List<ContactHoraire> contactsDuPassage(long idPassage) {
                    return contacts;
                }

                @Override
                public Optional<PlageNuit> plageNuit(long idPassage) {
                    return Optional.of(NUIT);
                }

                /// Fenêtre d'enregistrement de la démo : de 20 h au soir à 6 h au matin, la plage sur
                /// laquelle une tranche sans contact vaut zéro.
                @Override
                public Optional<FenetreObserveeNuit.Bornes> fenetreEnregistree(long idPassage) {
                    return Optional.of(new FenetreObserveeNuit.Bornes(
                            SOIR.atTime(20, 0), SOIR.plusDays(1).atTime(6, 0)));
                }
            };
        }
    }
}

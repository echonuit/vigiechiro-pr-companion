package fr.univ_amu.iut.analyse.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.analyse.model.ServiceActivite;
import fr.univ_amu.iut.analyse.view.ActiviteController;
import fr.univ_amu.iut.analyse.viewmodel.ActiviteViewModel;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.FenetreObserveeNuit;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;

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

    /// Préfixe du message de sortie, repris par chaque rendu.
    private static final String APERCU_ECRIT = "Apercu ecrit dans ";

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
        // Schéma migré (comme CaptureAnalyse) : l'écran porte des onglets de vues mémorisées, dont le
        // dépôt lit `saved_filter_view`. Aucune donnée n'est semée pour autant : les contacts restent
        // fournis par le service de démonstration.
        Injector injecteur = creerInjecteur();
        new MigrationSchema(injecteur.getInstance(SourceDeDonnees.class)).migrer();
        rendre(injecteur, sortie.resolve("apercu-activite.png"));
        rendre(injecteurAvec(List.of()), sortie.resolve("apercu-activite-vide.png"));
        exporter(injecteur, sortie.resolve("apercu-activite-export.png"));
        rendreTransverse(injecteur, sortie.resolve("apercu-activite-transverse.png"));
        rendreApresExport(injecteur, sortie.resolve("apercu-activite-retour.png"));
        rendreListeLieu(injecteur, sortie.resolve("apercu-activite-lieu.png"));
    }

    /// Aperçu de la **liste ouverte de la puce « Lieu »** (#2967) : le critère qui a remplacé les deux
    /// listes à choix unique « Carré » et « Point ».
    ///
    /// C'est le seul endroit où se voient les deux choses qui font l'intérêt du critère : les valeurs
    /// **groupées et nommées** (Communes, Carrés, Points), là où une liste plate ne dirait pas si
    /// « Ahetze » est une commune ou un carré ; et le point sous sa forme **« carré · point »**, le schéma
    /// posant `UNIQUE(site_id, code)` - un code seul désigne autant de lieux qu'il y a de carrés.
    ///
    /// L'écran est ouvert en **transverse** : sur un passage unique, la liste n'aurait qu'un lieu par
    /// groupe et ne montrerait ni le groupement ni la qualification.
    private static void rendreListeLieu(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = chargeur(injecteur);
        Parent vue = loader.load();
        ActiviteController controleur = loader.getController();
        controleur.ouvrirTout("demo");
        if (!(vue.lookup("#menuAjoutFiltre") instanceof MenuButton menuAjout)) {
            throw new IllegalStateException("Menu « + Filtre » introuvable : la puce ne peut pas être posée.");
        }
        ApercuFx.exigerParLibelle("le menu « + Filtre »", menuAjout.getItems(), MenuItem::getText, "Lieu")
                .fire();
        // Reconnue à SON CONTENU, et non à la première puce venue : l'écran s'ouvre sur l'onglet
        // « Chiroptères », dont la puce « Taxon parent » porte la même classe CSS. Un `findFirst()` a
        // d'abord rendu un aperçu parfaitement lisible de la MAUVAISE puce, sous la bonne légende - le
        // défaut même que corrige l'ADR 3053, reproduit en l'écrivant.
        MenuButton puce = vue.lookupAll(".critere-multiple").stream()
                .filter(MenuButton.class::isInstance)
                .map(MenuButton.class::cast)
                .filter(bouton -> bouton.getItems().stream().anyMatch(item -> "Communes".equals(item.getText())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune puce ne porte l'en-tête « Communes » : ce n'est pas la liste « Lieu »."));
        if (!ApercuFx.enregistrerMenuOuvert(puce, fichier)) {
            throw new IllegalStateException("Popup de la puce non rendu : " + fichier + " n'aurait rien montré.");
        }
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// (`CablageInjecteursCaptureTest`).
    public static Injector creerInjecteur() {
        return injecteurAvec(contactsDemo());
    }

    /// Injecteur de démonstration alimenté par les `contacts` donnés : la liste vide sert l'aperçu de
    /// l'**état vide**, qui nomme la cause de l'absence.
    private static Injector injecteurAvec(List<ContactHoraire> contacts) {
        return Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(ModuleCaptureCommun.executeursSynchrones(), new ModuleDemo(contacts)));
    }

    /// Charge `Activite.fxml`, l'ouvre sur le passage de démonstration puis rend la scène hors-écran en
    /// PNG. Scène bornée sous 1000 px (le rendu headless plafonne le blit à 1000×1000).
    private static void rendre(Injector injecteur, Path fichier) throws IOException {
        ApercuFx.enregistrerPng(new Scene(ouvrir(injecteur), 980, 620), fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }

    /// Aperçu de l'**image exportée** (#2352) : passe par le vrai geste d'export du controller, qui
    /// redessine le graphe hors écran et l'estampille de son contexte. Reconstruire ici une imitation de
    /// l'export produirait une capture qui dériverait du produit (ADR 0025).
    private static void exporter(Injector injecteur, Path fichier) throws IOException {
        ActiviteController controleur = ouvrirControleur(injecteur);
        // Date d'export FIXE : les PNG sont versionnés, et un `LocalDate.now()` reverserait une capture
        // différente à chaque jour de CI.
        controleur.exporterVers(fichier, SOIR.plusDays(1));
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }

    /// Aperçu de la **vue transverse** (toutes les nuits de l'utilisateur) : second point d'entrée de
    /// l'écran, et le seul où l'**aplat nocturne disparaît** : plusieurs nuits n'ont pas de fenêtre
    /// commune, et en afficher une serait trompeur.
    private static void rendreTransverse(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = chargeur(injecteur);
        Parent vue = loader.load();
        ActiviteController controleur = loader.getController();
        controleur.ouvrirTout("demo");
        ApercuFx.enregistrerPng(new Scene(vue, 980, 620), fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }

    /// Aperçu du **bandeau de retour** après un export réussi : sans lui, un export qui a marché serait
    /// indiscernable d'un clic sans effet. L'élément n'apparaît sur aucun autre aperçu de cet écran.
    private static void rendreApresExport(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = chargeur(injecteur);
        Parent vue = loader.load();
        ActiviteController controleur = loader.getController();
        ouvrirSurLaDemo(controleur);
        controleur.exporterVers(Path.of(System.getProperty("java.io.tmpdir"), "activite-nuit.png"), SOIR.plusDays(1));
        ApercuFx.enregistrerPng(new Scene(vue, 980, 620), fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
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

    /// Le contexte du passage de démonstration : celui de la **première nuit** du semis, et non un carré
    /// jumeau écrit à côté. Deux littéraux à tenir d'accord finissent par diverger, et l'aperçu montrerait
    /// alors un en-tête qui ne correspond à aucune des données tracées.
    private static void ouvrirSurLaDemo(ActiviteController controleur) {
        controleur.ouvrirSur(new ContextePassage(1L, 2, new ContexteSite(LIEUX[0][1], LIEUX[0][2], null)));
    }

    /// Le lieu de chacune des trois nuits : commune, carré, point (#2967).
    ///
    /// Trois triplets **choisis pour ce que la puce « Lieu » doit montrer**, et non au hasard : deux
    /// nuits partagent leur commune et leur carré en changeant de point, la troisième change tout. La
    /// liste ouverte présente donc deux communes, deux carrés et trois points - de quoi voir à la fois le
    /// **groupement** par dimension et la **qualification** du point par son carré. Un semis à un seul
    /// lieu aurait rendu une liste d'une ligne par groupe, où rien de tout cela ne se lit.
    private static final String[][] LIEUX = {
        {"Ahetze", "640380", "A1"},
        {"Ahetze", "640380", "B2"},
        {"Biarritz", "870150", "Z1"},
    };

    /// Contacts de démonstration : cinq espèces avec une forme de nuit plausible (comptes par heure de
    /// 20 h à 05 h). Déterministe, aucune base.
    private static List<ContactHoraire> contactsDemo() {
        List<ContactHoraire> contacts = new ArrayList<>();
        ajouter(contacts, "Pipkuh", "Pipistrelle de Kuhl", new int[] {2, 9, 16, 22, 18, 12, 7, 4, 2, 1});
        ajouter(contacts, "Pippip", "Pipistrelle commune", new int[] {6, 13, 10, 8, 6, 4, 3, 2, 1, 0});
        ajouter(contacts, "Nycnoc", "Noctule commune", new int[] {7, 5, 3, 2, 1, 1, 0, 0, 0, 0});
        ajouter(contacts, "Barbar", "Barbastelle d'Europe", new int[] {0, 1, 2, 3, 3, 2, 1, 1, 0, 0});
        ajouter(contacts, "Myodau", "Murin de Daubenton", new int[] {0, 1, 1, 2, 2, 1, 1, 0, 0, 0});
        return List.copyOf(contacts);
    }

    /// Les codes suivent la **casse du référentiel Tadarida** (`Pippip`, et non `PIPPIP`) : le repère
    /// « espèce à enjeu » (#2353) compare des codes, et une démo en majuscules montrerait l'écran sans la
    /// fonctionnalité : un aperçu qui documente une absence.
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
                            taxon,
                            nom,
                            "Chiroptères",
                            jour.plusDays(nuit).atTime(heure, minute),
                            LIEUX[nuit][0],
                            LIEUX[nuit][1],
                            LIEUX[nuit][2],
                            null));
                }
            }
        }
    }

    /// Module de démonstration : un [ActiviteViewModel] alimenté par un [ServiceActivite] à contacts
    /// fixes, et les contrats de fil d'Ariane inertes (la capture est rendue hors-chrome).
    private static final class ModuleDemo extends AbstractModule {

        /// Référentiel des **espèces à enjeu** (#2353) : cet injecteur n'installe ni `ValidationModule`
        /// (qui pose la liaison réelle) ni `AudioModule` (qui pose le défaut vide), il déclare donc son
        /// propre monde. Une espèce marquée, pour que l'aperçu **montre** le repère plutôt que de
        /// l'attester par une absence.
        @Provides
        EspecesPrioritaires especesPrioritaires() {
            // Le sous-ensemble RÉELLEMENT prioritaire parmi les cinq espèces de la démo : Pipistrelle
            // commune et Noctule commune figurent au plan national, la Pipistrelle de Kuhl, la
            // Barbastelle d'Europe et le Murin de Daubenton non. Un référentiel de démonstration qui
            // s'écarterait du vrai ferait mentir l'aperçu dans un sens ou dans l'autre.
            return () -> Set.of("Pippip", "Nycnoc");
        }

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
            return new ServiceActivite(null, null, null, Set::of) {
                @Override
                public List<ContactHoraire> contactsDuPassage(long idPassage) {
                    return contacts;
                }

                /// Vue transverse : les mêmes contacts, l'écran couvrant alors toutes les nuits de
                /// l'utilisateur plutôt qu'un passage.
                @Override
                public List<ContactHoraire> contactsDeLUtilisateur(String idUtilisateur) {
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

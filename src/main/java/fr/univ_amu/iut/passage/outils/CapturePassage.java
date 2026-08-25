package fr.univ_amu.iut.passage.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.AcquisitionAncrage;
import fr.univ_amu.iut.commun.model.FuseauDuPoint;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RapportAncrage;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.OuvrirActivite;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.di.CampagneModule;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.FenetreObserveeNuit;
import fr.univ_amu.iut.passage.model.HydratationSquelette;
import fr.univ_amu.iut.passage.model.IndiceAcoustique;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.RapportReactivation.AbsenceReactivation;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import fr.univ_amu.iut.passage.model.VoieReactivation;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.passage.view.AppuisPassage;
import fr.univ_amu.iut.passage.view.NavigationPassage;
import fr.univ_amu.iut.passage.view.PassageController;
import fr.univ_amu.iut.passage.view.RattachementModaleController;
import fr.univ_amu.iut.passage.view.ReactivationModaleController;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran pivot M-Passage en PNG pour le comparer à la maquette du brief, dans plusieurs
/// **états** afin d'en montrer les particularités :
///
/// - `apercu-passage.png` : passage **vérifié**, « Préparer le dépôt » actif, validation Tadarida
///   verrouillée (le passage n'est pas encore déposé) ;
/// - `apercu-passage-depose.png` : passage **déposé**, stepper au bout, « Préparer le dépôt »
///   désactivé, validation Tadarida déverrouillée ;
/// - `apercu-passage-rattachement.png` : la **modale « Modifier le passage »** (rattachement, météo,
///   enregistreur, micro) **hors connexion** : la ligne VigieChiro y est masquée ;
/// - `apercu-passage-rattachement-connecte.png` : la même, **connecté**, « Récupérer depuis
///   VigieChiro » et « Envoyer vers VigieChiro » apparaissent (#1839) ;
/// - `apercu-passage-reactivation.png` : la modale « Réactiver ce passage » et ses deux barres (#1780) ;
/// - `apercu-passage-squelette.png` : la fiche d'une nuit **rapatriée de Vigie-Chiro**, connecté :
///   audio absente, et « Réactiver ce passage » **actif** (#2554).
///
/// À ne pas « corriger » : le site et le point (cibles de clé étrangère) sont insérés en SQL brut, sans les DAO de la
/// feature `sites` : `passage` ne doit pas en dépendre (cycle ArchUnit `features_sans_cycle`, et
/// `sites` dépend déjà de `passage`).
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CapturePassage {

    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final String ENREGISTREUR = "1925492";
    private static final String NUMERO_CARRE = "640380";
    private static final String CODE_POINT = "A1";
    private static final String NOM_SITE = "Étang de la Tuilière";

    /// Participation Vigie-Chiro à laquelle la nuit squelette est rattachée. Aucun appel réseau n'a lieu :
    /// seule la PRÉSENCE du lien compte pour la règle qui autorise la réactivation.
    private static final String PARTICIPATION_DEMO = "6a53f5faae21902a597394d3";
    private static final String APERCU_ECRIT = "Apercu ecrit dans ";
    private static final String FXML_RATTACHEMENT = "RattachementModale.fxml";
    private static final int NB_SEQUENCES = 60;
    /// Volumes posés en **base 1000**, celle dans laquelle `Formats` les rend depuis #3573. Écrits en
    /// base 1024, ils affichaient « 189 Mo » là où le littéral disait 180 : le chiffre publié n'était
    /// plus celui qu'on avait choisi, et aucun test ne rougit là-dessus (#3624).
    private static final long VOLUME_ORIGINAUX_OCTETS = 5_000_000_000L;
    private static final long VOLUME_SEQUENCES_OCTETS = 180_000_000L;

    private CapturePassage() {}

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

    /// Identifiant volontairement absent de la base : la fiche échoue à charger et rend son bandeau.
    private static final long PASSAGE_INEXISTANT = 999_999L;

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-passage");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        new EnregistreurDao(source).insert(new Enregistreur(ENREGISTREUR, "V1.01", null));
        // Deux campagnes (#2355, #2630) : la liste déroulante de « Modifier le passage » montre un
        // choix réel plutôt que sa seule sentinelle « aucune campagne », et la modale de gestion a de
        // quoi lister. Le commentaire de la première est rendu dans sa cellule.
        CampagneDao campagneDao = new CampagneDao(source);
        Long ens = campagneDao
                .insert(new Campagne(null, "Suivi ENS 2026", 2026, "commanditaire : le Parc"))
                .id();
        campagneDao.insert(new Campagne(null, "Thèse Samuel", 2025, null));
        long idPoint = seederSiteEtPoint(source);
        long idVerifie = seederPassage(source, workspace, idPoint, StatutWorkflow.VERIFIE, 2, ens);
        long idDepose = seederPassage(source, workspace, idPoint, StatutWorkflow.DEPOSE, 1, null);

        // Pivot : deux statuts pour montrer l'évolution des actions disponibles (préparer le dépôt
        // quand vérifié ; validation déverrouillée une fois déposé).
        rendrePivot(injecteur, idVerifie, sortie.resolve("apercu-passage.png"));
        rendrePivot(injecteur, idDepose, sortie.resolve("apercu-passage-depose.png"));
        // Modale « Modifier le passage » ouverte sur le passage vérifié, dans ses DEUX états : hors
        // connexion (la ligne VigieChiro est masquée) puis connecté (elle apparaît). Sans le second, les
        // deux gestes livrés par #1839 n'étaient montrés nulle part - le harnais n'ayant pas de passerelle,
        // la capture unique les cachait sans que rien ne le signale.
        rendreRattachement(injecteur, idVerifie, sortie.resolve("apercu-passage-rattachement.png"));
        rendreGestionCampagnes(injecteur, sortie.resolve("apercu-passage-campagnes.png"));
        rendreRattachement(injecteurConnecte(), idVerifie, sortie.resolve("apercu-passage-rattachement-connecte.png"));
        // Troisième état de la modale : une nuit SQUELETTE (rapatriée, sans fichier ni séquence). Ses
        // heures ne sont attestées par rien, donc saisissables (#1892) - l'inverse des deux captures
        // ci-dessus, où elles sont grisées. Sans elle, le seul cas où l'utilisateur PEUT corriger ses
        // heures n'était montré nulle part, alors que c'est le cas qui a motivé le geste.
        long idSquelette = seederSquelette(source, workspace, idPoint);
        rendreRattachementSquelette(
                injecteur, idSquelette, sortie.resolve("apercu-passage-rattachement-squelette.png"));
        // La FICHE de cette même nuit, connecté (#2554). C'est l'écran du défaut d'origine : « Réactiver ce
        // passage » y était grisé, parce que le gating exigeait des séquences que la synchro n'avait pas
        // rapatriées. Aucune capture ne le montrait - la seule vue d'un squelette était la modale
        // ci-dessus, où le bouton n'apparaît pas. Il faut la passerelle ET le rattachement à une
        // participation : c'est cette combinaison que la règle interroge.
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(
                        LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idSquelette), PARTICIPATION_DEMO));
        rendrePivot(injecteurConnecte(), idSquelette, sortie.resolve("apercu-passage-squelette.png"));
        // Modale « Réactiver ce passage » (#1780) : les deux barres de phase en cours (régénération pleine,
        // ancrage à mi-course), montrant que la barre ne reste plus figée pendant l'ancrage réseau.
        rendreModaleReactivation(injecteur, sortie.resolve("apercu-passage-reactivation.png"));
        // Bandeau de retour en erreur (#1917) : ouvrir sur un passage inexistant produit le cas réel sans
        // mock. Aucun aperçu ne montrait de bandeau avant cette passe.
        rendrePivot(injecteur, PASSAGE_INEXISTANT, sortie.resolve("apercu-passage-retour.png"));
        rendreRattachementRetour(injecteur, idVerifie, sortie.resolve("apercu-passage-rattachement-retour.png"));
        rendreCompteRenduReactivation(injecteur, sortie.resolve("apercu-passage-reactivation-compte-rendu.png"), true);
        rendreCompteRenduReactivation(injecteur, sortie.resolve("apercu-passage-reactivation-lacunes.png"), false);
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// (test).
    public static Injector creerInjecteur() {
        // La ligne « Campagne » (#2355) manquait ici faute de `CampagneModule` : composer depuis la
        // racine rend l'oubli impossible plutôt que de le réparer une fois de plus.
        return Guice.createInjector(
                Modules.override(RacineInjecteur.modules()).with(ModuleCaptureCommun.executeursSynchrones()));
    }

    /// Même injecteur, mais **connecté** : la passerelle [SynchronisationParticipation] est posée, ce qui
    /// fait apparaître la ligne « Récupérer / Envoyer » de la modale (le contrôleur la masque quand
    /// l'`Optional` est vide). Le client pointe vers une adresse morte : la capture **rend** la vue, elle
    /// ne clique sur rien - il suffit que la passerelle existe.
    ///
    /// Il partage la base de la capture : le workspace est fixé par propriété système avant sa création,
    /// donc les deux injecteurs ouvrent le même fichier SQLite.
    private static Injector injecteurConnecte() {
        return Guice.createInjector(
                ModuleCaptureCommun.communSynchrone(),
                new PersistenceModule(),
                new PassageModule(),
                new CampagneModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        // Le fournisseur se réclame DANS configure() : `getProvider` hors de cette méthode
                        // lève « The binder can only be used inside configure() ».
                        Provider<SourceDeDonnees> source = getProvider(SourceDeDonnees.class);
                        Provider<Workspace> workspace = getProvider(Workspace.class);
                        Provider<Horloge> horloge = getProvider(Horloge.class);
                        OptionalBinder.newOptionalBinder(binder(), SynchronisationParticipation.class)
                                .setBinding()
                                // Classe anonyme et non lambda : `com.google.inject.Provider` n'a qu'une
                                // methode abstraite mais n'est pas annote `@FunctionalInterface`, et javac
                                // l'accepte comme cible de lambda la ou ecj le refuse (#3228). `Providers.of`
                                // ne conviendrait pas ici : il evaluerait MAINTENANT, dans configure(), alors
                                // que `source.get()` n'a de sens qu'a l'injection.
                                .toProvider(new Provider<SynchronisationParticipation>() {
                                    @Override
                                    public SynchronisationParticipation get() {
                                        return passerelleDApercu(source.get());
                                    }
                                });
                        // L'hydratation d'un squelette, posée pour la même raison et par le même
                        // conditionnement qu'en production : ReconstructionModule ne se charge qu'avec
                        // ConnexionModule, donc « connecté » veut bien dire « hydratation disponible ».
                        // C'est ce que la règle de gating interroge pour activer « Réactiver ce passage »
                        // sur une nuit sans séquence (#2554).
                        OptionalBinder.newOptionalBinder(binder(), HydratationSquelette.class)
                                .setBinding()
                                .toProvider(new Provider<HydratationSquelette>() {
                                    @Override
                                    public HydratationSquelette get() {
                                        return new HydratationSquelette(
                                                source.get(),
                                                new ClientVigieChiro("http://localhost:1", Optional::empty),
                                                workspace.get(),
                                                horloge.get(),
                                                Optional.empty(),
                                                () -> {});
                                    }
                                });
                    }

                    private SynchronisationParticipation passerelleDApercu(SourceDeDonnees source) {
                        return new SynchronisationParticipation(
                                new ClientVigieChiro("http://localhost:1", Optional::empty),
                                new LienVigieChiroDao(source),
                                new PassageDao(source),
                                new MaterielMicroDao(source),
                                new EnregistreurDao(source),
                                idPoint -> Optional.empty(),
                                new FenetreObserveeNuit(
                                        new SessionDao(source),
                                        new EnregistrementOriginalDao(source),
                                        new SequenceDao(source)),
                                // Aperçu : aucune commune résolue, donc le repli métropole - ce que
                                // montrent les captures, et ce qu'attend leur déterminisme.
                                new FuseauDuPoint(idPoint -> Optional.empty()));
                    }
                });
    }

    /// Charge `Passage.fxml` sur `idPassage` (ViewModel connu + contrats de navigation neutres) et
    /// rend le pivot hors-écran.
    private static void rendrePivot(Injector injecteur, long idPassage, Path fichier) throws IOException {
        PassageViewModel passageVm = new PassageViewModel(
                injecteur.getInstance(ServicePassage.class),
                injecteur.getInstance(ServiceReactivationPassage.class),
                injecteur.getInstance(PortailVigieChiro.class));
        FXMLLoader loader = new FXMLLoader(PassageController.class.getResource("Passage.fxml"));
        loader.setControllerFactory(type -> type == PassageController.class
                ? new PassageController(
                        passageVm,
                        Optional.<OuvrirVerification>of(idp -> {}),
                        Optional.<OuvrirDiagnostic>of(idp -> {}),
                        // Feature `activite-nuit` OPTIONNELLE (offerte par défaut depuis la clôture du lot
                        // #2352) : la carte figure donc sur l'aperçu, comme dans le produit.
                        Optional.<OuvrirActivite>of(idp -> {}),
                        idp -> {},
                        Optional.<OuvrirLot>of(idp -> {}),
                        injecteur.getInstance(NavigationPassage.class),
                        ouvrirSiteNeutre(),
                        numeroCarre -> {},
                        idp -> 0,
                        // Appuis socle (#1213) : l'exécuteur vient de l'injecteur de capture, donc
                        // SYNCHRONE (garde-fou #1278) - le snapshot part une fois l'écran chargé.
                        new AppuisPassage(
                                injecteur.getInstance(ExecuteurTache.class),
                                injecteur.getInstance(PortailVigieChiro.class),
                                url -> {},
                                // Synthèse absente de l'injecteur de capture : la carte se masque, comme
                                // elle le ferait la feature coupée. L'aperçu montre l'écran sans elle.
                                Optional.empty()))
                : injecteur.getInstance(type));
        Parent vue = loader.load();
        PassageController controleur = loader.getController();
        controleur.ouvrirSur(idPassage, new ContexteSite(NUMERO_CARRE, CODE_POINT, NOM_SITE));
        // 1280 et non 1100 depuis la 5e carte d'actions (« Activité de la nuit », #2352) : à largeur
        // constante, cinq cartes se partagent ce que quatre occupaient, les titres passent à deux lignes et
        // la garde des libellés comprimés refuse l'aperçu. On rend donc la fenêtre de référence d'un cran
        // plus large, ce qui redonne à chaque carte la largeur qu'elle avait à quatre.
        ApercuFx.enregistrerPng(new Scene(vue, 1280, 620), fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }

    /// Contrat [fr.univ_amu.iut.commun.view.OuvrirSite] neutre (no-op) pour la capture : la navigation
    /// vers le site (segment « Carré N » du fil d'Ariane) est portée par le chrome, hors de cet aperçu
    /// hors-écran. Évite aussi de faire dépendre l'outil de la feature `sites` (cycle ArchUnit).
    private static fr.univ_amu.iut.commun.view.OuvrirSite ouvrirSiteNeutre() {
        return new fr.univ_amu.iut.commun.view.OuvrirSite() {
            @Override
            public void ouvrirListe() {}

            @Override
            public void ouvrirDetail(String numeroCarre) {}
        };
    }

    /// Charge `RattachementModale.fxml` (controller injecté par Guice), la démarre sur le passage et
    /// rend la modale hors-écran.
    private static void rendreRattachement(Injector injecteur, long idPassage, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(NavigationPassage.class.getResource(FXML_RATTACHEMENT));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        RattachementModaleController controleur = loader.getController();
        controleur.demarrer(idPassage, NUMERO_CARRE, CODE_POINT, () -> {});
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }

    /// La même modale sur une nuit **squelette**, mais **défilée jusqu'aux heures**.
    ///
    /// La capture existait déjà et sa légende annonçait les heures saisissables (#1892) - sauf qu'elles
    /// vivent en bas d'un `ScrollPane` (#2496) et **n'apparaissaient pas dans l'image** : on documentait un
    /// état par une photo qui ne le montrait pas. Constat de la passe 8 de #2554.
    ///
    /// On défile comme le ferait l'utilisateur, après une passe CSS + layout (le skin du `ScrollPane`
    /// n'existe pas avant), plutôt que d'agrandir la fenêtre : l'écran headless est figé à 1000 px de haut.
    private static void rendreRattachementSquelette(Injector injecteur, long idPassage, Path fichier)
            throws IOException {
        FXMLLoader loader = new FXMLLoader(NavigationPassage.class.getResource(FXML_RATTACHEMENT));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        RattachementModaleController controleur = loader.getController();
        controleur.demarrer(idPassage, NUMERO_CARRE, CODE_POINT, () -> {});
        Scene scene = new Scene(vue);
        vue.applyCss();
        vue.layout();
        ((ScrollPane) scene.lookup(".corps-modale")).setVvalue(1.0);
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }

    /// Modale **« Gérer les campagnes »** (#2630) : créer, renommer et supprimer une campagne sans
    /// terminal. Elle comble le dernier critère de #2355 non tenu côté interface.
    private static void rendreGestionCampagnes(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(NavigationPassage.class.getResource("GestionCampagnesModale.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }

    /// Modale « Modifier le passage » avec son **bandeau de retour** en severite INFO (#1917).
    ///
    /// La temperature est le bon vecteur : sa validation se declenche a l'« Appliquer », qui reste
    /// cliquable meme avec une saisie invalide. Plusieurs guidages voisins sont au contraire
    /// **inatteignables par l'IHM**, leur bouton etant grise tant que la saisie est invalide (« on
    /// empeche au lieu d'avertir apres coup », #790) : leur message ne peut pas etre montre ici, ni
    /// d'ailleurs jamais etre lu par un utilisateur.
    ///
    /// INFO et non ERREUR : un champ mal rempli n'est pas une panne. C'est exactement la nuance que le
    /// canal, nomme « messageErreur », ne pouvait pas porter avant ce chantier.
    private static void rendreRattachementRetour(Injector injecteur, long idPassage, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(NavigationPassage.class.getResource(FXML_RATTACHEMENT));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        RattachementModaleController controleur = loader.getController();
        controleur.demarrer(idPassage, NUMERO_CARRE, CODE_POINT, () -> {});
        Scene scene = new Scene(vue);
        // Le corps du formulaire vit desormais dans un ScrollPane (#2496) : son contenu n'est atteignable
        // par scene.lookup qu'une fois le skin construit, donc apres une passe CSS + layout.
        vue.applyCss();
        vue.layout();
        // On passe par le CHAMP et non par le ViewModel : c'est ce que fait l'utilisateur, et cela evite
        // d'ouvrir une couture d'apercu dans le controleur pour les besoins d'une capture.
        ((TextField) scene.lookup("#champTemperature")).setText("huit degres");
        // La validation vit dans « Appliquer », pas dans un ecouteur de frappe : c'est le clic qui la
        // declenche, et donc lui qui fait paraitre le bandeau.
        ((Button) scene.lookup("#boutonAppliquer")).fire();
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }

    /// Charge `ReactivationModale.fxml` (controller injecté par Guice) et la rend hors-écran dans l'état
    /// « les deux phases en cours » : régénération pleine, ancrage à mi-course. Aucun vrai travail n'est
    /// lancé - c'est le point de l'aperçu ([ReactivationModaleController#apercuPhasesEnCours], #1780).
    /// Les **deux issues** d'une réactivation, que rien ne montrait jusqu'ici (#1943) : celle qui aboutit,
    /// et celle qui laisse des lacunes **nommées**.
    ///
    /// L'état vient d'un vrai [RapportReactivation] passé au ViewModel : la mise en forme est celle de la
    /// production, pas une reconstitution (ADR 0025). C'est ce qui rend la capture capable de mentir moins
    /// que le texte qu'on aurait recopié - et de dériver visiblement le jour où le compte rendu changera.
    private static void rendreCompteRenduReactivation(Injector injecteur, Path fichier, boolean complet)
            throws IOException {
        FXMLLoader loader = new FXMLLoader(ReactivationModaleController.class.getResource("ReactivationModale.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ReactivationModaleController controleur = loader.getController();
        controleur.apercuCompteRendu(complet ? rapportComplet() : rapportAvecLacunes());
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }

    /// La nuit revenue entière : c'est le cas nominal, et il doit se voir aussi.
    private static RapportReactivation rapportComplet() {
        return new RapportReactivation(
                4236,
                0,
                0,
                0,
                NiveauConfiance.CERTITUDE,
                List.of(),
                new DecompteAudio(4236, 4236),
                VoieReactivation.BRUTS,
                // (mesurées, concordantes) - dans cet ordre : la phrase rendue dit « concordantes sur
                // mesurées », et les inverser produit un « 4236 sur 4053 » que seule la capture révèle.
                new IndiceAcoustique(4236, 4053),
                RapportAncrage.aucun(),
                List.of());
    }

    /// Une nuit incomplète, avec les **deux** motifs d'absence : l'un renvoie l'utilisateur à son dossier,
    /// l'autre désigne un défaut de notre côté. C'est cette distinction que la capture doit rendre lisible.
    private static RapportReactivation rapportAvecLacunes() {
        return new RapportReactivation(
                4229,
                0,
                7,
                0,
                NiveauConfiance.FORTE,
                List.of(),
                new DecompteAudio(4229, 4236),
                VoieReactivation.BRUTS,
                null,
                RapportAncrage.aucun(),
                List.of(
                        new AbsenceReactivation(
                                "Car130711-2026-Pass2-Z41-PaRecPR1997632_20260704_223507.wav",
                                "enregistrement absent du dossier",
                                6),
                        new AbsenceReactivation(
                                "Car130711-2026-Pass2-Z41-PaRecPR1997632_20260705_012327_001.wav",
                                "tranche non régénérée depuis son enregistrement",
                                1)));
    }

    private static void rendreModaleReactivation(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(ReactivationModaleController.class.getResource("ReactivationModale.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ReactivationModaleController controleur = loader.getController();
        // L'état est posé AVANT la scène, si bien que celle-ci se dimensionne sur le contenu réellement
        // affiché - exactement ce que fait la vraie fenêtre en s'ouvrant. La hauteur codée en dur de
        // l'aperçu précédent (340 px pour ~190 px de contenu) ne laissait pas seulement du blanc : elle
        // donnait à la modale 150 px de marge, de sorte qu'aucune croissance ne pouvait s'y voir. L'aperçu
        // ne mentait pas sur les pixels, il mentait sur le cadre.
        controleur.apercuPhasesEnCours("Régénération 30/30", 1.0, AcquisitionAncrage.LIBELLE + " (page 3/12)", 0.25);
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }

    /// Seede une nuit complète (chemins sous le `workspace` temporaire) avec le `statut` et le
    /// `numero` de passage donnés, et renvoie l'identifiant du passage. Le site/point (FK) est seedé
    /// à part et partagé.
    /// Nuit **squelette** : rapatriée de la plateforme, elle porte son identité mais **aucun
    /// enregistrement** - ni session, ni original, ni séquence. Rien n'atteste donc ses heures, et c'est
    /// exactement la nuit dont l'utilisateur doit pouvoir les corriger à la main (#1892).
    ///
    /// Ses bornes sont volontairement **aberrantes** (`15:00 → 15:00`) : c'est l'état réel qu'a produit le
    /// cliquet de #1860 sur le terrain, celui qu'on vient réparer. Une capture qui montrerait une nuit
    /// déjà juste n'illustrerait pas le besoin.
    private static long seederSquelette(SourceDeDonnees source, Path workspace, long idPoint) {
        long idPassage = new PassageDao(source)
                .insert(new Passage(
                        null,
                        3,
                        2026,
                        "2026-07-04",
                        "15:00",
                        "15:00",
                        null,
                        // « Récupéré » (#2581), et non « Déposé » : c'est ce que pose la synchro. La
                        // capture montrait jusqu'ici l'état d'avant le chantier - une pastille bleue et
                        // les cinq jalons du workflow marqués franchis sur une nuit qui n'en a parcouru
                        // aucun. Une capture ne peut pas échouer : elle dessine ce qu'on lui donne, et
                        // elle a continué d'être régénérée sans que rien ne signale qu'elle mentait.
                        StatutWorkflow.RECUPERE,
                        null,
                        null,
                        null,
                        "2026-07-05",
                        idPoint,
                        ENREGISTREUR,
                        null))
                .id();
        // Sa session archivée, SANS original ni séquence : c'est ce que pose la synchro
        // (CreationPassageArchive), et c'est ce qui distingue un squelette d'une nuit sans dossier. Sans
        // elle, la capture montrait un état que la plateforme ne produit jamais - et la réactivation, qui
        // relit le préfixe depuis ce chemin, n'aurait eu nulle part où le lire.
        new SessionDao(source)
                .insert(new SessionDEnregistrement(
                        null,
                        workspace
                                .resolve(new Prefixe(NUMERO_CARRE, 2026, 3, CODE_POINT).nomDossierSession())
                                .toString(),
                        0L,
                        0L,
                        idPassage));
        return idPassage;
    }

    private static long seederPassage(
            SourceDeDonnees source, Path workspace, long idPoint, StatutWorkflow statut, int numero, Long idCampagne) {
        PassageDao passageDao = new PassageDao(source);
        SessionDao sessionDao = new SessionDao(source);
        EnregistrementOriginalDao originalDao = new EnregistrementOriginalDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);

        Prefixe prefixe = new Prefixe(NUMERO_CARRE, 2026, numero, CODE_POINT);
        Passage passage = passageDao.insert(new Passage(
                null,
                numero,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                null,
                statut,
                Verdict.OK,
                null,
                "{\"tempDebut\":8.5}", // météo de début de nuit (#106) : montre la valeur sur l'aperçu
                null,
                idPoint,
                ENREGISTREUR,
                idCampagne));
        SessionDEnregistrement session = sessionDao.insert(new SessionDEnregistrement(
                null,
                workspace.resolve(prefixe.nomDossierSession()).toString(),
                VOLUME_ORIGINAUX_OCTETS,
                VOLUME_SEQUENCES_OCTETS,
                passage.id()));

        LocalDateTime debut = LocalDateTime.of(2026, 6, 22, 20, 25, 0);
        DateTimeFormatter horodatage = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        for (int i = 0; i < NB_SEQUENCES; i++) {
            String suffixe =
                    "PaRecPR" + ENREGISTREUR + "_" + debut.plusMinutes(12L * i).format(horodatage) + ".wav";
            String nomOriginal = prefixe.nommerOriginal(suffixe);
            EnregistrementOriginal original = originalDao.insert(new EnregistrementOriginal(
                    null,
                    nomOriginal,
                    workspace.resolve("bruts").resolve(nomOriginal).toString(),
                    5.0,
                    384000,
                    null,
                    session.id()));
            String nomSequence = prefixe.nommerSequence(nomOriginal, 0);
            sequenceDao.insert(new SequenceDEcoute(
                    null,
                    nomSequence,
                    original.id(),
                    0,
                    0.0,
                    5.0,
                    workspace.resolve("transformes").resolve(nomSequence).toString(),
                    false,
                    session.id()));
        }
        return passage.id();
    }

    /// Insère en SQL brut un site (`monitoring_site`) et son point d'écoute (`listening_point`),
    /// cibles de clé étrangère du passage, et renvoie l'`id` du point. Volontairement sans les DAO de
    /// la feature `sites` : `passage` ne doit pas en dépendre (cycle ArchUnit). Le point n'est qu'une
    /// FK ici : M-Passage affiche le libellé du site via [ContexteSite], sans jointure.
    private static long seederSiteEtPoint(SourceDeDonnees source) {
        String insertSite = "INSERT INTO monitoring_site"
                + "(square_number, friendly_name, protocol, comment, created_at, user_id)"
                + " VALUES (?, ?, ?, ?, ?, ?)";
        String insertPoint =
                "INSERT INTO listening_point(code, gps_lat, gps_lon, description, site_id)" + " VALUES (?, ?, ?, ?, ?)";
        try (Connection cx = source.getConnection()) {
            long idSite;
            try (PreparedStatement ps = cx.prepareStatement(insertSite, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, NUMERO_CARRE);
                ps.setString(2, NOM_SITE);
                // La colonne `protocol` stocke le LIBELLÉ persisté, pas le nom de la constante :
                // `Protocole.parLibelle` refuse « STANDARD ». Personne ne relisait cette colonne sur ce
                // chemin, donc la fixture mentait sans conséquence - jusqu'à ce qu'un lecteur arrive (#1495).
                ps.setString(3, Protocole.STANDARD.libelle());
                ps.setString(4, "Aix-en-Provence");
                ps.setString(5, "2026-01-01");
                ps.setString(6, ID_UTILISATEUR);
                ps.executeUpdate();
                idSite = cleGeneree(ps);
            }
            try (PreparedStatement ps = cx.prepareStatement(insertPoint, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, CODE_POINT);
                ps.setDouble(2, 43.4010);
                ps.setDouble(3, -1.5740);
                ps.setString(4, "Près du grand chêne");
                ps.setLong(5, idSite);
                ps.executeUpdate();
                return cleGeneree(ps);
            }
        } catch (SQLException echec) {
            throw new IllegalStateException("Seed SQL du site/point impossible", echec);
        }
    }

    private static long cleGeneree(PreparedStatement ps) throws SQLException {
        try (ResultSet cles = ps.getGeneratedKeys()) {
            if (!cles.next()) {
                throw new IllegalStateException("Aucune clé générée renvoyée par l'INSERT");
            }
            return cles.getLong(1);
        }
    }
}

package fr.univ_amu.iut.lot.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.TraitementVigieChiro;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.model.dao.DepotPlanDao;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.lot.view.LotController;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import fr.univ_amu.iut.lot.viewmodel.SuiviLignesDepot;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran M-Lot en PNG pour le comparer à la maquette du brief, en illustrant **le workflow
/// du dépôt étape par étape** (#251) :
///
/// - `apercu-lot-preparer.png` : passage **Vérifié** cohérent, étape ① « Vérifier et préparer le dépôt »
///   active ;
/// - `apercu-lot-deposer.png` : passage **Prêt à déposer** (après préparation), étape ② « Générer les
///   archives » active ;
/// - `apercu-lot-televerser-sans-archives.png` : passage **Prêt à déposer**, application **connectée**,
///   **aucune archive** sur le disque : l'étape ③ « Téléverser » est courante et l'étape ② n'est plus un
///   passage obligé (#1998) ;
/// - `apercu-lot-generation.png` : **génération en cours**, indicateur d'activité, bouton désactivé ;
/// - `apercu-lot-archives.png` : **archives générées**, liste des ZIP (redimensionnée à son contenu),
///   « Ouvrir le dossier » et « Supprimer les archives » actifs, étape ③ « Téléverser » courante ;
/// - `apercu-lot-televerser.png` : mêmes archives, **application connectée**, l'étape ③ expose enfin
///   « Téléverser sur Vigie-Chiro » à côté du dépôt manuel (#1890) ;
/// - `apercu-lot-depose.png` : passage **Déposé**, état final, toutes les étapes franchies ;
/// - `apercu-lot-participation.png` : **participation liée**, le bouton de l'étape ④ bascule sur son
///   second libellé, « Lancer la participation » (#1890) ;
/// - `apercu-lot-alertes.png` : passage **Vérifié incohérent** (séquences/journal manquants), la
///   zone d'alertes de cohérence (R14) apparaît et « Préparer le lot » est désactivé ;
/// - `apercu-lot-reprise.png` : **dépôt en cours** (#2354), une archive déjà déposée, une autre dont le
///   `PUT` a rencontré une coupure momentanée : sa ligne porte la mention discrète « Nouvelle tentative
///   dans N s… » à côté de la barre, l'unité restant « en cours ».
///
/// Les aperçus **connectés** ont besoin d'un `Optional<DepotVigieChiro>` non vide : sans lui, l'étape ③
/// et le second mode du bouton ④ ne se rendent pas du tout.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureLot {

    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final String SERIE = "1925492";
    private static final String NUMERO_CARRE = "040962";
    private static final String CODE_POINT = "A1";
    private static final String NOM_SITE = "Étang de la Tuilière";
    private static final Prefixe PREFIXE = new Prefixe(NUMERO_CARRE, 2026, 1, CODE_POINT);
    private static final Prefixe PREFIXE_INCOHERENT = new Prefixe(NUMERO_CARRE, 2026, 2, CODE_POINT);
    private static final String NOM_ORIGINAL = PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260620_213000.wav");
    /// Volumes posés en **base 1000**, celle dans laquelle `Formats` les rend depuis #3573. Écrits en
    /// base 1024, ils affichaient « 189 Mo » là où le littéral disait 180 : le chiffre publié n'était
    /// plus celui qu'on avait choisi, et aucun test ne rougit là-dessus (#3624).
    private static final long VOLUME_SEQUENCES_OCTETS = 180_000_000L;

    /// Racine d'affichage **déterministe** du dossier de session (R22) montrée à l'écran. On
    /// n'utilise pas le `@TempDir` aléatoire : son suffixe se retrouverait dans les PNG commités et
    /// salirait les assets à chaque régénération (le contenu réel n'est de toute façon pas lu).
    private static final String RACINE_DEMO = "/home/observateur/VigieChiro";

    private CaptureLot() {}

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

    /// Identifiant volontairement absent de la base : l'ouverture échoue et l'écran rend son bandeau.
    private static final long PASSAGE_INEXISTANT = 999_999L;

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-lot");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        // Horloge figée : la date de dépôt (marquerDepose) est ainsi **déterministe** dans l'aperçu
        // « déposé » (sinon l'horodatage système changerait le PNG à chaque régénération).
        Injector injecteur = creerInjecteur();
        // Second injecteur, connecté : il partage la même base (le workspace est un chemin, pas un objet),
        // donc il voit les mêmes passages. Seuls les deux aperçus connectés passent par lui.
        Injector connecte = creerInjecteurConnecte();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        ServiceLot service = injecteur.getInstance(ServiceLot.class);

        long idPoint = seederSiteEtPoint(source);
        long idCoherent = seederPassageCoherent(source, idPoint);
        long idIncoherent = seederPassageIncoherent(source, idPoint);

        // Workflow du dépôt illustré étape par étape (#251) :
        // ① Vérifié cohérent : « Vérifier et préparer le dépôt » actif.
        rendre(injecteur, idCoherent, sortie.resolve("apercu-lot-preparer.png"));
        // Après préparation : Prêt à déposer (étape ② à faire), « Générer les archives » actif.
        service.preparerLot(idCoherent);
        rendre(injecteur, idCoherent, sortie.resolve("apercu-lot-deposer.png"));
        // ② bis (#1998) : **connecté et sans archives**. C'est l'état neuf du chantier, le téléversement
        // produisant lui-même ce dont il a besoin, l'étape ③ est courante alors qu'aucune archive n'existe
        // sur le disque. Aucun aperçu ne le montrait : les deux rendus connectés existants partaient tous
        // d'archives déjà générées, donc de l'ancien flux.
        rendre(connecte, idCoherent, sortie.resolve("apercu-lot-televerser-sans-archives.png"));
        // ② Génération des archives en cours : indicateur d'activité, bouton désactivé.
        rendrePilote(
                injecteur,
                idCoherent,
                sortie.resolve("apercu-lot-generation.png"),
                (vm, depot) -> vm.marquerGenerationEnCours());
        // ③ Archives générées : liste des ZIP, « Ouvrir le dossier » actif, étape « Téléverser » courante.
        rendrePilote(
                injecteur,
                idCoherent,
                sortie.resolve("apercu-lot-archives.png"),
                (vm, depot) -> vm.appliquerGeneration(archivesDemo(vm)));
        // ③ bis (#1890) : mêmes archives, mais **connecté**. L'étape « Téléverser sur Vigie-Chiro » n'est
        // visible que si le dépôt est disponible : sans ce rendu, aucun aperçu ne la montrait.
        rendrePilote(
                connecte,
                idCoherent,
                sortie.resolve("apercu-lot-televerser.png"),
                (vm, depot) -> vm.appliquerGeneration(archivesDemo(vm)));
        // #2354 : dépôt EN COURS, une unité réessayée. Une archive déjà déposée, une en cours dont le PUT
        // a rencontré une coupure momentanée : sa ligne porte la mention discrète « Nouvelle tentative
        // dans N s… » (ambre d'avertissement), l'unité restant « en cours ».
        rendrePilote(connecte, idCoherent, sortie.resolve("apercu-lot-reprise.png"), (vm, depot) -> {
            // Dépôt EN COURS : l'écran bascule sur la table de suivi (le prompt « Téléverser… » cède la
            // place), état où la reprise se donne à voir.
            depot.marquerEnCours();
            SuiviLignesDepot lignes = depot.suiviLignes();
            String deposee = "Car040962-2026-Pass1-A1-originaux.zip";
            String enCours = "Car040962-2026-Pass1-A1-sequences.zip";
            lignes.planifier(List.of(
                    DepotUnite.aDeposer(idCoherent, deposee, TypeDepotUnite.ZIP, "2026-06-21T09:00:00"),
                    DepotUnite.aDeposer(idCoherent, enCours, TypeDepotUnite.ZIP, "2026-06-21T09:00:00")));
            lignes.demarree(deposee);
            lignes.deposee(deposee);
            lignes.demarree(enCours);
            lignes.progresse(enCours, 0.4);
            lignes.reprise(enCours, Duration.ofSeconds(3));
        });
        // ④ Déposé : état final, toutes les étapes franchies.
        service.marquerDepose(idCoherent);
        rendre(injecteur, idCoherent, sortie.resolve("apercu-lot-depose.png"));
        // ④ bis (#1890) : participation liée. Le bouton de l'étape ④ bascule sur son second libellé
        // (« 🚀 Lancer la participation ») : jamais rendu jusqu'ici, faute de lien en base.
        lierParticipation(injecteur, idCoherent);
        rendre(connecte, idCoherent, sortie.resolve("apercu-lot-participation.png"));
        // Cas bloquant : Vérifié incohérent → zone d'alertes (R14), « Préparer » désactivé.
        rendre(injecteur, idIncoherent, sortie.resolve("apercu-lot-alertes.png"));
        // Bandeau en ERREUR (#1917) : le succès est déjà couvert par apercu-lot-archives.png, produit par
        // la génération. Ouvrir sur un passage inexistant donne l'autre extrémité de l'échelle.
        rendre(injecteur, PASSAGE_INEXISTANT, sortie.resolve("apercu-lot-retour.png"));
    }

    /// Mémorise un lien `passage → participation` (#1890) : c'est ce que lit `participationLiee`, et
    /// donc ce qui fait basculer le bouton de l'étape ④ de « Marquer déposé » vers « Lancer la
    /// participation ». L'`objectid` est arbitraire mais **fixe** : il ne s'affiche pas, et un
    /// identifiant tiré au hasard rendrait le PNG non reproductible.
    private static void lierParticipation(Injector injecteur, long idPassage) {
        injecteur
                .getInstance(LienVigieChiroDao.class)
                .upsert(new LienVigieChiro(
                        LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), "6480c0ffee0000000000dead", false));
    }

    /// Injecteur de cet outil de capture : la composition **complète** de l'application, surchargée pour
    /// le déterminisme. Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(ModuleCaptureCommun.executeursSynchrones(), new ModuleCaptureLot()));
    }

    /// Variante **connectée** de [#creerInjecteur] (#1890) : mêmes modules, plus la liaison du dépôt.
    ///
    /// Deux injecteurs et non un seul, parce que les deux modes se rendent différemment et que **les
    /// deux méritent d'être relus** : le déconnecté masque l'étape ③ et n'offre que le dépôt manuel.
    /// Tout basculer en connecté aurait simplement déplacé l'angle mort d'un mode à l'autre.
    public static Injector creerInjecteurConnecte() {
        return Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(ModuleCaptureCommun.executeursSynchrones(), new ModuleCaptureLot(), new ModuleDepotConnecte()));
    }

    /// Réglages communs aux deux injecteurs de capture : horloge figée (aperçus reproductibles), DAO de
    /// suivi de dépôt, et un client Vigie-Chiro **jamais appelé**.
    ///
    /// Le client n'existe que pour satisfaire le graphe du dépôt : aucun aperçu ne fait de réseau, seules
    /// `disponible()` (présence de l'objet) et `participationLiee()` (lecture de `lien_vigiechiro` en
    /// base) sont consultées. Son jeton vaut `Optional.empty()`, pour qu'un appel accidentel parte en
    /// erreur franche plutôt que sur le réseau de la machine de capture.
    private static final class ModuleCaptureLot extends AbstractModule {

        @Provides
        Horloge horlogeFigee() {
            return new HorlogeFigee(LocalDateTime.of(2026, 6, 21, 8, 0));
        }

        @Provides
        @Singleton
        ClientVigieChiro clientSansReseau() {
            return new ClientVigieChiro(Optional::empty);
        }

        @Provides
        @Singleton
        TraitementVigieChiro traitementVigieChiro(ClientVigieChiro client) {
            return new TraitementVigieChiro(client);
        }
    }

    /// Rend le dépôt **disponible** dans l'injecteur de capture (#1890).
    ///
    /// Sans cette liaison, `Optional<DepotVigieChiro>` reste vide (défaut posé par `LotModule`) et la
    /// **moitié connectée de l'écran ne se rend pas** : l'étape ③ « Téléverser » est masquée
    /// (`enveloppeTeleverser.setVisible(depotViewModel.disponible())`) et le bouton de l'étape ④ ne
    /// s'affiche jamais dans son second libellé (« 🚀 Lancer la participation »). Aucun aperçu ne
    /// montrait donc cette zone, alors qu'elle porte deux des étapes du flux.
    ///
    /// Calqué sur `DepotVigieChiroModule` (qualifiant intermédiaire pour éviter que la cible de
    /// l'`OptionalBinder` ne se référence elle-même), mais sans exiger de connexion réelle.
    private static final class ModuleDepotConnecte extends AbstractModule {

        private static final String QUALIFIANT = "captureDepotConnecte";

        @Override
        protected void configure() {
            OptionalBinder.newOptionalBinder(binder(), DepotVigieChiro.class)
                    .setBinding()
                    .to(Key.get(DepotVigieChiro.class, Names.named(QUALIFIANT)));
        }

        @Provides
        @Singleton
        @Named(QUALIFIANT)
        DepotVigieChiro depotDeCapture(
                Optional<SynchronisationParticipation> participations,
                ClientVigieChiro client,
                TraitementVigieChiro traitement,
                DepotUniteDao depotUnites,
                DepotPlanDao depotPlans,
                PassageDao passageDao,
                MoteurWorkflowPassage moteurWorkflow,
                Horloge horloge) {
            return new DepotVigieChiro(
                    participations.orElseThrow(() -> new IllegalStateException(
                            "SynchronisationParticipationModule doit être chargé dans l'injecteur de capture")),
                    client,
                    traitement,
                    depotUnites,
                    depotPlans,
                    passageDao,
                    moteurWorkflow,
                    horloge);
        }
    }

    /// Charge `Lot.fxml`, l'ouvre sur le passage puis rend la scène hors-écran en PNG.
    ///
    /// **La hauteur doit contenir tout l'écran.** L'application monte ses vues dans un `ScrollPane`
    /// permanent (`MainController.defilementCentral`) : ce qui dépasse défile. Une capture n'a pas ce
    /// recours - une hauteur trop courte comprime les `Label` en `wrapText`, qui se rabattent sur une
    /// ligne et s'élident. Les consignes des étapes ③ et « Libérer l'espace disque » se terminaient
    /// ainsi par une ellipse, ce qui a été pris pour un défaut de mise en page du produit alors que
    /// c'était un défaut de la capture : elle montrait un écran que l'utilisateur ne voit jamais.
    private static void rendre(Injector injecteur, long idPassage, Path fichier) throws IOException {
        rendrePilote(injecteur, idPassage, fichier, (vm, depot) -> {});
    }

    /// Variante de [#rendre] qui **pilote le ViewModel** après ouverture (états non reflétés par
    /// `consulterLot` : génération en cours, archives produites), via une `controllerFactory` à VM connu.
    private static void rendrePilote(
            Injector injecteur, long idPassage, Path fichier, BiConsumer<LotViewModel, DepotViewModel> pilote)
            throws IOException {
        // 1200 depuis #3464 : le bouton « Copier » posé contre le chemin de dépôt rend cette carte plus
        // haute que son libellé seul, et le garde-fou anti-troncature l'a chiffré - « manque 6 px » sur
        // deux libellés enroulables. La marge est prise au-delà du strict nécessaire : un aperçu qui
        // tient à six pixels près rougira au premier mot ajouté ailleurs.
        rendrePilote(injecteur, idPassage, fichier, 1200, pilote);
    }

    /// Variante à **hauteur de scène** explicite : un état plus chargé (table de dépôt peuplée, #2354)
    /// a besoin de quelques pixels de plus, sinon le garde-fou anti-troncature rejette la capture.
    private static void rendrePilote(
            Injector injecteur,
            long idPassage,
            Path fichier,
            int hauteur,
            BiConsumer<LotViewModel, DepotViewModel> pilote)
            throws IOException {
        LotViewModel vm = injecteur.getInstance(LotViewModel.class);
        // Tenue en local : l'instance n'est pas un singleton de l'injecteur de capture, donc un pilote qui
        // la repêcherait par getInstance en obtiendrait une AUTRE que celle du controller (#2354).
        DepotViewModel depotVm = injecteur.getInstance(DepotViewModel.class);
        FXMLLoader loader = new FXMLLoader(LotController.class.getResource("Lot.fxml"));
        loader.setControllerFactory(type -> type == LotController.class
                ? new LotController(
                        vm,
                        depotVm,
                        injecteur.getInstance(NavigationViewModel.class),
                        injecteur.getInstance(OuvrirSite.class),
                        injecteur.getInstance(OuvrirPassage.class),
                        injecteur.getInstance(OuvreurDeLien.class),
                        injecteur.getInstance(DepotDispositionColonnes.class),
                        injecteur.getInstance(fr.univ_amu.iut.commun.view.ExecuteurTache.class),
                        // Suivi du traitement serveur (#1263) : l'injecteur de capture n'a pas de
                        // `connexion`, donc pas de client : le suivi est absent, et la zone reste masquée.
                        injecteur.getInstance(fr.univ_amu.iut.lot.viewmodel.TraitementViewModel.class))
                : injecteur.getInstance(type));
        Parent vue = loader.load();
        LotController controleur = loader.getController();
        // Capture hors-chrome : le fil d'Ariane n'est pas rendu ; le contexte n'a donc pas à être réel.
        controleur.ouvrirSur(new ContextePassage(idPassage, 1, new ContexteSite(NUMERO_CARRE, CODE_POINT, null)));
        pilote.accept(vm, depotVm);
        // Hauteur généreuse : le flux ordonné à 4 étapes (#251) + la carte « Libérer l'espace disque » (#805)
        // sont hauts ; à l'écran ça défile dans le chrome, mais la capture hors-chrome doit tout rendre
        // (dont le bouton « Supprimer les archives ») sans écraser la zone d'alertes (R14).
        ApercuFx.enregistrerPng(new Scene(vue, 980, hauteur), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Archives ZIP de **démonstration** (#251) pour l'aperçu « archives générées » : on ne zippe pas
    /// réellement (les WAV de la base de démo n'existent pas sur disque), on alimente directement la vue
    /// avec une archive plausible dans le sous-dossier `depot/` de la session.
    private static List<ArchiveDepot> archivesDemo(LotViewModel vm) {
        Path archive = Path.of(vm.cheminDepotProperty().get(), PREFIXE.prefixeFichier() + "1.zip");
        return List.of(new ArchiveDepot(archive, 1, VOLUME_SEQUENCES_OCTETS, 6));
    }

    private static long seederSiteEtPoint(SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        Site site = new SiteDao(source)
                .insert(new Site(
                        null,
                        NUMERO_CARRE,
                        NOM_SITE,
                        Protocole.STANDARD,
                        "Digne-les-Bains",
                        "2026-01-01",
                        ID_UTILISATEUR));
        // GPS cohérent avec le carré 040962 (département 04, Alpes-de-Haute-Provence ; cf. ValidateurCarre).
        return new PointDao(source)
                .insert(new PointDEcoute(null, CODE_POINT, 44.0900, 6.2400, "Près du grand chêne", site.id()))
                .id();
    }

    /// Passage Vérifié entièrement cohérent (6 séquences préfixées + journal) prêt à être préparé.
    private static long seederPassageCoherent(SourceDeDonnees source, long idPoint) {
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));
        Passage passage = insererPassage(source, idPoint, 1, Verdict.OK);
        SessionDEnregistrement session = new SessionDao(source)
                .insert(new SessionDEnregistrement(
                        null,
                        RACINE_DEMO + "/" + PREFIXE.nomDossierSession(),
                        5_000_000_000L,
                        VOLUME_SEQUENCES_OCTETS,
                        passage.id()));
        long idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(
                        null, NOM_ORIGINAL, "bruts/" + NOM_ORIGINAL, 12.0, 384000, null, session.id()))
                .id();
        SequenceDao sequenceDao = new SequenceDao(source);
        for (int i = 0; i < 6; i++) {
            String nom = PREFIXE.nommerSequence(NOM_ORIGINAL, i);
            sequenceDao.insert(new SequenceDEcoute(
                    null, nom, idOriginal, i, i * 5.0, 5.0, "transformes/" + nom, true, session.id()));
        }
        new JournalDuCapteurDao(source)
                .insert(new JournalDuCapteur(null, "LogPR" + SERIE + ".txt", null, null, session.id()));
        return passage.id();
    }

    /// Passage Vérifié incohérent : une session sans séquences ni journal → alertes bloquantes (R14).
    private static long seederPassageIncoherent(SourceDeDonnees source, long idPoint) {
        Passage passage = insererPassage(source, idPoint, 2, Verdict.OK);
        new SessionDao(source)
                .insert(new SessionDEnregistrement(
                        null, RACINE_DEMO + "/" + PREFIXE_INCOHERENT.nomDossierSession(), null, null, passage.id()));
        return passage.id();
    }

    private static Passage insererPassage(SourceDeDonnees source, long idPoint, int numero, Verdict verdict) {
        return new PassageDao(source)
                .insert(new Passage(
                        null,
                        numero,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.VERIFIE,
                        verdict,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE,
                        null));
    }
}

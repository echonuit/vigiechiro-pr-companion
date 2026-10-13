package fr.univ_amu.iut.perf.outils;

import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.bloquerLeFil;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.supprimer;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.surLeFil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FournisseurToken;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ConfirmationsDuBanc;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.multisite.view.NavigationMultisite;
import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.Gel;
import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.Questions;
import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.Releve;
import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.SondeImages;
import fr.univ_amu.iut.recette.FenetreDuBanc;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.function.BooleanSupplier;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.testfx.util.WaitForAsyncUtils;

/// L'application montée pour un banc de l'IHM (#105) : la composition, la fenêtre, la sonde, et ce que
/// l'écran affiché montre.
///
/// Deux liaisons seulement s'écartent de la production, celles de `BancDeRecette` : le jeton se lit dans
/// la réserve du banc et le client vise `localhost:1`. Un banc ne parle à aucun serveur, et un jeton du
/// poste ne doit pas le faire changer de chemin.
///
/// Le banc ne dépend jamais d'un clic : les confirmations du navigateur passent par un confirmateur qui
/// note et refuse ([Questions]), et toute autre fenêtre ouverte arrête le chronomètre ([#intrus]).
final class EcranDuBanc {

    /// Le nombre de relevés publiés après le geste jeté, selon le protocole d'`O5.md`.
    static final int RELEVES = 3;

    static final Path RACINE =
            Path.of(System.getProperty("perf.ihm.racine", "target/banc-ihm")).toAbsolutePath();

    /// Le semis d'un banc : il écrit la base avant que l'écran ne s'ouvre.
    @FunctionalInterface
    interface Semis {
        void semer(Injector injecteur) throws Exception;
    }

    private final Stage stage;
    private final Questions questions = new Questions();
    private Injector injecteur;
    private SondeImages sonde;
    private EchelleDuRendu echelle;

    EcranDuBanc(Stage stage) {
        this.stage = stage;
    }

    Injector injecteur() {
        return injecteur;
    }

    SondeImages sonde() {
        return sonde;
    }

    /// Le confirmateur du banc, à poser aussi sur les écrans qui portent le leur (l'assistant d'import).
    Questions questions() {
        return questions;
    }

    /// Ce qui doit arrêter le banc, ou `null` : une question posée par un écran, ou une fenêtre autre que
    /// la sienne (une modale, quelle qu'elle soit). Lu sur le fil JavaFX.
    String intrus() {
        String question = questions.premiere();
        if (question != null) {
            return question;
        }
        return Window.getWindows().stream()
                .filter(fenetre -> fenetre != stage && fenetre.isShowing())
                .findFirst()
                .map(fenetre -> "une fenêtre s'est ouverte : " + titre(fenetre))
                .orElse(null);
    }

    private static String titre(Window fenetre) {
        return fenetre instanceof Stage autre && autre.getTitle() != null
                ? "« " + autre.getTitle() + " »"
                : fenetre.toString();
    }

    /// Un dossier de travail neuf sous [#RACINE].
    static Path espace(String nom) throws IOException {
        Path espace = RACINE.resolve(nom);
        supprimer(espace);
        Files.createDirectories(espace);
        return espace;
    }

    /// Compose l'application réelle sur `espace`, sème, monte le chrome et pose la sonde. La fenêtre
    /// affichée, il lit l'échelle de son rendu et s'arrête si elle n'est pas 1 sans dérogation
    /// ([EchelleDuRendu#verifier()]) : avant toute mesure, en quelques secondes, au lieu de rendre des
    /// chiffres qui ne se comparent à rien.
    void monter(Path espace, Semis semis) throws Exception {
        arreterLaSonde();
        System.setProperty("vigiechiro.workspace", espace.toString());
        Injector inj =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Provides
                    @Singleton
                    FournisseurToken sourceDuBanc(StockageConnexion reserve) {
                        return reserve;
                    }

                    @Provides
                    @Singleton
                    ClientVigieChiro clientDuBanc(FournisseurToken jetons) {
                        return new ClientVigieChiro("http://localhost:1", jetons);
                    }
                }));
        new MigrationSchema(inj.getInstance(SourceDeDonnees.class)).migrer();
        semis.semer(inj);
        injecteur = inj;
        surLeFil(() -> {
            FXMLLoader chargeur = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
            chargeur.setControllerFactory(DiagnosticGuice.pour(inj));
            Parent racine = chargeur.load();
            FenetreDuBanc.poser(stage, racine, 1280, 860);
            FenetreDuBanc.afficher(stage);
            echelle = EchelleDuRendu.lire(stage);
            ConfirmationsDuBanc.poser(inj.getInstance(Navigateur.class), questions);
            sonde = new SondeImages();
            sonde.start();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        echelle.verifier();
    }

    /// Un relevé ouvert sur l'échelle de la fenêtre montée : il ne s'ouvre qu'après [#monter].
    Releve releve(String titre) {
        return new Releve(RACINE, titre, echelle);
    }

    void arreterLaSonde() throws Exception {
        if (sonde != null) {
            SondeImages enCours = sonde;
            surLeFil(() -> {
                enCours.stop();
                return null;
            });
            sonde = null;
        }
    }

    void reinitialiserLaSonde() throws Exception {
        surLeFil(() -> {
            sonde.reinitialiser();
            return null;
        });
    }

    // ------------------------------------------------------------------ gestes

    void ouvrirMesSites() {
        injecteur.getInstance(NavigationSites.class).ouvrirAccueil();
    }

    void ouvrirMultisite() {
        injecteur.getInstance(NavigationMultisite.class).ouvrirAccueil();
    }

    void ouvrirSons(SourceObservations source) {
        injecteur.getInstance(OuvrirAudio.class).ouvrir(source);
    }

    /// Le temps, en millisecondes, du geste `action` jusqu'au premier pulse où `fini` est vrai.
    double chrono(Runnable action, BooleanSupplier fini) throws Exception {
        return InstrumentsDuBanc.chrono(stage, action, fini, this::intrus);
    }

    /// Ouvre un écran depuis un écran neutre : un geste jeté, puis [#RELEVES] relevés, la première
    /// ouverture (à froid pour cet écran dans ce processus) restant à part sous son nom. Chaque
    /// ouverture porte l'heure du poste à son départ, qui la retrouve dans un enregistrement de la JVM
    /// (#6207).
    void mesurerOuverture(Releve releve, String geste, Runnable neutre, Runnable ouvrir, BooleanSupplier ouvert)
            throws Exception {
        for (int i = 0; i <= RELEVES; i++) {
            ouvrirEtAttendre(neutre, () -> true);
            LocalTime depart = LocalTime.now();
            releve.noter(geste, i, chrono(ouvrir, ouvert), depart);
        }
    }

    void ouvrirEtAttendre(Runnable ouvrir, BooleanSupplier ouvert) throws Exception {
        chrono(ouvrir, ouvert);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Le banc retrouve-t-il un verdict déjà connu ? Un geste qui bloque le fil JavaFX 300 ms doit se
    /// lire à 300 ms au moins, et la sonde doit y voir un écart de plus de 200 ms. Sans ce contrôle, un
    /// chronomètre qui s'arrêterait trop tôt, ou une sonde qui ne verrait jamais rien, passeraient pour
    /// un écran rapide. Puis une fenêtre ouverte exprès, et une question posée exprès, doivent chacune
    /// arrêter le chronomètre : sinon un banc attendrait un clic sans le dire.
    void controlerLInstrument(Releve releve) throws Exception {
        reinitialiserLaSonde();
        WaitForAsyncUtils.waitForFxEvents();
        double ms = chrono(() -> bloquerLeFil(300), () -> true);
        Gel gel = sonde.relever();
        releve.noter("contrôle : geste qui bloque le fil 300 ms", 1, ms);
        releve.noterGel("contrôle : sonde pendant le blocage", gel);
        assertThat(ms)
                .as("le chronomètre doit compter un geste bloquant en entier")
                .isGreaterThanOrEqualTo(300);
        assertThat(gel.gelsMs()).as("la sonde doit voir le blocage de 300 ms").isNotEmpty();

        Stage[] modale = new Stage[1];
        assertThatThrownBy(() -> chrono(
                        () -> {
                            modale[0] = new Stage();
                            modale[0].setTitle("Témoin du banc");
                            modale[0].setScene(new Scene(new Label("témoin")));
                            modale[0].setWidth(240);
                            modale[0].setHeight(100);
                            modale[0].show();
                        },
                        () -> false))
                .as("une fenêtre ouverte doit arrêter le banc, au lieu de le laisser attendre un clic")
                .hasMessageContaining("une fenêtre s'est ouverte");
        surLeFil(() -> {
            modale[0].close();
            return null;
        });
        assertThatThrownBy(() -> chrono(() -> questions.confirmer("témoin"), () -> false))
                .as("une question posée par un écran doit arrêter le banc")
                .hasMessageContaining("l'écran a demandé une confirmation");
        questions.oublier();
    }

    /// Le plancher du chronomètre : une action qui ne fait rien attend quand même le pulse suivant.
    void temoinAVide(Releve releve) throws Exception {
        for (int i = 0; i <= RELEVES; i++) {
            releve.noter("témoin à vide (attente du pulse)", i, chrono(() -> {}, () -> true));
        }
    }

    // ------------------------------------------------------------------ lectures de l'écran

    int lignes(String idTable) {
        return stage.getScene().lookup(idTable) instanceof TableView<?> table
                ? table.getItems().size()
                : -1;
    }

    /// Les sites que « Mes sites » montre : ceux de sa liste, dès qu'une carte au moins est construite.
    /// La liste est virtualisée (#6172) et ne bâtit que les cartes visibles : compter ses nœuds ne
    /// dirait plus combien de sites elle porte.
    int cartesSites() {
        Scene scene = stage.getScene();
        return scene.lookup("#listeCartes") instanceof ListView<?> liste && scene.lookup(".carte-site") != null
                ? liste.getItems().size()
                : -1;
    }

    boolean visible(String id) {
        Node noeud = stage.getScene().lookup(id);
        return noeud != null && noeud.isVisible() && noeud.getParent() != null;
    }

    <T> T noeud(String id, Class<T> type) {
        Node noeud = stage.getScene().lookup(id);
        assertThat(noeud).as("le nœud %s doit être à l'écran", id).isInstanceOf(type);
        return type.cast(noeud);
    }

    /// Un nœud de l'écran affiché, et non du chrome : le chrome porte sa propre recherche globale sous
    /// le même identifiant, `#champRecherche`, et `Scene#lookup` la rendrait en premier.
    <T> T dansLEcran(String id, Class<T> type) {
        Parent vue =
                injecteur.getInstance(Navigateur.class).historique().getLast().vue();
        Node noeud = vue.lookup(id);
        assertThat(noeud).as("le nœud %s doit être dans l'écran affiché", id).isInstanceOf(type);
        return type.cast(noeud);
    }

    /// Le contrôleur de l'écran affiché, pris chez le navigateur qui le détient.
    Object controleurAffiche() {
        return injecteur.getInstance(Navigateur.class).historique().getLast().controleur();
    }
}

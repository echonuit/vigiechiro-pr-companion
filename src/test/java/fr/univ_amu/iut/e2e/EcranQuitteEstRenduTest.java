package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.LinkedKeyBinding;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.audio.view.SonsValidationController;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.EcranReglagesController;
import fr.univ_amu.iut.commun.view.EtapeNavigation;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.NavigationReglages;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.importation.view.ImportationController;
import fr.univ_amu.iut.importation.view.NavigationImportation;
import fr.univ_amu.iut.lot.view.LotController;
import fr.univ_amu.iut.lot.view.NavigationLot;
import fr.univ_amu.iut.multisite.view.NavigationMultisite;
import fr.univ_amu.iut.qualification.view.NavigationQualification;
import fr.univ_amu.iut.qualification.view.QualificationController;
import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import fr.univ_amu.iut.sites.view.MesSitesController;
import fr.univ_amu.iut.sites.view.NavigationSites;
import fr.univ_amu.iut.sites.view.SiteDetailController;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.beans.Observable;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Un écran qui reçoit un singleton observable est rendu au ramasse-miettes une fois quitté (#6184).
///
/// Le premier cas ouvre chaque écran de [EcransSuivantUnSingleton#ECRANS] par la navigation, constate
/// qu'il est vivant, le quitte, et refuse que son contrôleur ou sa vue soient encore tenus. Un écouteur
/// fort posé sur un singleton les tient jusqu'à la fermeture de l'application : c'est ce que faisait la
/// fiche d'un carré sur `EtatConnexion` (#6170), et ce cas rougit quand on le remet.
///
/// Le deuxième confronte la liste des singletons surveillés à la composition réelle : tout singleton lié
/// qui expose une propriété observable est surveillé, ou son écoute est déclarée tenue ailleurs. Le
/// troisième tient la liste des écrans : tout contrôleur de vue à qui la composition remet un singleton
/// surveillé est ouvert par le premier cas, ou déclaré hors historique avec sa raison.
///
/// Aucun geste du robot : TestFX garde dans `FiredEvents` tout événement tiré sur la fenêtre, et un
/// clic retiendrait un écran à lui seul. Chaque écran s'ouvre et se quitte par la navigation.
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class EcranQuitteEstRenduTest {

    private static final String CARRE = "640380";
    private static final String POINT = "A1";

    /// Le nombre de passages demandés au ramasse-miettes avant de déclarer un écran retenu.
    private static final int INSISTANCES = 50;

    /// JUnit crée ce répertoire et le **supprime** en fin de test (#4888).
    @TempDir
    private Path dossierTemporaire;

    private Injector injecteur;
    private JeuDeDonneesPassage jeu;

    @Start
    void start(Stage stage) throws Exception {
        System.setProperty("vigiechiro.workspace", dossierTemporaire.toString());
        injecteur = Guice.createInjector(RacineInjecteur.modules());
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        jeu = JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre(CARRE)
                .nomSite("Étang")
                .point(POINT)
                .position(43.52, 5.46)
                .semer();
        jeu.ajouterObservation("Pippip");

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injecteur));
        Parent racine = loader.load();
        FenetreAjustable.poser(stage, racine, 1100, 760);
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    void chaque_ecran_qui_recoit_un_singleton_observable_est_rendu_une_fois_quitte() throws Exception {
        Map<Class<?>, Consumer<Injector>> ouvertures = ouvertures();
        assertThat(ouvertures.keySet())
                .as("chaque écran de l'inventaire a son ouverture ici, et aucune ouverture n'est hors inventaire")
                .containsExactlyInAnyOrderElementsOf(EcransSuivantUnSingleton.ECRANS);

        for (Map.Entry<Class<?>, Consumer<Injector>> ecran : ouvertures.entrySet()) {
            String nom = ecran.getKey().getSimpleName();
            List<WeakReference<Object>> tenus = ouvrir(ecran.getKey(), ecran.getValue());

            // Témoin : tant qu'il est affiché, le navigateur le tient. Sans cela, une référence faible
            // vide à la fin ne dirait rien de l'écran, seulement que le test ne l'a jamais tenu.
            assertThat(ramasser(tenus, 1))
                    .as("%s affiché doit rester vivant : sinon le test ne tient rien", nom)
                    .hasSize(2);

            quitter(ecran.getKey());

            assertThat(ramasser(tenus, INSISTANCES))
                    .as(
                            "%s quitté doit être rendu, contrôleur et vue : un écouteur fort posé sur un"
                                    + " singleton le garderait jusqu'à la fermeture de l'application",
                            nom)
                    .isEmpty();
        }
    }

    @Test
    void tout_singleton_de_la_composition_qui_s_ecoute_est_surveille_ou_declare() {
        // Un singleton annoté sans liaison explicite n'entre dans la composition qu'à sa première
        // demande. Demander la liaison de chaque écran y fait entrer tout ce qu'il reçoit, sans rien
        // construire : sans cela, la liste lue dépendrait de l'ordre des tests.
        EcransSuivantUnSingleton.ECRANS.forEach(injecteur::getBinding);
        EcransSuivantUnSingleton.HORS_HISTORIQUE.keySet().forEach(injecteur::getBinding);

        Set<String> observables = new TreeSet<>();
        for (Map.Entry<Key<?>, Binding<?>> liaison : injecteur.getAllBindings().entrySet()) {
            Class<?> type = liaison.getKey().getTypeLiteral().getRawType();
            if (Scopes.isSingleton(liaison.getValue())
                    && type.getName().startsWith("fr.univ_amu.iut.")
                    && exposeUnObservable(type)) {
                observables.add(type.getName());
            }
        }

        Set<String> connus = new TreeSet<>();
        EcransSuivantUnSingleton.SINGLETONS_SURVEILLES.forEach(type -> connus.add(type.getName()));
        EcransSuivantUnSingleton.SINGLETONS_TENUS_AILLEURS.keySet().forEach(type -> connus.add(type.getName()));
        assertThat(observables)
                .as("un singleton observable de plus : le surveiller, ou dire où son écoute est tenue")
                .isSubsetOf(connus);
        assertThat(observables)
                .as("la liste ne garde pas un singleton que la composition ne lie plus")
                .containsAll(EcransSuivantUnSingleton.SINGLETONS_SURVEILLES.stream()
                        .map(Class::getName)
                        .collect(Collectors.toSet()));
    }

    @Test
    void tout_controleur_de_vue_qui_recoit_un_singleton_surveille_est_dans_l_inventaire() {
        Set<String> recoivent = new TreeSet<>();
        for (JavaClass classe : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("fr.univ_amu.iut")) {
            if (classe.getSimpleName().endsWith("Controller")
                    && classe.getPackageName().endsWith(".view")
                    && !singletonsSurveillesRecusPar(classe.reflect()).isEmpty()) {
                recoivent.add(classe.getName());
            }
        }

        Set<String> inventaire = new TreeSet<>();
        EcransSuivantUnSingleton.ECRANS.forEach(type -> inventaire.add(type.getName()));
        EcransSuivantUnSingleton.HORS_HISTORIQUE.keySet().forEach(type -> inventaire.add(type.getName()));
        assertThat(recoivent)
                .as("un contrôleur de vue reçoit un singleton surveillé, ou n'en reçoit plus : l'inventaire"
                        + " le nomme, parmi les écrans que le premier cas ouvre ou hors historique avec sa raison")
                .containsExactlyElementsOf(inventaire);
    }

    /// Les singletons surveillés que la composition remet à ce contrôleur, directement ou par un
    /// collaborateur qu'elle lui construit. La descente s'arrête à tout singleton : ce qu'un singleton
    /// reçoit vit autant que lui, et ne retient pas l'écran davantage.
    private Set<Class<?>> singletonsSurveillesRecusPar(Class<?> controleur) {
        Set<Class<?>> recus = new HashSet<>();
        Set<Key<?>> vues = new HashSet<>();
        Deque<Key<?>> aVoir = new ArrayDeque<>();
        aVoir.push(Key.get(controleur));
        while (!aVoir.isEmpty()) {
            Key<?> cle = aVoir.pop();
            if (!vues.add(cle)) {
                continue;
            }
            Class<?> type = cle.getTypeLiteral().getRawType();
            Binding<?> liaison = injecteur.getBinding(cle);
            if (EcransSuivantUnSingleton.SINGLETONS_SURVEILLES.contains(type)) {
                recus.add(type);
            } else if (type == controleur || !Scopes.isSingleton(liaison)) {
                if (liaison instanceof LinkedKeyBinding<?> liee) {
                    aVoir.push(liee.getLinkedKey());
                }
                if (liaison instanceof HasDependencies dependante) {
                    dependante.getDependencies().forEach(dependance -> aVoir.push(dependance.getKey()));
                }
            }
        }
        return recus;
    }

    private static boolean exposeUnObservable(Class<?> type) {
        return Arrays.stream(type.getMethods())
                .filter(methode -> Modifier.isPublic(methode.getModifiers()))
                .map(Method::getReturnType)
                .anyMatch(Observable.class::isAssignableFrom);
    }

    /// Chaque écran de l'inventaire, avec le geste de navigation qui l'ouvre.
    private Map<Class<?>, Consumer<Injector>> ouvertures() {
        ContextePassage passage = new ContextePassage(
                jeu.idPassage(), jeu.lePassage().numeroPassage(), new ContexteSite(CARRE, POINT, null));
        Map<Class<?>, Consumer<Injector>> ouvertures = new LinkedHashMap<>();
        ouvertures.put(
                MesSitesController.class,
                inj -> inj.getInstance(NavigationSites.class).ouvrirAccueil());
        ouvertures.put(
                SiteDetailController.class,
                inj -> inj.getInstance(NavigationSites.class).ouvrirDetail(jeu.leSite()));
        ouvertures.put(
                EcranReglagesController.class,
                inj -> inj.getInstance(NavigationReglages.class).ouvrir());
        ouvertures.put(
                ImportationController.class,
                inj -> inj.getInstance(NavigationImportation.class).ouvrir());
        ouvertures.put(
                SonsValidationController.class,
                inj -> inj.getInstance(OuvrirAudio.class).ouvrir(new SourceObservations.ParPassage(passage)));
        ouvertures.put(
                QualificationController.class,
                inj -> inj.getInstance(NavigationQualification.class).ouvrir(passage));
        ouvertures.put(
                LotController.class, inj -> inj.getInstance(NavigationLot.class).ouvrir(passage));
        return ouvertures;
    }

    /// Ouvre l'écran et rend deux références faibles, sur son contrôleur et sur sa vue, pris chez le
    /// navigateur. Dans sa propre méthode, pour qu'aucune variable locale du test ne les tienne.
    private List<WeakReference<Object>> ouvrir(Class<?> ecran, Consumer<Injector> ouverture) throws Exception {
        surLeFil(() -> ouverture.accept(injecteur));
        EtapeNavigation etape = WaitForAsyncUtils.asyncFx(() ->
                        injecteur.getInstance(Navigateur.class).historique().getLast())
                .get(10, TimeUnit.SECONDS);
        assertThat(etape.controleur())
                .as("l'ouverture doit afficher l'écran qu'elle annonce")
                .isInstanceOf(ecran);
        return List.of(new WeakReference<>(etape.controleur()), new WeakReference<>(etape.vue()));
    }

    /// Quitte l'écran pour une autre racine : Mes sites, ou la vue multisite quand c'est Mes sites qu'on
    /// quitte. Une racine remet l'historique à plat, et l'écran quitté n'y est plus.
    private void quitter(Class<?> ecran) throws Exception {
        if (ecran == MesSitesController.class) {
            surLeFil(() -> injecteur.getInstance(NavigationMultisite.class).ouvrirAccueil());
        } else {
            surLeFil(() -> injecteur.getInstance(NavigationSites.class).ouvrirAccueil());
        }
        boolean encoreLa = WaitForAsyncUtils.asyncFx(() -> injecteur.getInstance(Navigateur.class).historique().stream()
                        .anyMatch(etape -> ecran.isInstance(etape.controleur())))
                .get(10, TimeUnit.SECONDS);
        assertThat(encoreLa)
                .as("l'écran quitté ne doit plus être dans l'historique : sinon c'est le navigateur qui le tient")
                .isFalse();
    }

    /// Insiste auprès du ramasse-miettes, puis rend ce que les références tiennent encore. Un seul
    /// `System.gc()` n'est qu'une demande : un témoin faible sans autre lien doit d'abord tomber, sans
    /// quoi le ramasse-miettes n'a pas tourné et le test ne prouverait rien. Passé le témoin, il
    /// insiste encore `insistances` fois tant qu'une référence tient : un écran se défait en plusieurs
    /// impulsions.
    private static List<Object> ramasser(List<WeakReference<Object>> references, int insistances) {
        WeakReference<Object> temoin = new WeakReference<>(new Object());
        for (int essai = 0;
                temoin.get() != null && essai < INSISTANCES
                        || essai < insistances && references.stream().anyMatch(r -> r.get() != null);
                essai++) {
            System.gc();
            WaitForAsyncUtils.waitForFxEvents();
        }
        assertThat(temoin.get())
                .as("le ramasse-miettes n'a pas tourné : ce test ne prouverait rien")
                .isNull();
        return references.stream()
                .map(WeakReference::get)
                .filter(objet -> objet != null)
                .collect(Collectors.toList());
    }

    private static void surLeFil(Runnable geste) throws Exception {
        WaitForAsyncUtils.asyncFx(geste).get(10, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }
}

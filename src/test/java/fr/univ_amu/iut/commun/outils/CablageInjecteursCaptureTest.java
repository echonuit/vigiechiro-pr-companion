package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.inject.Injector;
import fr.univ_amu.iut.audio.outils.CaptureValidationTadarida;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.commun.view.ExecuteurFiche;
import fr.univ_amu.iut.commun.view.ExecuteurFicheSynchrone;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// Garde-fou de câblage des outils de capture (#510, refondu par #2813).
///
/// Chaque `*.outils.Capture*` assemble un injecteur Guice **partiel** : un sous-ensemble de modules
/// (souvent sans la feature `validation`). Une dépendance socle non liée dans ce sous-ensemble (cf.
/// #509, `CompteurValidations` requis par `ServiceImport` mais fourni seulement par `ValidationModule`)
/// ne se révélait qu'au **run post-merge** du workflow `capture-vues`, une fois le défaut déjà intégré.
///
/// Ce test **construit** chaque injecteur, **sans rendre aucun PNG ni ouvrir de fenêtre**, pour faire
/// échouer un binding manquant dès le quality gate de PR.
///
/// ## Pourquoi les outils sont découverts et non énumérés (#2813)
///
/// La version précédente listait ses cas à la main, avec un avertissement en tête de fichier demandant
/// de tenir la liste à jour. Un avertissement en commentaire n'est pas un garde-fou : au moment de la
/// refonte, **deux outils manquaient**, dont `CaptureSynthese`, dont la Javadoc affirmait pourtant
/// « exposé pour le garde-fou de câblage ». Le commentaire revendiquait une couverture qui n'existait
/// pas, et rien ne pouvait le contredire.
///
/// Les outils sont donc **balayés depuis les sources**, comme le fait déjà `check-capture-mains.sh`.
/// Un outil neuf est couvert d'office ; un outil qui n'expose pas de fabrique doit figurer dans
/// [#EXEMPTES] **avec sa raison**. L'oubli d'une exemption fait rougir, jamais passer en silence.
class CablageInjecteursCaptureTest {

    private static final Path SOURCES = Path.of("src/main/java");

    /// Outils qui ne fabriquent **pas** d'injecteur, et pourquoi. Un outil absent d'ici et sans fabrique
    /// fait échouer [#aucun_outil_n_echappe_au_garde()] : c'est le sens de cette liste, l'oubli se voit.
    private static final Map<String, String> EXEMPTES = Map.ofEntries(
            Map.entry("CaptureMenuOutils", "réutilise l'injecteur de CaptureAccueil, déjà couvert"),
            Map.entry("CaptureMenuLigne", "part de la graine partagée GraineSonsValidation"),
            Map.entry("CaptureCommentaireAudio", "part de la graine partagée GraineSonsValidation"),
            Map.entry("CaptureFicheEspece", "rend un composant isolé, sans injecteur"),
            Map.entry("CaptureDialogues", "rend des dialogues construits à la main, sans injecteur"),
            Map.entry("CaptureCompteRendu", "rend un composant de compte rendu, sans injecteur"),
            Map.entry("CaptureCompteRenduDepot", "rend un composant de compte rendu, sans injecteur"),
            Map.entry("CaptureEcranReglages", "rend l'écran des réglages depuis l'injecteur racine"),
            Map.entry("CaptureImportVigieChiro", "rend une modale construite à la main, sans injecteur"),
            Map.entry("CaptureImportTransformes", "rend une modale construite à la main, sans injecteur"),
            Map.entry("CapturePublicationCorrections", "rend une modale construite à la main, sans injecteur"),
            Map.entry("CaptureConfirmationsImport", "rend des confirmations construites à la main, sans injecteur"));

    private static String workspacePrecedent;

    @BeforeAll
    static void espaceDeTravailJetable() throws IOException {
        // Construire un injecteur peut toucher la persistance : on pointe la propriété lue par le socle
        // (`vigiechiro.workspace`) vers un dossier jetable, et on restaure l'ancienne valeur ensuite pour
        // ne pas fuiter sur les autres tests du même fork.
        workspacePrecedent = System.getProperty("vigiechiro.workspace");
        System.setProperty(
                "vigiechiro.workspace",
                Files.createTempDirectory("vc-cablage-captures").toString());
    }

    @AfterAll
    static void restaurerEspaceDeTravail() {
        if (workspacePrecedent == null) {
            System.clearProperty("vigiechiro.workspace");
        } else {
            System.setProperty("vigiechiro.workspace", workspacePrecedent);
        }
    }

    /// Toutes les classes `Capture*` du dépôt, balayées depuis les sources.
    private static List<Class<?>> outilsDeCapture() {
        try (Stream<Path> arbre = Files.walk(SOURCES)) {
            return arbre.filter(Files::isRegularFile)
                    .filter(chemin -> chemin.getFileName().toString().startsWith("Capture"))
                    .filter(chemin -> chemin.getFileName().toString().endsWith(".java"))
                    .map(CablageInjecteursCaptureTest::classeDe)
                    .sorted(Comparator.comparing(Class::getSimpleName))
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException("Balayage des outils de capture impossible", echec);
        }
    }

    private static Class<?> classeDe(Path source) {
        String nomPleinement =
                SOURCES.relativize(source).toString().replace(".java", "").replace(File.separatorChar, '.');
        try {
            return Class.forName(nomPleinement);
        } catch (ClassNotFoundException echec) {
            throw new IllegalStateException("Classe introuvable pour " + source, echec);
        }
    }

    /// Les fabriques d'injecteur d'un outil : `creerInjecteur`, et ses variantes (`creerInjecteurConnecte`
    /// pour un graphe connecté, qui rend d'autres écrans que le mode déconnecté).
    private static List<Method> fabriques(Class<?> outil) {
        List<Method> trouvees = new ArrayList<>();
        for (Method methode : outil.getDeclaredMethods()) {
            boolean fabrique = Modifier.isStatic(methode.getModifiers())
                    && Modifier.isPublic(methode.getModifiers())
                    && methode.getParameterCount() == 0
                    && Injector.class.equals(methode.getReturnType());
            if (fabrique) {
                trouvees.add(methode);
            }
        }
        trouvees.sort(Comparator.comparing(Method::getName));
        return trouvees;
    }

    private static Supplier<Injector> appel(Method fabrique) {
        return () -> {
            try {
                return (Injector) fabrique.invoke(null);
            } catch (ReflectiveOperationException echec) {
                Throwable cause = echec.getCause() == null ? echec : echec.getCause();
                throw new IllegalStateException(cause);
            }
        };
    }

    static Stream<Arguments> injecteurs() {
        return outilsDeCapture().stream()
                .flatMap(outil -> fabriques(outil).stream()
                        .map(fabrique ->
                                Arguments.of(outil.getSimpleName() + "." + fabrique.getName(), appel(fabrique))));
    }

    @Test
    @DisplayName("#2813 : aucun outil de capture n'échappe au garde, ni par oubli ni par silence")
    void aucun_outil_n_echappe_au_garde() {
        List<Class<?>> outils = outilsDeCapture();

        assertThat(outils)
                .as("le balayage doit trouver les outils : sans matière, tout le reste serait vert pour rien")
                .hasSizeGreaterThan(30);

        List<String> orphelins = outils.stream()
                .filter(outil -> fabriques(outil).isEmpty())
                .map(Class::getSimpleName)
                .filter(nom -> !EXEMPTES.containsKey(nom))
                .toList();

        assertThat(orphelins)
                .as("cet outil n'expose aucune fabrique d'injecteur et n'est pas exempté. S'il en assemble "
                        + "un, exposez `public static Injector creerInjecteur()` ; s'il n'en a pas besoin, "
                        + "déclarez-le dans EXEMPTES avec la raison")
                .isEmpty();

        List<String> exemptionsPerimees = EXEMPTES.keySet().stream()
                .filter(nom ->
                        outils.stream().noneMatch(outil -> outil.getSimpleName().equals(nom)))
                .toList();

        assertThat(exemptionsPerimees)
                .as("exemption pour un outil qui n'existe plus : une liste qu'on ne nettoie pas finit par "
                        + "exempter des noms au hasard")
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("injecteurs")
    void l_injecteur_de_capture_se_construit(String outil, Supplier<Injector> fabrique) {
        assertThatCode(fabrique::get)
                .as("l'injecteur de %s doit se construire : toute dépendance socle doit être liée", outil)
                .doesNotThrowAnyException();
    }

    /// **Tous** les injecteurs de capture : un aperçu se `snapshot` immédiatement après la mise en page,
    /// sans pomper la file d'événements. Avec les exécuteurs asynchrones de production, un écran déporté
    /// sur le socle (#793) serait capturé **pendant** son chargement (voile « Chargement… », tables
    /// vides). Chaque injecteur de capture doit donc résoudre les exécuteurs **synchrones**.
    @ParameterizedTest(name = "{0}")
    @MethodSource("injecteurs")
    void les_executeurs_des_captures_sont_synchrones(String outil, Supplier<Injector> fabrique) {
        Injector injecteur = fabrique.get();
        assertThat(injecteur.getInstance(ExecuteurTache.class))
                .as("%s : ExecuteurTache doit être synchrone (sinon la capture montre le voile d'occupation)", outil)
                .isInstanceOf(ExecuteurTacheSynchrone.class);
        assertThat(injecteur.getInstance(ExecuteurFiche.class))
                .as("%s : ExecuteurFiche doit être synchrone (sinon la capture montre une fiche vide)", outil)
                .isInstanceOf(ExecuteurFicheSynchrone.class);
    }

    @Test
    @DisplayName("#1865 : l'outil du menu ☰ est CONNECTÉ, sinon sa capture perd la moitié plateforme")
    void l_injecteur_du_menu_actions_est_connecte() {
        Injector injecteur = CaptureValidationTadarida.creerInjecteur();

        // MenuAudio.adapter masque les deux entrées quand ces ViewModels sont indisponibles. Remettre un
        // Optional.empty() ici ne casserait ni la compilation ni aucune autre garde : la capture
        // redeviendrait simplement muette, en silence. C'est ce silence que ce test interdit.
        assertThat(injecteur.getInstance(ImportVigieChiroViewModel.class).disponible())
                .as("sans passerelle d'import, « Importer depuis Vigie-Chiro… » disparaît de la capture du ☰")
                .isTrue();
        assertThat(injecteur.getInstance(PublicationCorrectionsViewModel.class).disponible())
                .as("sans passerelle de publication, « Publier les corrections… » disparaît de la capture du ☰")
                .isTrue();
    }
}

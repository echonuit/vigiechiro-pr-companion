package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.inject.Injector;
import fr.univ_amu.iut.analyse.outils.CaptureAnalyse;
import fr.univ_amu.iut.audio.outils.CaptureSonsValidation;
import fr.univ_amu.iut.audio.outils.CaptureSonsValidationColonnes;
import fr.univ_amu.iut.audio.outils.CaptureSonsValidationFiltres;
import fr.univ_amu.iut.audio.outils.CaptureSonsValidationLot;
import fr.univ_amu.iut.audio.outils.CaptureValidationTadarida;
import fr.univ_amu.iut.audit.outils.CaptureAudit;
import fr.univ_amu.iut.commun.view.ExecuteurFiche;
import fr.univ_amu.iut.commun.view.ExecuteurFicheSynchrone;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.connexion.outils.CaptureConnexion;
import fr.univ_amu.iut.diagnostic.outils.CaptureDiagnostic;
import fr.univ_amu.iut.importation.outils.CaptureImport;
import fr.univ_amu.iut.lot.outils.CaptureLot;
import fr.univ_amu.iut.multisite.outils.CaptureMultisite;
import fr.univ_amu.iut.passage.outils.CapturePassage;
import fr.univ_amu.iut.qualification.outils.CaptureQualification;
import fr.univ_amu.iut.recherche.outils.CaptureRecherche;
import fr.univ_amu.iut.sites.outils.CaptureEcrans;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// Garde-fou de câblage des outils de capture (#510).
///
/// Chaque `*.outils.Capture*` assemble un injecteur Guice **partiel** : un sous-ensemble de modules
/// (souvent sans la feature `validation`). Une dépendance socle non liée dans ce sous-ensemble (cf.
/// #509, `CompteurValidations` requis par `ServiceImport` mais fourni seulement par `ValidationModule`)
/// ne se révélait qu'au **run post-merge** du workflow `capture-vues`, une fois le défaut déjà intégré.
///
/// Ce test **construit** chaque injecteur partiel, **sans rendre aucun PNG ni ouvrir de fenêtre**, pour
/// faire échouer un binding manquant dès le quality gate (CI de PR) plutôt qu'en post-merge. Compiler
/// les outils ne suffit pas : le défaut est un câblage Guice *au runtime* (`MissingImplementationError`
/// à la création de l'injecteur), pas une erreur de compilation.
///
/// Les outils au **plein** injecteur (`CaptureAccueil` / `CaptureRecherche` via `RacineInjecteur.creer()`,
/// `CaptureEcrans` via `RacineInjecteur.modules()`) sont déjà couverts par `RacineInjecteurTest` et ne
/// sont pas répétés ici.
///
/// ⚠️ Garder cette liste synchronisée avec `MAINS` de `.github/assets/capture-screenshots.sh` : tout
/// nouvel outil de capture à injecteur partiel doit exposer `public static Injector creerInjecteur()`
/// et être ajouté ci-dessous.
class CablageInjecteursCaptureTest {

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

    /// Un couple (nom lisible, fabrique d'injecteur) par outil de capture à injecteur partiel.
    static Stream<Arguments> injecteursPartiels() {
        return Stream.of(
                Arguments.of("CaptureImport", (Supplier<Injector>) CaptureImport::creerInjecteur),
                Arguments.of("CaptureQualification", (Supplier<Injector>) CaptureQualification::creerInjecteur),
                Arguments.of("CapturePassage", (Supplier<Injector>) CapturePassage::creerInjecteur),
                Arguments.of("CaptureLot", (Supplier<Injector>) CaptureLot::creerInjecteur),
                Arguments.of("CaptureMultisite", (Supplier<Injector>) CaptureMultisite::creerInjecteur),
                Arguments.of("CaptureAnalyse", (Supplier<Injector>) CaptureAnalyse::creerInjecteur),
                Arguments.of("CaptureAudit", (Supplier<Injector>) CaptureAudit::creerInjecteur),
                Arguments.of("CaptureDiagnostic", (Supplier<Injector>) CaptureDiagnostic::creerInjecteur),
                Arguments.of("CaptureSonsValidation", (Supplier<Injector>) CaptureSonsValidation::creerInjecteur),
                Arguments.of("CaptureSonsValidationFiltres", (Supplier<Injector>)
                        CaptureSonsValidationFiltres::creerInjecteur),
                Arguments.of("CaptureSonsValidationColonnes", (Supplier<Injector>)
                        CaptureSonsValidationColonnes::creerInjecteur),
                Arguments.of("CaptureSonsValidationLot", (Supplier<Injector>) CaptureSonsValidationLot::creerInjecteur),
                Arguments.of(
                        "CaptureValidationTadarida", (Supplier<Injector>) CaptureValidationTadarida::creerInjecteur),
                Arguments.of("CaptureConnexion", (Supplier<Injector>) CaptureConnexion::creerInjecteur));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("injecteursPartiels")
    void l_injecteur_partiel_de_capture_se_construit(String outil, Supplier<Injector> fabrique) {
        assertThatCode(fabrique::get)
                .as("l'injecteur partiel de %s doit se construire : toute dépendance socle doit être liée", outil)
                .doesNotThrowAnyException();
    }

    /// **Tous** les injecteurs de capture (partiels + chrome complet) : un aperçu se `snapshot`
    /// immédiatement après la mise en page, sans pomper la file d'événements. Avec les exécuteurs
    /// asynchrones de production, un écran déporté sur le socle (#793) serait capturé **pendant** son
    /// chargement (voile « Chargement… », tables vides). Chaque injecteur de capture doit donc résoudre
    /// les exécuteurs **synchrones** (cf. [ModuleCaptureCommun]).
    static Stream<Arguments> tousLesInjecteurs() {
        return Stream.concat(
                injecteursPartiels(),
                Stream.of(
                        Arguments.of("CaptureAccueil", (Supplier<Injector>) CaptureAccueil::creerInjecteur),
                        Arguments.of("CaptureRecherche", (Supplier<Injector>) CaptureRecherche::creerInjecteur),
                        Arguments.of("CaptureEcrans", (Supplier<Injector>) CaptureEcrans::creerInjecteur)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tousLesInjecteurs")
    void les_executeurs_des_captures_sont_synchrones(String outil, Supplier<Injector> fabrique) {
        Injector injecteur = fabrique.get();
        assertThat(injecteur.getInstance(ExecuteurTache.class))
                .as("%s : ExecuteurTache doit être synchrone (sinon la capture montre le voile d'occupation)", outil)
                .isInstanceOf(ExecuteurTacheSynchrone.class);
        assertThat(injecteur.getInstance(ExecuteurFiche.class))
                .as("%s : ExecuteurFiche doit être synchrone (sinon la capture montre une fiche vide)", outil)
                .isInstanceOf(ExecuteurFicheSynchrone.class);
    }
}

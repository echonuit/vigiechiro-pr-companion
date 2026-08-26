package fr.univ_amu.iut.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.viewmodel.ProgressionOperation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Règles d'architecture (ArchUnit) garanties dès la fondation, complétées par les règles de couches UI
/// du socle MVVM : viewmodel sans JavaFX scene/fxml/stage, vue sans JDBC. Le point d'extension Protocole
/// reste à verrouiller avec la feature correspondante.
///
/// Une feature **peut** dépendre du paquet `model` d'une autre (entités, `model.dao`, services), jamais
/// de son `view` ni de son `viewmodel`, et le graphe de slices `fr.univ_amu.iut.(*)` reste sans cycle.
/// Écrit avec l'API core d'ArchUnit ([ClassFileImporter] + `@Test`) plutôt qu'avec `@AnalyzeClasses`,
/// convention du projet (cf. IMPL-CONVENTIONS).
///
/// **Ce que le vert de ces règles ne couvre pas** (#2181). ArchUnit lit le **bytecode**, et une
/// dépendance qui se réduit à une constante compile-time n'y laisse aucune trace : le compilateur inline
/// la valeur (JLS 13.1) et le `.class` ne référence jamais la classe qui la déclarait. Toutes les règles
/// sont aveugles à cette **catégorie**, qui vaut pour toute `String`, `int` ou `boolean` constant.
/// `cli.commande.Importer` a ainsi cité `importation.viewmodel` sans qu'aucune règle ne bronche.
///
/// [IsolationFeatureSourcesTest] est le doublon au niveau des **sources** de la règle d'isolation
/// inter-feature : l'`import` est dans le `.java` quoi qu'en fasse le compilateur. Les cinq autres
/// règles restent sans jumeau. Un vert d'ici dit « aucune dépendance dans le bytecode ».
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importerLeCodeDeProduction() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("fr.univ_amu.iut");
    }

    @Test
    @DisplayName("Les paquets model ne dépendent pas de JavaFX (réutilisation O6)")
    void model_sans_javafx() {
        noClasses()
                .that()
                .resideInAPackage("..model..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("javafx..")
                .check(classes);
    }

    @Test
    @DisplayName("Le produit ne dépend pas de son outillage : rien hors « outils » n'importe « outils » (#2746)")
    void produit_sans_outillage() {
        // L'outillage (captures de documentation, bancs de mesure, graines de données) est bâti avec le
        // produit mais n'est pas le produit. Il porte 47 points d'entrée `main` qui n'ont rien à faire
        // dans le binaire d'un naturaliste, et il est déjà exclu de la couverture (`**/outils/**`).
        //
        // Cette règle est la condition pour l'en retirer : tant qu'une classe de production en dépend,
        // l'exclusion casserait une fonctionnalité. C'était le cas jusqu'ici, pour un seul franchissement
        // - `ExportGraphe` appelait `ApercuFx.enregistrerPng` pour l'export d'image offert à
        // l'utilisateur (#2352, #2618). Le geste vit désormais dans `commun.view.RenduPng`.
        //
        // Le sens compte : l'outillage a parfaitement le droit de dépendre du produit, c'est même sa
        // raison d'être. C'est l'inverse qui est interdit.
        noClasses()
                .that()
                .resideOutsideOfPackages("..outils..", "..perf..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..outils..", "..perf..")
                .check(classes);
    }

    @Test
    @DisplayName("La lecture brute de l'API n'est appelée que par le groupe « api » de la CLI (#3006)")
    void lecture_brute_reservee_au_groupe_api() {
        // `ClientVigieChiro.lectureBrute` rend le corps sans le nommer ni l'interpréter : c'est
        // l'échappatoire d'exploration, et elle ne doit pas devenir le chemin de moindre résistance
        // pour une capacité nouvelle. Tout le reste du client existe parce qu'un point d'accès NOMMÉ
        // encode une fois pour toutes ce qu'il faut savoir avant d'appeler (plafond de pagination,
        // coordonnées inversées, filtre ignoré par le serveur). Cette règle rend la promesse tenable
        // autrement que par la bonne volonté.
        noClasses()
                .that()
                .resideOutsideOfPackages("..cli.commande.api..", "..commun.api..")
                .should()
                .callMethod(ClientVigieChiro.class, "lectureBrute", String.class)
                .check(classes);
    }

    @Test
    @DisplayName("Un outil de capture pose son temps écoulé, il ne le lit pas à l'horloge (#3483)")
    void capture_pose_son_temps_ecoule() {
        // `ProgressionOperation.appliquer(Progression)` calcule l'estimation du temps restant à partir de
        // l'horloge, depuis la référence posée par `demarrer`. C'est juste pour une opération réelle.
        // Pour une capture, c'est la VITESSE DE LA MACHINE qui entre dans le PNG :
        // `apercu-import-decompression-volume.png` annonçait « ~13 s restant » sur l'intégration continue
        // et « ~15 s » sur un poste, pour le même état posé - un écart que la revue visuelle a mis des
        // semaines à imputer, parce qu'il ressemble à du bruit de rendu.
        //
        // La surcharge `appliquer(Progression, Duration)` reçoit l'écoulé de l'appelant. Cette règle rend
        // le bon geste obligatoire au lieu de le laisser à la vigilance : un aperçu ne se relit pas assez
        // souvent pour qu'un chiffre faux s'y remarque.
        //
        // `..perf..` est exclu à dessein : un banc de mesure lit l'horloge parce que MESURER LA MACHINE
        // est sa raison d'être. Il ne publie pas d'image.
        noClasses()
                .that()
                .resideInAPackage("..outils..")
                .and()
                .resideOutsideOfPackages("..perf..")
                .should()
                .callMethod(ProgressionOperation.class, "appliquer", Progression.class)
                .check(classes);
    }

    @Test
    @DisplayName("La persistance (infra + DAO) ne dépend pas de JavaFX")
    void persistance_sans_javafx() {
        noClasses()
                .that()
                .resideInAnyPackage("..commun.persistence..", "..model.dao..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("javafx..")
                .check(classes);
    }

    @Test
    @DisplayName("La couche viewmodel ne dépend pas de javafx.scene/fxml/stage (javafx.beans OK)")
    void viewmodel_sans_javafx_ui() {
        // Le ViewModel porte l'état observable (javafx.beans.property) mais reste agnostique de
        // l'IHM : aucune dépendance vers les widgets (scene), le FXML ou les fenêtres (stage).
        noClasses()
                .that()
                .resideInAPackage("..viewmodel..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("javafx.scene..", "javafx.fxml..", "javafx.stage..")
                .check(classes);
    }

    @Test
    @DisplayName("La couche view ne touche jamais JDBC (ni model.dao ni java.sql)")
    void view_sans_jdbc() {
        // L'UI passe toujours par les ViewModels / services : elle ne dialogue jamais en direct
        // avec la couche d'accès aux données ni avec l'API JDBC.
        noClasses()
                .that()
                .resideInAPackage("..view..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..model.dao..", "java.sql..")
                .check(classes);
    }

    @Test
    @DisplayName("Les slices fr.univ_amu.iut.* sont sans cycle (hors racine de composition)")
    void features_sans_cycle() {
        // La racine de composition (commun.di) connaît toutes les features : c'est son rôle.
        // On l'exclut de l'analyse de cycles (sinon commun ↔ sites apparaîtrait comme un faux cycle).
        JavaClasses horsRacineDeComposition = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(location -> !location.contains("/commun/di/"))
                .importPackages("fr.univ_amu.iut");

        slices().matching("fr.univ_amu.iut.(*)..").should().beFreeOfCycles().check(horsRacineDeComposition);
    }

    @Test
    @DisplayName("Une feature ne dépend pas du view ni du viewmodel d'une AUTRE feature")
    void pas_de_dependance_inter_feature_vers_la_vue() {
        // Règle volontairement permissive sur model/dao/services (bibliotheque → validation.model,
        // cli → plusieurs services sont légitimes). Seuls view/viewmodel restent privés à leur feature.
        // Exception : le socle `commun.view` (notamment le Navigateur) est PARTAGÉ - les features
        // l'utilisent légitimement pour piloter la zone centrale du chrome (cf. Javadoc Navigateur).
        classes().should(neDependentPasDuViewDuneAutreFeature()).check(classes);
    }

    /// Condition : une classe ne doit dépendre d'aucune classe résidant dans un paquet `view` ou
    /// `viewmodel` appartenant à une **autre** feature (le `view`/`viewmodel` de
    /// sa propre feature reste autorisé). Le socle `commun` est exclu : c'est la couche partagée
    /// (le [fr.univ_amu.iut.commun.view.Navigateur] est explicitement destiné aux features), pas une
    /// feature « voisine ».
    private static ArchCondition<JavaClass> neDependentPasDuViewDuneAutreFeature() {
        return new ArchCondition<>("ne pas dépendre du view/viewmodel d'une autre feature") {
            @Override
            public void check(JavaClass origine, ConditionEvents events) {
                String featureOrigine = feature(origine);
                for (Dependency dependance : origine.getDirectDependenciesFromSelf()) {
                    JavaClass cible = dependance.getTargetClass();
                    String featureCible = feature(cible);
                    if (estVueOuViewModel(cible)
                            && !featureCible.equals(featureOrigine)
                            && !featureCible.equals("commun")) {
                        events.add(SimpleConditionEvent.violated(dependance, dependance.getDescription()));
                    }
                }
            }
        };
    }

    /// Vrai si un segment du paquet de `classe` est `view` ou `viewmodel`.
    private static boolean estVueOuViewModel(JavaClass classe) {
        for (String segment : classe.getPackageName().split("\\.")) {
            if (segment.equals("view") || segment.equals("viewmodel")) {
                return true;
            }
        }
        return false;
    }

    /// Nom de la feature : segment juste après `fr.univ_amu.iut.` (ex. `sites`).
    private static String feature(JavaClass classe) {
        String prefixe = "fr.univ_amu.iut.";
        String paquet = classe.getPackageName();
        if (!paquet.startsWith(prefixe)) {
            return "";
        }
        String reste = paquet.substring(prefixe.length());
        int point = reste.indexOf('.');
        return point < 0 ? reste : reste.substring(0, point);
    }
}

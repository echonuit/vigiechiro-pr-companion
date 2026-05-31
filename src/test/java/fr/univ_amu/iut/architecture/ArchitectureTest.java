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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Règles d'architecture (ArchUnit) garanties dès la fondation, complétées par les règles de
/// couches UI du socle MVVM (viewmodel sans JavaFX scene/fxml/stage, vue sans JDBC). Le point
/// d'extension Protocole reste à verrouiller avec la feature correspondante.
///
/// Dépendances inter-features : une feature PEUT dépendre du paquet `model` d'une autre
/// feature (entités, `model.dao`, services métier), mais JAMAIS de son `view` ni de son
/// `viewmodel`. Le graphe de slices `fr.univ_amu.iut.(*)` reste par ailleurs sans cycle.
///
/// Écrit avec l'API « core » d'ArchUnit ([ClassFileImporter] + `@Test`) plutôt
/// qu'avec `@AnalyzeClasses`/`@ArchTest` : c'est la convention du projet (cf.
/// IMPL-CONVENTIONS).
class ArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importerLeCodeDeProduction() {
    classes =
        new ClassFileImporter()
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
    JavaClasses horsRacineDeComposition =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> !location.contains("/commun/di/"))
            .importPackages("fr.univ_amu.iut");

    slices()
        .matching("fr.univ_amu.iut.(*)..")
        .should()
        .beFreeOfCycles()
        .check(horsRacineDeComposition);
  }

  @Test
  @DisplayName("Une feature ne dépend pas du view ni du viewmodel d'une AUTRE feature")
  void pas_de_dependance_inter_feature_vers_la_vue() {
    // Règle volontairement permissive sur model/dao/services (bibliotheque → validation.model,
    // cli → plusieurs services sont légitimes). Seuls view/viewmodel restent privés à leur feature.
    classes().should(neDependentPasDuViewDuneAutreFeature()).check(classes);
  }

  /// Condition : une classe ne doit dépendre d'aucune classe résidant dans un paquet `view` ou
  /// `viewmodel` appartenant à une **autre** feature (le `view`/`viewmodel` de
  /// sa propre feature reste autorisé).
  private static ArchCondition<JavaClass> neDependentPasDuViewDuneAutreFeature() {
    return new ArchCondition<>("ne pas dépendre du view/viewmodel d'une autre feature") {
      @Override
      public void check(JavaClass origine, ConditionEvents events) {
        String featureOrigine = feature(origine);
        for (Dependency dependance : origine.getDirectDependenciesFromSelf()) {
          JavaClass cible = dependance.getTargetClass();
          if (estVueOuViewModel(cible) && !feature(cible).equals(featureOrigine)) {
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

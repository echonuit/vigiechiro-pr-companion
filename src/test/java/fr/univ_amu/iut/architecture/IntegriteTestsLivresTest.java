package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// Garde d'intégrité : vérifie que les tests « détecteurs de trous » livrés avec le sujet sont
/// **présents** et **actifs**.
///
/// L'audit des forks a montré que certaines équipes **suppriment ou neutralisent** (`@Disabled`,
/// classe vidée) les tests gênants pour verdir leur CI — notamment les tests de vue qui font un
/// vrai `lookup` des `fx:id` et échouent donc sur un écran non câblé. Ce contrôle, exécuté par
/// réflexion (références par nom de classe, pour compiler même si une classe a été supprimée), fait
/// **échouer la CI** dans ce cas, de sorte que le tableau de bord le détecte. Retirer cette garde
/// elle-même reste possible mais visible (son absence est le signal).
@Tag("conformite")
class IntegriteTestsLivresTest {

    /// Tests à NE PAS supprimer ni désactiver (ils détectent les écrans/parcours non implémentés).
    private static final List<String> TESTS_PROTEGES = List.of(
            "fr.univ_amu.iut.sites.view.SitesVueIntegrationTest",
            "fr.univ_amu.iut.importation.view.ImportationVueIntegrationTest",
            "fr.univ_amu.iut.qualification.view.QualificationVueIntegrationTest",
            "fr.univ_amu.iut.passage.view.PassageVueIntegrationTest",
            "fr.univ_amu.iut.validation.view.ValidationVueIntegrationTest",
            "fr.univ_amu.iut.multisite.view.MultisiteVueIntegrationTest",
            "fr.univ_amu.iut.lot.view.LotVueIntegrationTest",
            "fr.univ_amu.iut.diagnostic.view.DiagnosticVueIntegrationTest",
            "fr.univ_amu.iut.bibliotheque.view.BibliothequeVueIntegrationTest",
            "fr.univ_amu.iut.e2e.ParcoursPremiereNuitE2ETest",
            "fr.univ_amu.iut.e2e.ParcoursDeclarerSiteE2ETest",
            "fr.univ_amu.iut.e2e.ParcoursImporterNuitE2ETest",
            "fr.univ_amu.iut.e2e.ParcoursVerifierEchantillonnageE2ETest",
            "fr.univ_amu.iut.e2e.ParcoursRegrouperNuitsParPointE2ETest");

    @Test
    @DisplayName("Les tests livrés (détecteurs de trous) sont présents et actifs (anti-suppression / anti-@Disabled)")
    void tests_livres_presents_et_actifs() {
        List<String> problemes = new ArrayList<>();
        for (String fqcn : TESTS_PROTEGES) {
            Class<?> classe;
            try {
                classe = Class.forName(fqcn);
            } catch (ClassNotFoundException introuvable) {
                problemes.add(fqcn + " : SUPPRIMÉ (classe introuvable sur le classpath de test)");
                continue;
            }
            if (classe.isAnnotationPresent(Disabled.class)) {
                problemes.add(fqcn + " : classe entière @Disabled");
            }
            Method[] methodes = classe.getDeclaredMethods();
            long nbTests = Arrays.stream(methodes)
                    .filter(m -> m.isAnnotationPresent(Test.class))
                    .count();
            if (nbTests == 0) {
                problemes.add(fqcn + " : aucune méthode @Test (classe vidée ?)");
            }
            List<String> desactives = Arrays.stream(methodes)
                    .filter(m -> m.isAnnotationPresent(Test.class) && m.isAnnotationPresent(Disabled.class))
                    .map(Method::getName)
                    .toList();
            if (!desactives.isEmpty()) {
                problemes.add(fqcn + " : @Disabled sur " + desactives);
            }
        }
        assertThat(problemes)
                .as("tests livrés supprimés ou neutralisés — à restaurer (ne pas retirer les tests fournis)")
                .isEmpty();
    }
}

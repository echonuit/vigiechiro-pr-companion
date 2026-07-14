package fr.univ_amu.iut.commun.di;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.inject.Module;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Auto-découverte des modules de feature (#933). Garde-fous du `ServiceLoader<`[ModuleDeFeature]`>` :
/// **toutes** les features sont découvertes (l'oubli d'une entrée `META-INF/services` est le piège
/// n°1), les deux listes (classpath `META-INF/services` et module-path `module-info provides`) sont
/// **synchronisées**, et une feature peut être **désactivée**.
class DecouverteModulesTest {

    /// Les 21 modules de feature attendus (le socle CommunModule/PersistenceModule n'en fait pas
    /// partie : il est installé explicitement, jamais découvert).
    private static final Set<String> FEATURES_ATTENDUES = Set.of(
            "AnalyseModule",
            "AudioModule",
            "AuditModule",
            "ImportVigieChiroModule",
            "PublicationCorrectionsModule",
            "DiscussionModule",
            "BibliothequeModule",
            "ConnexionModule",
            "DiagnosticModule",
            "ImportationModule",
            "DepotVigieChiroModule",
            "LotModule",
            "MultisiteModule",
            "PassageModule",
            "ReconstructionModule",
            "SynchronisationParticipationModule",
            "QualificationModule",
            "RechercheModule",
            "ControleCarreStocModule",
            "SitesModule",
            "SynchronisationSitesModule",
            "ValidationModule");

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.features.desactivees");
        System.clearProperty("vigiechiro.features.analyse");
        System.clearProperty("vigiechiro.features.diagnostic");
        System.clearProperty("vigiechiro.features.importation");
        System.clearProperty("vigiechiro.features.import-vigiechiro");
        System.clearProperty("vigiechiro.features.passage");
        System.clearProperty("vigiechiro.features.lot");
        System.clearProperty("vigiechiro.features.qualification");
        System.clearProperty("vigiechiro.features.recherche");
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("ServiceLoader découvre exactement les modules de feature attendus")
    void serviceloader_decouvre_toutes_les_features() {
        Set<String> decouverts = ServiceLoader.load(ModuleDeFeature.class).stream()
                .map(provider -> provider.type().getSimpleName())
                .collect(Collectors.toSet());

        assertThat(decouverts).containsExactlyInAnyOrderElementsOf(FEATURES_ATTENDUES);
    }

    @Test
    @DisplayName("META-INF/services (classpath) et module-info `provides` (module-path) sont synchronisés")
    void les_deux_declarations_sont_synchronisees() throws IOException {
        ModuleDescriptor descriptor;
        try (InputStream flux = Files.newInputStream(Path.of("target", "classes", "module-info.class"))) {
            descriptor = ModuleDescriptor.read(flux);
        }
        Set<String> viaModuleInfo = descriptor.provides().stream()
                .filter(provides -> provides.service().equals(ModuleDeFeature.class.getName()))
                .flatMap(provides -> provides.providers().stream())
                .collect(Collectors.toSet());

        Set<String> viaServiceLoader = ServiceLoader.load(ModuleDeFeature.class).stream()
                .map(provider -> provider.type().getName())
                .collect(Collectors.toSet());

        assertThat(viaModuleInfo)
                .as("module-info `provides ModuleDeFeature with ...` doit lister exactement les mêmes"
                        + " modules que META-INF/services")
                .isEqualTo(viaServiceLoader);
    }

    @Test
    @DisplayName("le socle est explicite et une feature désactivable l'est par l'alias rétro-compatible")
    void socle_explicite_et_feature_desactivable(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        System.setProperty("vigiechiro.features.desactivees", "ImportVigieChiroModule");

        List<String> noms = RacineInjecteur.modules().stream()
                .map(module -> module.getClass().getSimpleName())
                .toList();

        // Socle toujours présent, en tête, jamais découvert.
        assertThat(noms).startsWith("CommunModule", "PersistenceModule");
        // La feature désactivable désactivée est absente, les autres restent.
        assertThat(noms).doesNotContain("ImportVigieChiroModule").contains("SitesModule", "ConnexionModule");
    }

    @Test
    @DisplayName("sans filtre, modules() = socle (2) + toutes les features")
    void modules_assemble_socle_et_toutes_les_features(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());

        List<Module> modules = RacineInjecteur.modules();

        assertThat(modules).hasSize(2 + FEATURES_ATTENDUES.size());
    }

    @Test
    @DisplayName("chaque module de feature déclare une Fonctionnalite à id unique et non vide")
    void chaque_module_declare_une_fonctionnalite() {
        List<Fonctionnalite> fonctionnalites = ServiceLoader.load(ModuleDeFeature.class).stream()
                .map(provider -> provider.get().fonctionnalite())
                .toList();

        assertThat(fonctionnalites).hasSize(FEATURES_ATTENDUES.size());
        assertThat(fonctionnalites).allSatisfy(f -> {
            assertThat(f.id()).isNotBlank();
            assertThat(f.libelle()).isNotBlank();
            assertThat(f.categorie()).isNotNull();
        });
        assertThat(fonctionnalites).extracting(Fonctionnalite::id).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("garde-fou : une feature COEUR ne peut pas être désactivée, même par propriété système")
    void garde_fou_coeur_non_desactivable(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        System.setProperty("vigiechiro.features.passage", "off");

        List<String> noms = RacineInjecteur.modules().stream()
                .map(module -> module.getClass().getSimpleName())
                .toList();

        assertThat(noms).contains("PassageModule");
    }

    @Test
    @DisplayName("la feuille diagnostic (désormais OPTIONNELLE) est désactivable et l'injecteur se construit")
    void feuille_diagnostic_desactivable(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        System.setProperty("vigiechiro.features.diagnostic", "off");

        List<String> noms = RacineInjecteur.modules().stream()
                .map(module -> module.getClass().getSimpleName())
                .toList();
        assertThat(noms).doesNotContain("DiagnosticModule");

        assertThatCode(RacineInjecteur::creer).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la feuille analyse (désormais OPTIONNELLE) est désactivable et l'injecteur se construit")
    void feuille_analyse_desactivable(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        System.setProperty("vigiechiro.features.analyse", "off");

        List<String> noms = RacineInjecteur.modules().stream()
                .map(module -> module.getClass().getSimpleName())
                .toList();
        assertThat(noms).doesNotContain("AnalyseModule");

        assertThatCode(RacineInjecteur::creer).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la feuille lot (désormais OPTIONNELLE) est désactivable et l'injecteur se construit")
    void feuille_lot_desactivable(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        System.setProperty("vigiechiro.features.lot", "off");

        List<String> noms = RacineInjecteur.modules().stream()
                .map(module -> module.getClass().getSimpleName())
                .toList();
        assertThat(noms).doesNotContain("LotModule");

        assertThatCode(RacineInjecteur::creer).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la feuille qualification (désormais OPTIONNELLE) est désactivable et l'injecteur se construit")
    void feuille_qualification_desactivable(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        System.setProperty("vigiechiro.features.qualification", "off");

        List<String> noms = RacineInjecteur.modules().stream()
                .map(module -> module.getClass().getSimpleName())
                .toList();
        assertThat(noms).doesNotContain("QualificationModule");

        assertThatCode(RacineInjecteur::creer).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la feuille importation (désormais OPTIONNELLE) est désactivable et l'injecteur se construit")
    void feuille_importation_desactivable(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        System.setProperty("vigiechiro.features.importation", "off");

        List<String> noms = RacineInjecteur.modules().stream()
                .map(module -> module.getClass().getSimpleName())
                .toList();
        assertThat(noms).doesNotContain("ImportationModule");

        assertThatCode(RacineInjecteur::creer).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("désactiver import-vigiechiro (propriété système) la retire ; l'injecteur se construit encore")
    void feature_optionnelle_desactivable_par_propriete(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        System.setProperty("vigiechiro.features.import-vigiechiro", "off");

        List<String> noms = RacineInjecteur.modules().stream()
                .map(module -> module.getClass().getSimpleName())
                .toList();
        assertThat(noms).doesNotContain("ImportVigieChiroModule");

        assertThatCode(RacineInjecteur::creer).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la feuille recherche (désormais OPTIONNELLE) est désactivable et l'injecteur se construit")
    void feuille_recherche_desactivable(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        System.setProperty("vigiechiro.features.recherche", "off");

        List<String> noms = RacineInjecteur.modules().stream()
                .map(module -> module.getClass().getSimpleName())
                .toList();
        assertThat(noms).doesNotContain("RechercheModule");

        assertThatCode(RacineInjecteur::creer).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un flag persisté feature.<id>.active=false (app_setting) désactive la feature au démarrage")
    void flag_persiste_desactive_une_feature(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(tmp));
        new MigrationSchema(source).migrer();
        new Reglages(new ReglagesDao(source)).ecrireBooleen("feature.import-vigiechiro.active", false);

        List<String> noms = RacineInjecteur.modules().stream()
                .map(module -> module.getClass().getSimpleName())
                .toList();

        assertThat(noms).doesNotContain("ImportVigieChiroModule").contains("SitesModule");
    }
}

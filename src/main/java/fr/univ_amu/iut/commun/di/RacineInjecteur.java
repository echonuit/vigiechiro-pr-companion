package fr.univ_amu.iut.commun.di;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/// Racine de composition Guice de l'application (composition root).
///
/// **Socle** ([CommunModule] + [PersistenceModule]) : installé **explicitement** (jamais découvert).
/// **Features** : **auto-découvertes** via `ServiceLoader<`[ModuleDeFeature]`>` (#933). Chaque feature
/// se déclare dans `META-INF/services/...ModuleDeFeature` (classpath : tests, fat-jar) **et** dans le
/// `provides ... with ...` de `module-info.java` (module-path : `javafx:run`). Ajouter une feature ne
/// touche donc **plus** cette classe : il suffit d'un `XxxModule extends ModuleDeFeature` + une ligne
/// dans chacune des deux listes (gardées synchronisées par `DecouverteModulesTest`).
///
/// La feature `cli` ne s'installe pas ici : elle crée l'injecteur enfant
/// (`RacineInjecteur.creer().createChildInjector(new CliModule())`) et n'est pas un `ModuleDeFeature`.
///
/// Note d'architecture : ce paquet `commun.di` peut dépendre des features (rôle d'une racine de
/// composition) ; le test `ArchitectureTest` ignore donc `commun.di` dans la détection de cycles.
public final class RacineInjecteur {

    /// Propriété système (noms **simples** de classes, séparés par des virgules) listant les features à
    /// **ne pas** installer, ex. `-Dvigiechiro.features.desactivees=DiagnosticModule`.
    private static final String PROP_DESACTIVEES = "vigiechiro.features.desactivees";

    private RacineInjecteur() {}

    /// Crée l'injecteur applicatif avec tous les modules câblés.
    public static Injector creer() {
        return Guice.createInjector(modules());
    }

    /// Liste des modules applicatifs (socle explicite + features auto-découvertes), **source unique**
    /// de la composition. Exposée pour permettre des **overrides ciblés** sans la dupliquer :
    /// `Modules.override(RacineInjecteur.modules()).with(...)`, utilisé par les outils de capture qui
    /// rendent le **chrome complet** avec une horloge figée ou un service no-op.
    ///
    /// Les features sont triées par **nom de classe** (ordre déterministe, reproductible) : l'ordre
    /// d'installation Guice n'a pas d'effet fonctionnel (les `Set` des points d'extension sont retriés
    /// par `ordre()` côté chrome, et `OptionalBinder.setBinding` l'emporte quel que soit l'ordre).
    public static List<Module> modules() {
        List<Module> modules = new ArrayList<>();
        // Socle : toujours explicite, jamais découvert.
        modules.add(new CommunModule());
        modules.add(new PersistenceModule());
        // Features : auto-découvertes, éventuellement filtrées, triées pour un ordre déterministe.
        Set<String> desactivees = featuresDesactivees();
        ServiceLoader.load(ModuleDeFeature.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(module -> !desactivees.contains(module.getClass().getSimpleName()))
                .sorted(Comparator.comparing(module -> module.getClass().getName()))
                .forEach(modules::add);
        return List.copyOf(modules);
    }

    private static Set<String> featuresDesactivees() {
        String propriete = System.getProperty(PROP_DESACTIVEES, "").trim();
        if (propriete.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(propriete.split(","))
                .map(String::trim)
                .filter(nom -> !nom.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}

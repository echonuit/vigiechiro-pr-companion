package fr.univ_amu.iut.cli;

import com.google.inject.ConfigurationException;
import com.google.inject.Injector;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/// [IFactory] picocli adossée à l'injecteur **Guice** : picocli demande une instance de commande
/// (`@Command`), on la fait **construire par Guice** pour que ses services métier (`@Inject`) soient
/// injectés. picocli renseigne ensuite les champs `@Option`/`@Parameters` par réflexion.
///
/// Repli sur la fabrique par défaut de picocli pour ses propres classes (convertisseurs de types,
/// `HelpCommand`…) que Guice ne saurait pas construire.
final class FabriqueGuice implements IFactory {

    private final Injector injecteur;
    private final IFactory parDefaut = CommandLine.defaultFactory();

    FabriqueGuice(Injector injecteur) {
        this.injecteur = Objects.requireNonNull(injecteur, "injecteur");
    }

    @Override
    public <K> K create(Class<K> cls) throws Exception {
        try {
            return injecteur.getInstance(cls);
        } catch (ConfigurationException nonInjectable) {
            return parDefaut.create(cls);
        }
    }
}

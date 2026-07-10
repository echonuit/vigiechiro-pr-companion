package fr.univ_amu.iut.audio.di;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.ServiceValidation;

/// Liaison **réelle** de l'import VigieChiro (axe 4.2) : pose la valeur de l'`OptionalBinder<ImportVigieChiro>`
/// déclaré (à vide) par [AudioModule]. Chargé **uniquement** dans l'injecteur applicatif complet
/// (`RacineInjecteur`), là où `ClientVigieChiro` est lié (par `ConnexionModule`).
///
/// À part de [AudioModule] à dessein : les injecteurs partiels de capture (`CaptureSonsValidation`,
/// `CaptureValidationTadarida`) assemblent la vue audio **sans `connexion`** et ne chargent pas ce module ;
/// `AudioViewModel` y reçoit un `Optional.empty()` et l'import VigieChiro y est simplement indisponible.
///
/// L'optional vise une **clé qualifiée** (`@Named`) pour éviter l'auto-référence (`RecursiveBinding` /
/// double binding avec le `@Provides`), comme `lot/di/DepotVigieChiroModule`.
public class ImportVigieChiroModule extends AbstractModule {

    private static final String QUALIFIANT = "vigiechiro";

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), ImportVigieChiro.class)
                .setBinding()
                .to(Key.get(ImportVigieChiro.class, Names.named(QUALIFIANT)));
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    ImportVigieChiro fournirImportVigieChiro(
            ClientVigieChiro client, LienVigieChiroDao liens, ServiceValidation service) {
        return new ImportVigieChiro(client, liens, service);
    }
}

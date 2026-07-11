package fr.univ_amu.iut.lot.di;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;

/// Liaison **réelle** du dépôt VigieChiro (#142) : pose la valeur de l'`OptionalBinder<DepotVigieChiro>`
/// déclaré (à vide) par [LotModule]. Ce module n'est chargé que dans l'**injecteur applicatif complet**
/// (`RacineInjecteur`), là où `ClientVigieChiro` est lié (par `ConnexionModule`).
///
/// Il vit **à part** de [LotModule] à dessein : les injecteurs partiels de capture assemblent la feature
/// `lot` sans `connexion` (donc sans client HTTP) et ne chargent pas ce module ; `LotViewModel` y reçoit
/// un `Optional.empty()` et le téléversement y est simplement indisponible, sans binding manquant.
///
/// La liaison de l'optional vise une **clé qualifiée** (`@Named`) plutôt que `DepotVigieChiro` directement,
/// pour éviter que la cible ne se référence elle-même (`RecursiveBinding` / double binding avec le
/// `@Provides`).
public class DepotVigieChiroModule extends AbstractModule {

    private static final String QUALIFIANT = "vigiechiro";

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), DepotVigieChiro.class)
                .setBinding()
                .to(Key.get(DepotVigieChiro.class, Names.named(QUALIFIANT)));
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    DepotVigieChiro fournirDepotVigieChiro(
            @Named(QUALIFIANT) SynchronisationParticipation participations, ClientVigieChiro client) {
        return new DepotVigieChiro(participations, client);
    }
}

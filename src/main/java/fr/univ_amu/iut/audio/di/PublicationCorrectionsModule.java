package fr.univ_amu.iut.audio.di;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.Optional;

/// Liaison **réelle** de la publication des corrections (#723) : pose la valeur de
/// l'`OptionalBinder<PublicationCorrections>`. Chargé **uniquement** dans l'injecteur applicatif
/// complet (`RacineInjecteur`), là où `ClientVigieChiro` est lié (par `ConnexionModule`) : les
/// injecteurs partiels de capture, assemblés sans `connexion`, reçoivent un `Optional.empty()` et la
/// publication y est simplement indisponible. Calqué sur [ImportVigieChiroModule] (même raison
/// d'être, même clé qualifiée `@Named` contre l'auto-référence).
public class PublicationCorrectionsModule extends ModuleDeFeature {

    private static final String QUALIFIANT = "vigiechiro-corrections";

    /// Identité de la feature. `OPTIONNELLE` : ne binde aucun contrat `Ouvrir…`, désactivable en
    /// sécurité (l'item de menu disparaît, l'export `_Vu` reste le repli).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite(
                "publier-corrections", "Publication des corrections vers VigieChiro", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), PublicationCorrections.class)
                .setBinding()
                .to(Key.get(PublicationCorrections.class, Names.named(QUALIFIANT)));
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    PublicationCorrections fournirPublicationCorrections(
            ClientVigieChiro client,
            LienVigieChiroDao liens,
            ObservationDao observations,
            Optional<ImportObservations> importateur) {
        return new PublicationCorrections(client, liens, observations, importateur);
    }
}

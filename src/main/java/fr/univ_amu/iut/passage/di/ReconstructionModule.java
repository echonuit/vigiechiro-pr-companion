package fr.univ_amu.iut.passage.di;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.PointParLocalite;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.HydratationSquelette;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import java.util.Optional;

/// Liaison **réelle** de [ServiceReconstructionPassages] (#1305), patron de
/// `SynchronisationParticipationModule` : la reconstruction d'un passage jamais importé ici a besoin de la
/// **connexion** VigieChiro (elle lit les participations, leur détail et leurs observations). Ce module
/// n'est donc chargé que dans l'app complète (avec `ConnexionModule`) ; hors connexion, l'`OptionalBinder`
/// déclaré vide par `PassageModule` reste vide, et les appelants le disent au lieu d'échouer.
///
/// Le qualificateur `@Named` évite l'auto-référence (`RecursiveBinding`) sur l'`OptionalBinder`.
public class ReconstructionModule extends ModuleDeFeature {

    private static final String QUALIFIANT = "vigiechiro";

    /// Identité de la feature. `COEUR` : socle non désactivable.
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("reconstruction-passages", "Reconstruction des passages", Categorie.COEUR);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), ServiceReconstructionPassages.class)
                .setBinding()
                .to(Key.get(ServiceReconstructionPassages.class, Names.named(QUALIFIANT)));
        // Le service est aussi un rapprocheur de STRUCTURE (#1707) : à la synchro « mes sites », il rapatrie
        // les passages manquants en squelettes. Contribué au Multibinder par la MÊME clé qualifiée (le
        // singleton, pas une seconde instance). Ce module n'étant chargé qu'avec ConnexionModule, le
        // rapprocheur n'apparaît que quand la synchro peut réellement tourner.
        Multibinder.newSetBinder(binder(), RapprochementVigieChiro.class)
                .addBinding()
                .to(Key.get(ServiceReconstructionPassages.class, Names.named(QUALIFIANT)));
        // Hydratation d'une nuit rapatriée (#2555) : même conditionnement que la reconstruction, et pour la
        // même raison (elle lit les observations sur la plateforme). L'OptionalBinder vide de PassageModule
        // reste vide hors connexion, et la réactivation d'un squelette le DIT au lieu d'échouer. Même
        // qualificateur que ci-dessus, pour la même raison : éviter l'auto-référence (`RecursiveBinding`).
        OptionalBinder.newOptionalBinder(binder(), HydratationSquelette.class)
                .setBinding()
                .to(Key.get(HydratationSquelette.class, Names.named(QUALIFIANT)));
    }

    /// L'hydratation d'un squelette (#2555) : elle rapatrie les observations d'une nuit récupérée pour lui
    /// recréer ses séquences, **en place**. Partage les collaborateurs de la reconstruction (client,
    /// espace de travail, port d'import) sans partager son geste : la reconstruction remplace la nuit,
    /// l'hydratation la complète.
    @Provides
    @Singleton
    @Named(QUALIFIANT)
    HydratationSquelette fournirHydratationSquelette(
            SourceDeDonnees source,
            ClientVigieChiro client,
            Workspace workspace,
            Horloge horloge,
            Optional<ImportObservations> importObservations) {
        return new HydratationSquelette(source, client, workspace, horloge, importObservations);
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    ServiceReconstructionPassages fournirServiceReconstructionPassages(
            SourceDeDonnees source,
            ClientVigieChiro client,
            PointParLocalite pointParLocalite,
            Optional<ImportObservations> importObservations,
            Workspace workspace,
            Horloge horloge) {
        return new ServiceReconstructionPassages(
                source, client, pointParLocalite, importObservations, workspace, horloge);
    }
}

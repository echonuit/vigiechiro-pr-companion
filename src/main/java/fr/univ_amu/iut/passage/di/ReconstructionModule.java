package fr.univ_amu.iut.passage.di;

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
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.PointParLocalite;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
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

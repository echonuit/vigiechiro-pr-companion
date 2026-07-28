package fr.univ_amu.iut.passage.di;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.viewmodel.GestionCampagnesViewModel;

/// Module Guice de la feature `campagne` (#2355) : assemble [CampagneDao] et [ServiceCampagne]. Vit
/// dans `passage/di` à côté de [ReconstructionModule] et [SynchronisationParticipationModule] : même
/// patron de **module secondaire de la feature `passage`**, dont le modèle vit dans `passage/model`
/// (c'est le passage qui référencera la campagne). Pas de nouveau dossier-feature.
///
/// `OPTIONNELLE` (désactivable, active par défaut) : le regroupement par campagne est un **outil pour
/// qui en a besoin**, pas un passage obligé. Coupée, la feature retire sa capacité (CLI, colonne,
/// rattachement) sans casser l'injecteur, et l'application reste utilisable sans jamais créer de campagne.
public class CampagneModule extends ModuleDeFeature {

    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("campagne", "Campagnes de suivi", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        // ServiceCampagne rendu OPTIONNEL : la modale « Modifier le passage » (feature `passage`,
        // toujours présente) l'injecte en Optional pour masquer son champ campagne quand la feature est
        // coupée. L'OptionalBinder pointe l'implémentation construite par le @Provides qualifié
        // ci-dessous (garde ServiceCampagne, un service `model`, hors des annotations Guice). Le défaut
        // vide est déclaré par PassageModule, toujours chargé.
        OptionalBinder.newOptionalBinder(binder(), ServiceCampagne.class)
                .setBinding()
                .to(Key.get(ServiceCampagne.class, Names.named("campagne.impl")));
    }

    @Provides
    @Singleton
    CampagneDao fournirCampagneDao(SourceDeDonnees source) {
        return new CampagneDao(source);
    }

    @Provides
    @Singleton
    @Named("campagne.impl")
    ServiceCampagne fournirServiceCampagne(CampagneDao campagneDao, PassageDao passageDao, Horloge horloge) {
        return new ServiceCampagne(campagneDao, passageDao, horloge);
    }

    /// ViewModel de la modale « Gérer les campagnes » (#2630). Il prend le service **sans** `Optional` :
    /// cette modale n'est atteignable que depuis une surface qui a déjà constaté que la feature est
    /// active. Fourni ici, et non par `PassageModule`, pour qu'il disparaisse avec la feature.
    ///
    /// Non-singleton : chaque ouverture repart d'une liste fraîche et d'un bandeau vide.
    @Provides
    GestionCampagnesViewModel fournirGestionCampagnesViewModel(@Named("campagne.impl") ServiceCampagne service) {
        return new GestionCampagnesViewModel(service);
    }
}

package fr.univ_amu.iut.passage.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;

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
        // Aucune contribution au socle (pas de carte d'accueil ni d'onglet) : la campagne se gère
        // depuis la modale « Modifier le passage », les exports et la CLI. Ce module ne fait que
        // fournir les services ci-dessous.
    }

    @Provides
    @Singleton
    CampagneDao fournirCampagneDao(SourceDeDonnees source) {
        return new CampagneDao(source);
    }

    @Provides
    @Singleton
    ServiceCampagne fournirServiceCampagne(CampagneDao campagneDao, PassageDao passageDao, Horloge horloge) {
        return new ServiceCampagne(campagneDao, passageDao, horloge);
    }
}

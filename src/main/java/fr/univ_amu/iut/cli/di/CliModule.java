package fr.univ_amu.iut.cli.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.cli.model.RegistrePassages;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.Optional;

/// Module Guice de la feature transverse `cli`. Assemble les **aides de lecture** propres à la
/// ligne de commande ([RegistrePassages]) à partir des DAO publiés par les autres features
/// (`passage`, `sites`).
///
/// Même patron que `SitesModule` / `LotModule` : une méthode `@Provides @Singleton` câble un
/// objet resté **sans annotation d'injection** (couche `model` indépendante du framework, donc
/// instanciable à la main dans les tests).
///
/// **Installation.** Ce module n'est **pas** ajouté à `RacineInjecteur` (fichier gelé pour cette
/// tâche). La `Cli` l'installe comme **injecteur enfant** de l'injecteur applicatif complet :
/// `RacineInjecteur.creer().createChildInjector(new CliModule())`. L'enfant hérite ainsi de tous
/// les bindings du socle et des features (dont `ServiceImport`, `ServiceLot`,
/// `ServiceValidation`, que la CLI résout directement), et y ajoute les aides propres à la CLI.
/// La dépendance va `cli → <autre>.model.dao` (lecture des DAO), jamais l'inverse : le graphe de
/// features reste acyclique (`ArchitectureTest`).
public class CliModule extends AbstractModule {

    @Provides
    @Singleton
    RegistrePassages fournirRegistrePassages(
            PassageDao passageDao,
            PointDao pointDao,
            SiteDao siteDao,
            PointCommuneDao communesDao,
            ReleveTraitementDao relevesDao,
            ResultatsIdentificationDao resultatsDao,
            Optional<ServiceCampagne> campagnes) {
        return new RegistrePassages(passageDao, pointDao, siteDao, communesDao, relevesDao, resultatsDao, campagnes);
    }
}

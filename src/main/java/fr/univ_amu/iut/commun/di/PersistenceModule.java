package fr.univ_amu.iut.commun.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;

/// Module Guice de l'infrastructure de persistance : expose [MigrationSchema], [UniteDeTravail] et
/// le [UtilisateurDao] transverse, tous construits à partir de la [SourceDeDonnees] du socle.
///
/// Comme `SitesModule`, on passe par `@Provides` pour garder l'infra indépendante du
/// framework d'injection.
public class PersistenceModule extends AbstractModule {

    @Override
    protected void configure() {
        // Tout passe par des @Provides.
    }

    @Provides
    @Singleton
    MigrationSchema fournirMigrationSchema(SourceDeDonnees source) {
        return new MigrationSchema(source);
    }

    @Provides
    UniteDeTravail fournirUniteDeTravail(SourceDeDonnees source) {
        return new UniteDeTravail(source);
    }

    @Provides
    @Singleton
    UtilisateurDao fournirUtilisateurDao(SourceDeDonnees source) {
        return new UtilisateurDao(source);
    }

    @Provides
    @Singleton
    ReglagesDao fournirReglagesDao(SourceDeDonnees source) {
        return new ReglagesDao(source);
    }

    @Provides
    @Singleton
    Reglages fournirReglages(ReglagesDao dao) {
        return new Reglages(dao);
    }

    /// DAO des correspondances locale ↔ VigieChiro (#728), transverse comme [UtilisateurDao] :
    /// alimenté par `connexion` (rapprocheurs) et consommé par les features (taxons, sites, dépôt).
    @Provides
    @Singleton
    LienVigieChiroDao fournirLienVigieChiroDao(SourceDeDonnees source) {
        return new LienVigieChiroDao(source);
    }

    /// Dernier état connu du traitement serveur (#1262) : le cache que le suivi (#1259) tient à jour.
    @Provides
    @Singleton
    ReleveTraitementDao fournirReleveTraitementDao(SourceDeDonnees source) {
        return new ReleveTraitementDao(source);
    }
}

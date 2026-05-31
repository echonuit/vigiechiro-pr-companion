package fr.univ_amu.iut.commun.di;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.importation.di.ImportationModule;
import fr.univ_amu.iut.lot.di.LotModule;
import fr.univ_amu.iut.multisite.di.MultisiteModule;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.qualification.di.QualificationModule;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.validation.di.ValidationModule;

/**
 * Racine de composition Guice de l'application (composition root).
 *
 * <p>C'est le seul endroit qui connaît la liste des modules à assembler : le socle ({@link
 * CommunModule} + {@link PersistenceModule}) et l'ensemble des features (sites, passage,
 * qualification, validation, multisite, importation, lot). Chaque feature publie ses DAO et ses
 * services via son propre module Guice ; cette racine se contente de les installer.
 *
 * <p>Note d'architecture : ce paquet {@code commun.di} dépend des features (il les assemble), ce
 * qui est normal pour une racine de composition. Le test {@code ArchitectureTest} ignore donc
 * explicitement les dépendances issues de {@code commun.di} dans la détection de cycles.
 */
public final class RacineInjecteur {

  private RacineInjecteur() {}

  /** Crée l'injecteur applicatif avec tous les modules câblés. */
  public static Injector creer() {
    return Guice.createInjector(
        new CommunModule(),
        new PersistenceModule(),
        new SitesModule(),
        new PassageModule(),
        new QualificationModule(),
        new ValidationModule(),
        new MultisiteModule(),
        new ImportationModule(),
        new LotModule());
  }
}

package fr.univ_amu.iut.commun.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.nio.file.Path;

/**
 * Module Guice du socle : fournit le {@link Workspace} et la {@link SourceDeDonnees} (singleton).
 *
 * <p>Le workspace est par défaut {@code <Documents>/VigieChiro-Companion} (R21). Pour les tests
 * d'intégration ou une démo jetable, on peut le surcharger via la propriété système {@code
 * vigiechiro.workspace} (ex. {@code -Dvigiechiro.workspace=/tmp/vc}). Les tests unitaires des DAO,
 * eux, instancient directement {@code SourceDeDonnees} sur un {@code @TempDir} sans passer par
 * Guice.
 */
public class CommunModule extends AbstractModule {

  @Override
  protected void configure() {
    // Rien à binder par interface ici : tout passe par des @Provides (besoin de logique).
  }

  @Provides
  @Singleton
  Workspace fournirWorkspace() {
    String surcharge = System.getProperty("vigiechiro.workspace");
    return surcharge != null ? new Workspace(Path.of(surcharge)) : Workspace.parDefaut();
  }

  @Provides
  @Singleton
  SourceDeDonnees fournirSourceDeDonnees(Workspace workspace) {
    return new SourceDeDonnees(workspace);
  }

  /**
   * Horloge applicative : l'horloge système en production. Transverse (les règles de dates R3/R4 et
   * les horodatages des features la réclament), elle est donc bindée au niveau du socle. Les tests
   * n'utilisent pas ce binding : ils injectent directement une {@link
   * fr.univ_amu.iut.commun.model.HorlogeFigee}.
   */
  @Provides
  @Singleton
  Horloge fournirHorloge() {
    return Horloge.systeme();
  }
}

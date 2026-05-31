package fr.univ_amu.iut.sites.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import java.util.Objects;

/// Carte d'accueil de la feature `sites` : ouvre l'écran « Mes sites de suivi ».
///
/// Implémente le contrat du socle [ActiviteAccueil] et délègue l'ouverture à [NavigationSites]
/// (même feature). Enregistrée dans le `Multibinder<ActiviteAccueil>` par
/// [fr.univ_amu.iut.sites.di.SitesModule].
public final class ActiviteMesSites implements ActiviteAccueil {

  private final NavigationSites navigation;

  @Inject
  public ActiviteMesSites(NavigationSites navigation) {
    this.navigation = Objects.requireNonNull(navigation, "navigation");
  }

  @Override
  public int ordre() {
    return 10;
  }

  @Override
  public String icone() {
    return "🗺";
  }

  @Override
  public String titre() {
    return "Mes sites";
  }

  @Override
  public String description() {
    return "Vos carrés et points d'écoute.";
  }

  @Override
  public void ouvrir() {
    navigation.ouvrirAccueil();
  }
}

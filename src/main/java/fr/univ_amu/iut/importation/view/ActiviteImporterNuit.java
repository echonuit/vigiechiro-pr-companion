package fr.univ_amu.iut.importation.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import java.util.Objects;

/// Carte d'accueil de la feature `importation` : ouvre l'assistant « Importer une nuit ».
///
/// Implémente le contrat du socle [ActiviteAccueil] et délègue l'ouverture à
/// [NavigationImportation] (même feature). Enregistrée dans le `Multibinder<ActiviteAccueil>` par
/// [fr.univ_amu.iut.importation.di.ImportationModule].
public final class ActiviteImporterNuit implements ActiviteAccueil {

  private final NavigationImportation navigation;

  @Inject
  public ActiviteImporterNuit(NavigationImportation navigation) {
    this.navigation = Objects.requireNonNull(navigation, "navigation");
  }

  @Override
  public int ordre() {
    return 20;
  }

  @Override
  public String icone() {
    return "📥";
  }

  @Override
  public String titre() {
    return "Importer une nuit";
  }

  @Override
  public String description() {
    return "Une nuit de Passive Recorder.";
  }

  @Override
  public void ouvrir() {
    navigation.ouvrir();
  }
}

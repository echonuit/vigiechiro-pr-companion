package fr.univ_amu.iut.sites.viewmodel;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/// Fraîcheur du dernier passage d'un site, calculée (jamais stockée) pour le badge de la
/// carte M-Sites.
///
/// Trois niveaux dérivés du nombre de jours écoulés depuis le dernier passage importé :
///  - [#FRAIS] : moins de 7 jours (badge vert) ;
///  - [#TIEDE] : entre 7 et 30 jours (badge orange) ;
///  - [#FROID] : plus de 30 jours, ou aucun passage (badge gris).
///
/// C'est une donnée de **présentation** : elle vit donc dans la couche `viewmodel` et reste
/// agnostique de l'IHM (aucun import `javafx.scene`). Le nom de la classe CSS associée
/// ([#classeBadge()]) est juste une chaîne ; c'est la vue qui l'applique réellement.
public enum Fraicheur {
  FRAIS("badge-frais"),
  TIEDE("badge-tiede"),
  FROID("badge-froid");

  /// Seuil (exclu) entre un site « frais » et un site « tiède ».
  private static final int SEUIL_FRAIS_JOURS = 7;

  /// Seuil (exclu) entre un site « tiède » et un site « froid ».
  private static final int SEUIL_TIEDE_JOURS = 30;

  private final String classeBadge;

  Fraicheur(String classeBadge) {
    this.classeBadge = classeBadge;
  }

  /// Nom de la classe CSS appliquée au badge de fraîcheur (appliquée par la vue).
  public String classeBadge() {
    return classeBadge;
  }

  /// Déduit la fraîcheur du nombre de jours écoulés depuis le dernier passage.
  ///
  /// @param joursDepuisDernierPassage nombre de jours (négatif toléré : compté comme frais)
  public static Fraicheur depuisJours(long joursDepuisDernierPassage) {
    if (joursDepuisDernierPassage < SEUIL_FRAIS_JOURS) {
      return FRAIS;
    }
    if (joursDepuisDernierPassage <= SEUIL_TIEDE_JOURS) {
      return TIEDE;
    }
    return FROID;
  }

  /// Déduit la fraîcheur depuis la date du dernier passage (`null` = aucun passage = froid).
  ///
  /// @param dernierPassage date du dernier passage, ou `null` si le site n'en a aucun
  /// @param aujourdhui date du jour (lue de l'horloge applicative, déterministe en test)
  public static Fraicheur depuis(LocalDate dernierPassage, LocalDate aujourdhui) {
    if (dernierPassage == null) {
      return FROID;
    }
    return depuisJours(ChronoUnit.DAYS.between(dernierPassage, aujourdhui));
  }
}

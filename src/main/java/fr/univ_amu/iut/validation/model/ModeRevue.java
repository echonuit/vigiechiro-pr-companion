package fr.univ_amu.iut.validation.model;

/// Mode de revue des observations lors de la validation taxonomique (R18, parcours P7).
///
/// - [#INVENTAIRE] : revue orientée « liste d'espèces ». Une fois une espèce confirmée sur la
///   nuit, les autres détections **non touchées** de la même espèce Tadarida sont propagées
///   automatiquement (mode `auto`, R24) : on arrête de valider cette espèce à la main.
/// - [#ACTIVITE] : revue quantitative. Aucune propagation : chaque observation doit être passée
///   en revue individuellement.
///
/// Le mode est un choix de l'observateur au moment de la revue (sélectionnable par passage), pas
/// une donnée persistée de l'observation. La trace de ce qui a été propagé vit, elle, dans la
/// colonne `validation_mode` de chaque observation (R24 : `manuel` vs `auto`).
public enum ModeRevue {
  INVENTAIRE,
  ACTIVITE
}

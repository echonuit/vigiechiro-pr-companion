package fr.univ_amu.iut.passage.model;

/// Une participation de la plateforme **sans équivalent local** (#1305) : elle existe sur VigieChiro
/// (déposée depuis un autre poste, avant l'application, ou après une réinstallation), mais **rien** de
/// cette nuit n'est en base ici — ni passage, ni observation, ni séquence. C'est la population que les
/// issues A à G ne traitaient pas : elles savent archiver et réactiver un passage **qu'on a eu**.
///
/// @param idParticipation `_id` de la participation sur la plateforme
/// @param numeroCarre carré extrait du titre du site VigieChiro, ou `null` s'il n'a pas pu l'être
/// @param codePoint code de la localité (ex. `Z41`), ou `null`
/// @param dateDebut début de la nuit (ISO 8601), ou `null`
/// @param pointLocalConnu `true` si le carré **et** le point existent déjà localement : la
///     reconstruction est alors possible telle quelle ; sinon, il faut d'abord créer le site et le point
public record ParticipationOrpheline(
        String idParticipation, String numeroCarre, String codePoint, String dateDebut, boolean pointLocalConnu) {}

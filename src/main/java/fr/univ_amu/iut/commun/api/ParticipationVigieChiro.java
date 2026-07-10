package fr.univ_amu.iut.commun.api;

/// Une **participation** de l'observateur telle que listée par `GET /moi/participations` (axe 4.2) : de
/// quoi la reconnaître pour la **rattacher à la main** à un passage local (quand elle n'a pas été créée par
/// l'app, donc sans lien stocké). On expose le strict nécessaire au choix : l'identifiant, la localité, la
/// date de début de nuit et le titre du site (qui porte le numéro de carré).
///
/// @param id `_id` de la participation (à stocker comme `objectid` du lien passage)
/// @param point nom de la localité (ex. `Z41`), ou `null`
/// @param dateDebut début de la nuit, ISO 8601 (`date_debut`), ou `null`
/// @param siteTitre titre du site (ex. `Vigiechiro - Point Fixe-130711`, contient le carré), ou `null`
public record ParticipationVigieChiro(String id, String point, String dateDebut, String siteTitre) {}

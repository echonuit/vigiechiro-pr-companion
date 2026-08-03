package fr.univ_amu.iut.commun.persistence;

/// Une session dont les chemins persistés doivent suivre son dossier, quand une restauration le
/// remet ailleurs (#2727).
///
/// @param id clé de `recording_session`
/// @param idPassage passage associé, `null` s'il n'y en a pas : les résultats d'identification sont
///     rattachés au passage et non à la session, il n'y a alors rien à réécrire de ce côté
record SessionARelocaliser(long id, Long idPassage) {}

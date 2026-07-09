package fr.univ_amu.iut.commun.api;

import java.util.List;

/// Une *donnée* VigieChiro = un **fichier** audio et ses observations Tadarida, renvoyé par
/// `GET /participations/#id/donnees` (#719, axe 4.2). Le `titre` est le nom de fichier (sans extension,
/// ex. `"Car130711-2026-Pass1-Z41-PaRec..._20260703_220529_000"`), qui sert de clé de rattachement à la
/// séquence d'écoute locale de même nom.
///
/// @param titre nom de fichier de la donnée (clé de rattachement à la séquence locale)
/// @param observations détections Tadarida du fichier (éventuellement vide)
public record DonneeVigieChiro(String titre, List<ObservationVigieChiro> observations) {}

package fr.univ_amu.iut.analyse.model;

import java.time.LocalDateTime;

/// Un **point** de la courbe d'activité : le nombre de contacts d'une espèce tombés dans une tranche
/// horaire (#2352). Le début de tranche est **aligné sur l'horloge** (top d'heure, demie ou quart), jamais
/// sur un décalage arbitraire, pour que deux nuits se comparent tranche à tranche.
///
/// On porte l'instant complet (date + heure) et non l'heure seule : une nuit court du soir `J` au matin
/// `J+1`, et deux tranches à cheval sur minuit doivent rester dans l'ordre chronologique (23:30 avant
/// 00:00 de la nuit suivante).
///
/// @param debutTranche début de la tranche, aligné sur l'horloge, instant réel
/// @param nombre nombre de contacts dans la tranche (toujours strictement positif : les tranches vides ne
///     produisent pas de point)
public record PointActivite(LocalDateTime debutTranche, int nombre) {}

package fr.univ_amu.iut.commun.api;

/// Compte-rendu d'un rapprochement VigieChiro (#717) : ce qui a été synchronisé, pour le montrer à
/// l'utilisateur après une connexion (« référentiel à jour : N taxons »). Renvoyé par
/// [RapprochementVigieChiro#synchroniser(ClientVigieChiro)] quand une synchro a effectivement eu lieu.
///
/// @param libelle nature synchronisée, au pluriel (ex. `"taxons"`, `"sites"`)
/// @param nombre nombre d'éléments synchronisés
public record RapportSynchro(String libelle, int nombre) {}

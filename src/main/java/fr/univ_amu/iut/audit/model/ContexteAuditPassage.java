package fr.univ_amu.iut.audit.model;

/// Identité du site et du point d'un passage, pour **ouvrir ce passage depuis un constat d'audit** (#1347).
///
/// Un constat cite un passage par son `id`. L'écran pivot M-Passage, lui, attend le contexte de son site
/// (carré, code du point, nom) pour composer son fil d'Ariane — c'est le contrat socle `OuvrirPassage`.
///
/// Ce record vit dans le **modèle** de la feature `audit` (et non dans son `viewmodel`) parce que c'est le
/// service d'audit qui sait le résoudre : il tient déjà le `PointDao` et le `SiteDao`, dont il se sert pour
/// recalculer le préfixe attendu. La vue le traduit en `ContexteSite` du socle.
///
/// @param numeroCarre numéro de carré du site (ex. `130711`)
/// @param codePoint code du point d'écoute (ex. `Z41`)
/// @param nomSite nom convivial du site, ou `null`
public record ContexteAuditPassage(String numeroCarre, String codePoint, String nomSite) {}

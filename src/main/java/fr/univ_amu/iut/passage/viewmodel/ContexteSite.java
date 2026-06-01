package fr.univ_amu.iut.passage.viewmodel;

/// Contexte d'identité du site/point fourni à l'écran M-Passage par la navigation (depuis
/// M-Site-detail).
///
/// Permet d'afficher le carré et le code du point **sans** que la feature `passage` dépende de
/// `sites` (ce qui formerait un cycle avec `sites → passage`, déjà présent). L'écran appelant, qui
/// connaît déjà le site courant, transmet ces libellés au moment d'ouvrir M-Passage.
///
/// @param numeroCarre numéro de carré Vigie-Chiro du site (ex. `640380`)
/// @param codePoint code du point d'écoute (ex. `A1`)
/// @param nomSite nom convivial du site, ou `null`
public record ContexteSite(String numeroCarre, String codePoint, String nomSite) {}

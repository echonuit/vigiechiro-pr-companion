package fr.univ_amu.iut.passage.model;

import java.util.OptionalDouble;

/// Verdict d'une séquence **régénérée depuis le brut désigné** (#1682) : le [VerdictIdentite]
/// (structurel, jamais un veto acoustique) et la **concordance acoustique mesurée** en indice.
///
/// La concordance est un `OptionalDouble` : vide quand aucun cri n'était mesurable (séquence sans
/// observation, ou cris hors fenêtre) ; sinon la fraction des cris retrouvés (0..1). Elle n'entre pas
/// dans la décision d'accepter : voir [IndiceAcoustique].
///
/// @param verdict identité structurelle de la séquence (accepté sur nom et durée, ou refusé)
/// @param concordanceCris fraction des cris attendus retrouvés (indice), vide si non mesurable
public record VerdictRegenere(VerdictIdentite verdict, OptionalDouble concordanceCris) {}

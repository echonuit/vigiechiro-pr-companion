package fr.univ_amu.iut.saison.model;

/// Une ligne du solde de saison : un **point suivi**, l'état de ses deux passages attendus, et la
/// phrase d'action **« reste à faire »**. C'est cette dernière colonne qui transforme un tableau de
/// suivi en plan de travail : un état se décrit, une action se fait (#2356).
///
/// Le carré et le code du point donnent l'identité affichée ; `idPoint` sert au double-clic (ouvrir
/// le point quand il n'a pas encore de passage).
///
/// @param numeroCarre numéro du carré (site) auquel appartient le point
/// @param codePoint code du point d'écoute dans le carré
/// @param idPoint identifiant technique du point (pour l'ouverture)
/// @param passage1 état du premier passage (présent ou absent)
/// @param passage2 état du second passage (présent ou absent)
/// @param resteAFaire phrase de l'action à mener sur ce point, ou chaîne **vide** si le point est à
///     jour
public record LigneSaison(
        String numeroCarre,
        String codePoint,
        Long idPoint,
        CasePassage passage1,
        CasePassage passage2,
        String resteAFaire) {

    /// Vrai si le point est **à jour** : plus aucune action à mener (les deux passages sont terminés).
    public boolean aJour() {
        return resteAFaire.isEmpty();
    }
}

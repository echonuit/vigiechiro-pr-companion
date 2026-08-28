package fr.univ_amu.iut.cli.commande;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.sites.model.ControleCarreLocal;
import picocli.CommandLine.Model.CommandSpec;

/// Dire une divergence de carré sur la sortie d'**erreur**, sans changer le code de sortie (#4682).
///
/// Deux commandes posent des coordonnées sur un point - `ajouter-point` et `modifier-point` - et l'écran
/// contrôle depuis #733 ce qu'elles ne contrôlaient pas. Le geste est ici plutôt que dupliqué dans les
/// deux : c'est la même phrase, et deux copies finiraient par ne plus l'être.
///
/// **Sur stderr, et sans toucher au code de sortie.** `ajouter-point` écrit l'identifiant du point sur la
/// sortie standard pour que `POINT=$(vigiechiro ajouter-point ...)` marche : y ajouter un avertissement
/// casserait tous les scripts qui l'emploient. Et le contrôle est un confort, jamais une condition -
/// refuser la saisie parce qu'un carré étonne serait le transformer en garde, ce qu'il n'est pas.
final class AvertissementCarre {

    private AvertissementCarre() {}

    /// Écrit le message du verdict si, et seulement si, il y a quelque chose à signaler.
    ///
    /// Une concordance ne se dit pas : elle serait du bruit à chaque appel. Un point sans coordonnées ou
    /// un site sans carré ne se disent pas non plus - il n'y a rien à confronter, et ce n'est pas une
    /// anomalie.
    static void direSiDivergence(
            CommandSpec spec, ControleCarreLocal controle, String carreDeclare, Double latitude, Double longitude) {
        controle.confronter(carreDeclare, latitude, longitude)
                .filter(verdict -> verdict.severite() == Severite.AVERTISSEMENT)
                .ifPresent(verdict -> spec.commandLine().getErr().println(verdict.message()));
    }
}

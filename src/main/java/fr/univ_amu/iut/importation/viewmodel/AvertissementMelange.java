package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.AnalyseMelange;

/// Rédige l'avertissement **« mélange »** (#33) destiné à l'utilisateur à partir d'une
/// [AnalyseMelange]. Extrait du [ImportationViewModel] pour garder ce dernier focalisé sur l'état de
/// l'assistant : la mise en phrase (présentation) est une responsabilité à part, mais reste dans la
/// couche `viewmodel` (le `model` ne porte pas de texte d'IHM, à l'image de `messageErreur` ou
/// `resumeJournal`, construits côté VM).
///
/// L'avertissement ne vise plus que le mélange de **plusieurs enregistreurs** (séries différentes sur
/// une même carte), qui reste **non géré** (un import correspond à un seul enregistreur). Le cas
/// **plusieurs nuits** d'un même enregistreur n'est **plus** un avertissement : il est pris en charge
/// par le **découpage par nuit** (un passage par nuit), signalé par la table des nuits de l'assistant.
final class AvertissementMelange {

    private AvertissementMelange() {}

    /// Construit l'avertissement à afficher, ou une chaîne **vide** si le dossier ne mélange pas
    /// plusieurs enregistreurs (dossier d'un seul enregistreur, même sur plusieurs nuits : cas géré). Le
    /// message est informatif : il n'empêche pas l'import.
    static String rediger(AnalyseMelange melange) {
        if (!melange.plusieursEnregistreurs()) {
            return "";
        }
        // La phrase se lit d'un bloc plutôt que par morceaux : ce qui varie est visible à sa place, et
        // le seul fragment optionnel est nommé au lieu d'être un `append` conditionnel au milieu.
        String surPlusieursNuits = melange.plusieursNuits()
                ? String.format(
                        ", sur plusieurs nuits (%d dates)", melange.nuits().size())
                : "";
        return String.format(
                "⚠ Ce dossier mélange plusieurs enregistreurs (séries %s)%s : un import correspond à un seul"
                        + " enregistreur, vérifiez la source avant d'importer.",
                String.join(", ", melange.series()), surPlusieursNuits);
    }
}

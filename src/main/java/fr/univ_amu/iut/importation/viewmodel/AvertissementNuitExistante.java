package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.PassageExistant;
import java.util.List;
import java.util.stream.Collectors;

/// Rédige l'avertissement **« nuit déjà importée »** (#147) destiné à l'utilisateur à partir des
/// passages déjà en base pour le même enregistreur et la même date.
///
/// Même esprit que [AvertissementMelange] / [AvertissementIncoherence] : la mise en phrase
/// (présentation) est une responsabilité à part, dans la couche `viewmodel`. L'avertissement est
/// **informatif** (non bloquant) : réimporter une nuit déjà présente crée simplement un nouveau passage,
/// ce que l'utilisateur peut vouloir (autre point / autre numéro) ou non.
final class AvertissementNuitExistante {

    private AvertissementNuitExistante() {}

    /// Construit l'avertissement, ou une chaîne **vide** si aucun passage n'existe pour cette nuit.
    static String rediger(String numeroSerie, String dateNuit, List<PassageExistant> existants) {
        if (existants.isEmpty()) {
            return "";
        }
        String details = existants.stream()
                .map(passage -> "n° " + passage.numeroPassage() + " (" + passage.annee() + ")")
                .collect(Collectors.joining(", "));
        String motPassage = existants.size() == 1 ? " : passage " : " : passages ";
        return "⚠ Cette nuit (PR " + numeroSerie + ", " + dateNuit + ") a déjà été importée" + motPassage + details
                + ". L'importer créera un nouveau passage (vérifiez le rattachement).";
    }
}

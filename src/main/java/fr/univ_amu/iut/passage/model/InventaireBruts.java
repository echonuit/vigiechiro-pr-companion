package fr.univ_amu.iut.passage.model;

import java.util.List;

/// Ce qu'un dossier de bruts livre pour **hydrater** un passage reconstruit (#1649, EPIC #1653) : la
/// **fréquence d'acquisition** lue du log et la liste des bruts à ré-intégrer.
///
/// La fréquence est ici parce qu'elle est **une par nuit** (le log en donne une seule) et qu'elle
/// pilote la transformation à l'identique : sans elle, aucune tranche ne peut être régénérée avec le
/// bon découpage à 5 s réelles. La porter à côté des bruts, plutôt que sur chaque brut, dit qu'elle
/// vaut pour toute la nuit.
///
/// @param frequenceAcquisitionHz la vraie `Fe` (celle du log, pas de l'en-tête), en Hz
/// @param bruts les bruts inventoriés (au moins un : un inventaire vide n'aurait rien à hydrater)
public record InventaireBruts(int frequenceAcquisitionHz, List<BrutInventorie> bruts) {

    public InventaireBruts {
        bruts = List.copyOf(bruts);
    }
}

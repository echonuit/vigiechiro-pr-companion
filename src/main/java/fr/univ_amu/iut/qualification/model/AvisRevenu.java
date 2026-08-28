package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.commun.model.VerdictFichier;
import java.util.Map;
import java.util.Objects;

/// L'avis qu'un relecteur renvoie, tel qu'il sort du paquet et avant d'être confronté à la sélection
/// (#4627).
///
/// Le pseudo est celui relevé à l'ouverture du paquet, jamais au moment du jugement : le pourquoi
/// est dans [fr.univ_amu.iut.passage.model.PaquetOuvert].
///
/// @param pseudoRelecteur le nom lisible de qui a jugé
/// @param verdicts le verdict rapporté pour chaque séquence, par identifiant
public record AvisRevenu(String pseudoRelecteur, Map<Long, VerdictFichier> verdicts) {

    public AvisRevenu {
        Objects.requireNonNull(pseudoRelecteur, "pseudoRelecteur");
        verdicts = Map.copyOf(Objects.requireNonNull(verdicts, "verdicts"));
    }
}

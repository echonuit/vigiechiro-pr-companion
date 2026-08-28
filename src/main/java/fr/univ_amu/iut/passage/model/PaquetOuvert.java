package fr.univ_amu.iut.passage.model;

import java.util.List;
import java.util.Objects;

/// Un paquet d'emport ouvert par un relecteur, et **signé de lui** (#4626, ADR 4517).
///
/// Le pseudo est relevé à l'ouverture et figé ici : l'identité locale expire avec le jeton de la
/// plateforme, et le relire au moment du jugement rendrait anonyme un avis posé deux semaines plus
/// tard.
///
/// @param pseudoRelecteur le nom lisible de qui a ouvert, jamais son identifiant de plateforme
/// @param manifeste le texte du manifeste tel que l'expéditeur l'a écrit
/// @param sequences les entrées de séquences que le paquet porte, dans l'ordre de l'archive
public record PaquetOuvert(String pseudoRelecteur, String manifeste, List<String> sequences) {

    public PaquetOuvert {
        Objects.requireNonNull(pseudoRelecteur, "pseudoRelecteur");
        Objects.requireNonNull(manifeste, "manifeste");
        sequences = List.copyOf(Objects.requireNonNull(sequences, "sequences"));
    }
}

package fr.univ_amu.iut.passage.model;

import java.util.List;
import java.util.Objects;

/// Le matériel micro relu d'une participation, et ce que la lecture n'a pas su prendre (#6211, #6228).
///
/// Une hauteur ou une position que le produit ne sait pas traduire est laissée vide dans le matériel
/// local, sans rien effacer sur la plateforme. Elle se dit pourtant : `nonRelus` nomme chaque valeur
/// écartée, telle qu'elle est écrite, pour que la récupération et la reconstruction l'annoncent dans leur
/// bilan au lieu de la taire.
///
/// @param materiel ce qui a été relu, à écrire localement
/// @param nonRelus une ligne par valeur écartée, ex. `hauteur du micro « 2-3 »` ; vide si tout a été relu
public record MaterielRelu(MaterielMicro materiel, List<String> nonRelus) {

    public MaterielRelu {
        Objects.requireNonNull(materiel, "materiel");
        nonRelus = List.copyOf(nonRelus);
    }
}

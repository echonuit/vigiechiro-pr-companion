package fr.univ_amu.iut.commun.model;

import java.util.Objects;

/// Un passage **désigné** pour un traitement groupé (#2357) : son identifiant, et de quoi le nommer
/// dans le journal et le compte rendu.
///
/// La `designation` est ce que l'observateur lit (« 640380 / A1 / 2026 n°1 »). Elle est portée par la
/// cible plutôt que reconstruite par le moteur : celui-ci ne sait rien des carrés ni des points, et
/// c'est précisément ce qui le rend réutilisable, et testable sans base.
///
/// @param idPassage identifiant technique du passage
/// @param designation libellé lisible du passage, jamais vide (il préfixe chaque ligne de journal)
public record CiblePassage(Long idPassage, String designation) {

    public CiblePassage {
        Objects.requireNonNull(idPassage, "idPassage");
        if (designation == null || designation.isBlank()) {
            throw new IllegalArgumentException("designation vide : un passage doit rester nommable dans le journal");
        }
    }
}

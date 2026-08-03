package fr.univ_amu.iut.commun.persistence;

import java.util.Objects;

/// Où une racine de son a été remise par une restauration complète (#2727).
///
/// @param origine chemin d'où elle venait, tel que le manifeste l'a conservé
/// @param destination chemin où elle se trouve maintenant, et vers lequel `root_path` pointe
public record PlacementRacine(String origine, String destination) {

    public PlacementRacine {
        Objects.requireNonNull(origine, "origine");
        Objects.requireNonNull(destination, "destination");
    }

    /// `true` si la racine n'a **pas** retrouvé son emplacement d'origine : son disque n'était pas
    /// là, et elle a été placée dans le workspace. C'est le cas qui mérite d'être dit à
    /// l'utilisateur, puisque ses gigaoctets ont changé de disque.
    public boolean deplacee() {
        return !origine.equals(destination);
    }
}

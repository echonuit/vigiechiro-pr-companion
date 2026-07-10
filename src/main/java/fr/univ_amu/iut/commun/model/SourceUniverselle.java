package fr.univ_amu.iut.commun.model;

import java.util.Optional;

/// Source d'information **universelle**, adressable par **nom latin** : le repli de
/// [ConstructeurLienEspece] pour tous les taxons que la fiche spécialisée chiroptères (PNA) ne couvre
/// pas (oiseaux, orthoptères, niveaux genre, couples).
///
/// Abstraite (interface fonctionnelle) pour rester **interchangeable** : l'implémentation par défaut
/// vise GBIF ([LienGbif]), mais une variante Wikipédia FR se substitue sans toucher au constructeur
/// (même entrée « nom latin »).
@FunctionalInterface
public interface SourceUniverselle {

    /// URL de la fiche pour le `nomLatin` donné, ou vide si aucun lien n'est constructible (nom latin
    /// absent ou vide).
    Optional<String> lienPourNomLatin(String nomLatin);
}

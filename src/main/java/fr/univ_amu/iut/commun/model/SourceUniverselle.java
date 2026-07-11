package fr.univ_amu.iut.commun.model;

import com.google.inject.ImplementedBy;
import java.util.Optional;

/// Source d'information **universelle**, adressable par **nom latin** : le repli de
/// [ConstructeurLienEspece] pour tous les taxons que la fiche spécialisée chiroptères (PNA) ne couvre
/// pas (oiseaux, orthoptères, niveaux genre, couples).
///
/// Abstraite (interface fonctionnelle) pour rester **interchangeable** : l'implémentation par défaut
/// vise GBIF ([LienGbif]), mais une variante Wikipédia FR se substitue sans toucher au constructeur
/// (même entrée « nom latin »).
///
/// [ImplementedBy] fixe **GBIF** comme défaut d'injection (comportement historique, utile aux tests qui
/// n'ont pas de module de préférence) ; l'application complète surcharge ce défaut par la
/// [SourceUniversellePreferee] pilotée par la préférence utilisateur (`CommunModule`, #849).
@FunctionalInterface
@ImplementedBy(LienGbif.class)
public interface SourceUniverselle {

    /// URL de la fiche pour le `nomLatin` donné, ou vide si aucun lien n'est constructible (nom latin
    /// absent ou vide).
    Optional<String> lienPourNomLatin(String nomLatin);
}

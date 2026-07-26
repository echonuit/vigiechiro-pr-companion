package fr.univ_amu.iut.passage.model;

import java.time.LocalDate;
import java.util.Optional;

/// Fenêtre calendaire fermée `[debut, fin]` pendant laquelle un passage est attendu (règle R3,
/// protocole `PointFixeStandard`). La table des fenêtres est définie **ici, en un seul endroit**, et
/// consommée à la fois par [ServicePassage#verifierFenetreSaisonniere] (vérification à la saisie) et
/// par le solde de saison (feature `saison`, #2356). C'est la condition pour que R3 n'existe pas en
/// deux exemplaires susceptibles de diverger.
///
/// Passage 1 : du **15 juin au 31 juillet**. Passage 2 : du **15 août au 30 septembre**. Tout autre
/// numéro de passage n'a pas de fenêtre définie ([Optional#empty()]).
///
/// @param debut premier jour **inclus** de la fenêtre
/// @param fin dernier jour **inclus** de la fenêtre
public record FenetreSaisonniere(LocalDate debut, LocalDate fin) {

    /// Vrai si `date` tombe dans la fenêtre fermée `[debut, fin]` (bornes incluses).
    public boolean contient(LocalDate date) {
        return !date.isBefore(debut) && !date.isAfter(fin);
    }

    /// Fenêtre attendue pour le passage `numeroPassage` de l'année `annee`, ou [Optional#empty()]
    /// pour un numéro sans fenêtre définie (autre que 1 ou 2).
    public static Optional<FenetreSaisonniere> pour(int numeroPassage, int annee) {
        return switch (numeroPassage) {
            case 1 -> Optional.of(new FenetreSaisonniere(LocalDate.of(annee, 6, 15), LocalDate.of(annee, 7, 31)));
            case 2 -> Optional.of(new FenetreSaisonniere(LocalDate.of(annee, 8, 15), LocalDate.of(annee, 9, 30)));
            default -> Optional.empty();
        };
    }
}

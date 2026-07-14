package fr.univ_amu.iut.commun.model;

import java.util.Optional;

/// **Port** (inversion `passage` → `sites`) : retrouver un **point d'écoute local** à partir de ce que
/// la plateforme VigieChiro en dit — le numéro de **carré** (porté par le titre du site) et le **code**
/// de la localité (ex. `Z41`).
///
/// C'est le sens **inverse** de [ReferentielPoint] (qui va du point local vers son identité VigieChiro).
/// Il sert à la reconstruction des passages **jamais importés localement** (#1305) : la participation
/// distante dit « carré 130711, localité Z41 », il faut savoir à quel point local cela correspond — sans
/// que `passage` dépende de `sites` (le cycle est interdit par `ArchitectureTest`).
///
/// Défaut no-op dans les injecteurs partiels : l'`Optional` est alors vide, et la reconstruction refuse
/// en le disant plutôt que d'inventer un rattachement.
@FunctionalInterface
public interface PointParLocalite {

    /// Identifiant du point d'écoute `codePoint` du carré `numeroCarre`, ou vide s'il n'existe pas
    /// localement (site jamais créé, ou point absent).
    Optional<Long> pour(String numeroCarre, String codePoint);
}

package fr.univ_amu.iut.commun.model;

import java.util.Objects;

/// Résultat élémentaire d'une vérification de règle métier **non bloquante** ou **bloquante**.
///
/// Le modèle conceptuel distingue deux familles de règles (cf. `Règles métier.md`) :
///
/// - les règles **SOFT** (ex. R3 : passage hors fenêtre, R4 : intervalle &lt; 1 mois) :
/// l'application *alerte sans bloquer*, l'utilisateur reste libre de continuer ;
/// - les règles **BLOQUANTES** (ex. R14 : un passage « Inexploitable » ne peut pas rejoindre un
/// lot) : l'action est *refusée*.
///
/// Une `Alerte` porte ce niveau + un message destiné à l'utilisateur. Les services agrègent
/// plusieurs alertes dans un [ResultatVerification]. Attention : une alerte *bloquante*
/// exprimée via ce type sert à **présenter** un refus à l'IHM (cumul d'erreurs) ; un refus qui
/// doit interrompre un traitement côté service est levé par une [RegleMetierException].
///
/// @param niveau gravité de l'alerte
/// @param message texte affichable (français)
public record Alerte(Niveau niveau, String message) {

    /// Niveau de gravité d'une alerte métier.
    public enum Niveau {
        /// Avertissement non bloquant (l'utilisateur peut passer outre).
        SOFT,
        /// Violation bloquante (l'action est refusée).
        BLOQUANT
    }

    public Alerte {
        Objects.requireNonNull(niveau, "niveau");
        Objects.requireNonNull(message, "message");
    }

    /// Crée une alerte non bloquante (avertissement).
    public static Alerte soft(String message) {
        return new Alerte(Niveau.SOFT, message);
    }

    /// Crée une alerte bloquante.
    public static Alerte bloquante(String message) {
        return new Alerte(Niveau.BLOQUANT, message);
    }

    /// `true` si cette alerte est bloquante.
    public boolean estBloquante() {
        return niveau == Niveau.BLOQUANT;
    }
}

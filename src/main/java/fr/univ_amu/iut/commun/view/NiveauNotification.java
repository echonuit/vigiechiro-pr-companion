package fr.univ_amu.iut.commun.view;

/// Ce que vaut une nouvelle rendue par [Notificateur]. Deux niveaux suffisent : l'application n'a
/// jamais eu besoin de plus, et un troisième niveau serait un choix à faire à chaque appel sans que
/// l'utilisateur y gagne quoi que ce soit.
public enum NiveauNotification {

    /// L'action a eu lieu, entièrement.
    INFORMATION,

    /// L'action n'a pas eu lieu, ou seulement en partie : quelque chose reste à faire ou à regarder.
    AVERTISSEMENT
}

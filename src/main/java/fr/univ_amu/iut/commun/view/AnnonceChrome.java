package fr.univ_amu.iut.commun.view;

import java.util.Optional;

/// Contrat d'une **annonce du chrome** : un message que l'application porte à la connaissance de
/// l'utilisateur **au démarrage**, sans qu'il l'ait demandé et sans le bloquer (#2109).
///
/// Même mécanisme d'inversion de dépendance que [ActionMenu] / [ActiviteAccueil] : le socle déclare le
/// contrat et affiche le bandeau, chaque feature en fournit des implémentations enregistrées dans son
/// `Multibinder<AnnonceChrome>`, et le socle **ne dépend d'aucune feature**. Le chrome n'a donc pas à
/// savoir qu'il existe une vérification de mise à jour - il sait seulement afficher ce qu'on lui donne.
///
/// **Réservé à ce qui est utile et rare.** Une annonce s'impose à l'utilisateur au lancement : c'est
/// une ressource qui s'épuise vite. Ce qui relève d'un compte rendu d'action va au bandeau de l'écran
/// concerné (ADR 0023), ce qui décrit un état va à la barre de statut (ADR 0039).
@FunctionalInterface
public interface AnnonceChrome {

    /// Cherche s'il y a lieu d'annoncer quelque chose.
    ///
    /// **Appelée hors du fil JavaFX** : l'implémentation peut lire le réseau ou le disque sans
    /// précaution particulière, et *doit* le faire ici plutôt qu'au moment de l'affichage.
    ///
    /// Rend vide quand il n'y a rien à dire - ce qui est le cas le plus fréquent, et doit rester
    /// silencieux : une annonce vide n'affiche pas un bandeau vide, elle n'affiche rien.
    Optional<Annonce> chercher();

    /// Ce qui s'affiche : un message, et de quoi agir.
    ///
    /// @param message ce que l'utilisateur lit, en une phrase
    /// @param libelleAction le libellé du lien (ex. « Voir la version »)
    /// @param adresseAction l'adresse ouverte au clic, dans le navigateur du système
    record Annonce(String message, String libelleAction, String adresseAction) {}
}

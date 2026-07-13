package fr.univ_amu.iut.commun.model;

import java.util.concurrent.atomic.AtomicBoolean;

/// Jeton d'**annulation coopérative** d'une opération longue, vocabulaire **partagé** du socle (#1252,
/// généralise le jeton né dans la feature importation #139/#146). Le jeton appartient à l'**appelant**
/// (l'écran) : le fil d'IHM appelle [#annuler()] (bouton « Annuler »), le travail hors fil JavaFX le
/// consulte entre deux unités de travail et s'arrête **proprement** - jamais d'interruption brutale.
///
/// Deux styles de consultation, au choix du travail :
///
/// - **exception** : [#leverSiAnnule()] lève [OperationAnnuleeException], que le socle
///   [fr.univ_amu.iut.commun.view.ExecuteurTache] conclut par le callback `annule` (import : la boucle
///   s'arrête, l'appelant nettoie les fichiers partiels) ;
/// - **retour partiel** : le moteur lit [#estAnnule()] (ou `jeton::estAnnule` en `BooleanSupplier`),
///   termine l'unité en vol et **rend un bilan honnête** par le chemin de succès (dépôt : jamais d'unité
///   fantôme, la reprise ne renverra que le manquant).
///
/// Ce contrat coopératif reste **testable en synchrone** : un test annule le jeton *avant* de déclencher
/// l'opération et vérifie l'arrêt au premier point de contrôle (la simultanéité réelle relève de l'E2E).
/// Thread-safe ([AtomicBoolean]) : le drapeau est posé sur le fil JavaFX et lu hors fil.
public final class JetonAnnulation {

    private final AtomicBoolean annule = new AtomicBoolean(false);

    /// Demande l'annulation (idempotent). Appelé sur le fil JavaFX.
    public void annuler() {
        annule.set(true);
    }

    /// `true` si l'annulation a été demandée.
    public boolean estAnnule() {
        return annule.get();
    }

    /// Lève [OperationAnnuleeException] si l'annulation a été demandée ; sinon ne fait rien. À appeler
    /// entre deux unités de travail pour arrêter la boucle au plus tôt.
    public void leverSiAnnule() {
        if (annule.get()) {
            throw new OperationAnnuleeException();
        }
    }

    /// Jeton **neutre** (jamais annulé), pour les appels qui n'offrent pas d'annulation.
    public static JetonAnnulation neutre() {
        return new JetonAnnulation();
    }
}

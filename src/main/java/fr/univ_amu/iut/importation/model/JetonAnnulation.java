package fr.univ_amu.iut.importation.model;

import java.util.concurrent.atomic.AtomicBoolean;

/// Jeton d'**annulation coopérative** d'une opération longue (décompression #139, import #146). Le fil
/// d'IHM appelle [#annuler()] (bouton « Annuler ») ; le travail hors-thread, lui, vérifie
/// [#leverSiAnnule()] entre deux unités de travail (chaque fichier copié/transformé/extrait) et s'arrête
/// **proprement** en levant [AnnulationImportException], après quoi l'appelant nettoie les fichiers
/// partiels. Thread-safe ([AtomicBoolean]) : le drapeau est posé sur le fil JavaFX et lu hors-thread.
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

    /// Lève [AnnulationImportException] si l'annulation a été demandée ; sinon ne fait rien. À appeler
    /// entre deux unités de travail pour arrêter la boucle au plus tôt.
    public void leverSiAnnule() {
        if (annule.get()) {
            throw new AnnulationImportException();
        }
    }

    /// Jeton **neutre** (jamais annulé), pour les appels qui n'offrent pas d'annulation.
    public static JetonAnnulation neutre() {
        return new JetonAnnulation();
    }
}

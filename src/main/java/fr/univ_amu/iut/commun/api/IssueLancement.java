package fr.univ_amu.iut.commun.api;

/// Les cinq issues possibles d'une demande de lancement du traitement (#1261). Trois viennent du serveur,
/// une de son silence, et une **de nous** : celle qui protège les observations.
public enum IssueLancement {

    /// Demande **acceptée** : le traitement est planifié (`PLANIFIE`), le calcul suivra.
    ACCEPTE,

    /// Refus **bénin** : un traitement est déjà planifié ou en cours pour cette participation. Le serveur
    /// refuse toute demande concurrente dans une fenêtre de 24 h ; il travaille, il n'y a qu'à attendre.
    DEJA_LANCE,

    /// Refus **de notre fait**, avant même d'appeler le serveur : la participation a **déjà été calculée**,
    /// et le serveur, à chaque compute, **supprime toutes les `donnees` avant de recalculer**
    /// (`task_participation.py:726-731`). Sur une nuit déposée en archives ZIP — le mode par défaut depuis
    /// #984 — les WAV ne sont pas conservés sur S3 (#1244) : le recalcul ne pourrait pas les relire et les
    /// observations seraient **définitivement perdues**. Un premier lancement est sûr, une relance ne l'est
    /// pas : elle exige un accord explicite (option `--forcer` de la commande, #1265).
    RELANCE_BLOQUEE,

    /// Le serveur a **répondu non** pour une autre raison : droits insuffisants, participation inconnue,
    /// panne. Le détail (statut HTTP + corps) accompagne le résultat.
    REFUSE,

    /// Le serveur n'a **pas répondu** : application non connectée, réseau coupé, délai dépassé. On ne sait
    /// rien de l'état réel — la demande a pu partir malgré tout, il faut relire l'état pour savoir.
    INJOIGNABLE
}

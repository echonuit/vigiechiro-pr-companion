package fr.univ_amu.iut.audit.model;

/// Nature d'un [ConstatAudit], pour regrouper et expliquer les incohérences détectées par
/// l'[ServiceAuditCoherence].
public enum CategorieConstat {
    /// Un `file_path` persisté en base ne correspond à aucun fichier sur disque (sous le workspace).
    DISQUE_MANQUANT,
    /// Un fichier présent sous `bruts/` ou `transformes/` n'est référencé par aucune ligne en base.
    DISQUE_ORPHELIN,
    /// Un dossier de session sur disque n'a aucune ligne `recording_session` correspondante.
    DOSSIER_ORPHELIN,
    /// Un nom de fichier ne porte pas le préfixe attendu du passage.
    PREFIXE_NON_CONFORME,
    /// Une unité déjà déposée porte un nom qui ne correspond plus au préfixe courant (renommage
    /// après dépôt) : divergence base / serveur.
    DEPOT_DIVERGENT,
    /// Un passage n'a aucune session d'enregistrement (jamais importé).
    SESSION_ABSENTE,
    /// Une unité déposée est absente côté serveur (non traitée ou non déposée) : constat **en ligne**.
    SERVEUR_MANQUANT,
    /// Le journal de traitement du serveur est indisponible (hors connexion, ou traitement non terminé) :
    /// la vérification **en ligne** est partielle.
    SERVEUR_INJOIGNABLE,
    /// Un point d'écoute local diverge de sa localité serveur (inconnu du serveur, ou position
    /// différente) : constat **en ligne**.
    POINT_DIVERGENT
}

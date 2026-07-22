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
    /// L'audio d'un passage n'est **pas entièrement présent** sur disque (ADR 0048) : un seul
    /// constat informatif portant le décompte `présentes / total`, jamais une erreur par fichier.
    /// L'absence est un **état observé**, pas une corruption : l'utilisateur possède ses fichiers.
    AUDIO_INDISPONIBLE,
    /// Un fichier est **présent** au chemin attendu, mais son contenu n'est **pas celui que la base
    /// décrit** : empreinte divergente (#1299). Ce n'est pas une absence, c'est un **conflit** - une
    /// redécoupe, une autre nuit du même carré, une sauvegarde restaurée d'une autre version. Le
    /// laisser passer ferait **valider une espèce sur le mauvais audio**, en silence : c'est le seul
    /// écart de disponibilité qui reste une **erreur**.
    AUDIO_DIVERGENT,
    /// Une unité déposée est absente côté serveur (non traitée ou non déposée) : constat **en ligne**.
    SERVEUR_MANQUANT,
    /// Le journal de traitement du serveur est indisponible (hors connexion, ou traitement non terminé) :
    /// la vérification **en ligne** est partielle.
    SERVEUR_INJOIGNABLE,
    /// Un point d'écoute local diverge de sa localité serveur (inconnu du serveur, ou position
    /// différente) : constat **en ligne**.
    POINT_DIVERGENT,
    /// **Sens inverse** de [#POINT_DIVERGENT] (#1455) : la plateforme connaît une localité qu'on n'a
    /// **pas** ici, **et** cette localité porte des nuits absentes d'ici. Le prochain rapprochement
    /// créerait le point **en silence**, et ce silence masquerait du **travail qui existe ailleurs**.
    ///
    /// Une localité serveur inconnue qui ne porte **aucune** nuit ne fait **pas** de constat : la créer est
    /// le comportement voulu (c'est ce qui rend possible la restauration depuis une base vierge, #1050, et
    /// le reset guidé, #1419). Ce n'est pas l'absence du point qui mérite d'être dite, c'est ce qu'elle
    /// **cache**.
    POINT_SERVEUR_IGNORE
}

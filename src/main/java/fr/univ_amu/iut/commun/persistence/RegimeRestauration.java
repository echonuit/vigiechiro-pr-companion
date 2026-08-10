package fr.univ_amu.iut.commun.persistence;

/// Comment une restauration complète a replacé les dossiers, **selon la place disponible** (#3563).
///
/// Le régime n'est pas un réglage : il est choisi par [BesoinDePlace#regimePour(long)] au vu de
/// l'espace libre, et il figure au compte rendu parce qu'il dit ce qui **aurait** été garanti en cas
/// de panne. Une restauration réussie sous le régime dégradé est une restauration réussie ; ce qui
/// diffère est la promesse qu'on aurait pu tenir si le disque avait lâché en route.
public enum RegimeRestauration {

    /// Toutes les racines étalées et vérifiées, puis toutes basculées : une panne en cours de copie
    /// ne laisse que des temporaires, et l'état local est celui d'avant.
    ENSEMBLE,

    /// Une racine à la fois : étalée, vérifiée, basculée, puis la suivante. Chaque nuit reste
    /// tout-ou-rien ; **l'ensemble ne l'est plus**, et une panne peut laisser les premières en place
    /// et les dernières non. Choisi quand la place ne permet pas mieux, plutôt que de refuser.
    RACINE_PAR_RACINE,

    /// Aucun étalement : les dossiers sont copiés droit à leur place. C'est le chemin des sauvegardes
    /// **antérieures au manifeste** (#2726), qui ne disent pas d'où venaient les dossiers et ne
    /// permettent donc ni de vérifier ni de replacer. Une panne y laisse une destination à moitié
    /// écrite - ce n'est pas un choix de place, c'est ce que la sauvegarde permet.
    COPIE_DIRECTE
}

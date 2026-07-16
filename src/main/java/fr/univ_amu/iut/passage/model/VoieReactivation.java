package fr.univ_amu.iut.passage.model;

/// Par où l'audio est revenu (#1406). L'utilisateur désigne **un dossier** ; c'est l'application qui
/// reconnaît ce qu'il contient, en confrontant les noms des fichiers à ceux qu'elle a en base. Rien
/// n'est deviné : ou bien les noms des séquences y sont, ou bien ceux des originaux, ou bien ni l'un ni
/// l'autre - et on le dit.
public enum VoieReactivation {

    /// Les **séquences d'écoute** (tranches de 5 s) ont été retrouvées telles quelles : voie directe.
    TRANSFORMES,

    /// Seuls les **bruts** ont été retrouvés : les séquences sont **régénérées** à partir d'eux
    /// (transformation déterministe, R11), puis vérifiées comme n'importe quel candidat.
    BRUTS,

    /// Le passage a été **reconstruit** depuis la plateforme (#1305) : il connaît le **nom** de ses
    /// séquences (issu du CSV distant) mais ne porte **aucun inventaire d'originaux réutilisable** - ni
    /// empreinte, ni fréquence d'acquisition. La voie « bruts » ne peut donc rien apparier ni régénérer,
    /// et prétendre que les fichiers sont « introuvables » serait un mensonge : les bruts peuvent être là,
    /// c'est l'application qui n'a pas de quoi les relier. On le **dit** (#1648, EPIC #1653) ; l'hydratation
    /// effective depuis les bruts est un palier suivant.
    RECONSTRUIT,

    /// Le dossier ne contient ni les unes ni les autres : rien à rebrancher.
    AUCUNE
}

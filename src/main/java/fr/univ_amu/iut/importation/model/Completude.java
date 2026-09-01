package fr.univ_amu.iut.importation.model;

/// Ce que le journal du capteur permet de dire d'une nuit détectée (#4990).
///
/// Les valeurs nomment l'**état du domaine**, jamais la gravité de son annonce : celle-ci se décide à
/// la surface, où l'on parle à quelqu'un (ADR 0038, et ADR 4984 qui l'applique au protocole horaire).
///
/// **Trois valeurs, parce que deux forçaient à choisir entre deux erreurs.** Une nuit sans cycle
/// correspondant était déclarée complète, avec le badge le plus rassurant. Le choix était défendu -
/// ne pas deviner, pour éviter la fausse alerte d'une nuit calme - mais il lisait l'absence de preuve
/// comme une preuve. Le journal du capteur est circulaire (R19) : une carte pleine efface les entrées
/// des **premières** nuits, celles qu'on relira le plus tard.
public enum Completude {

    /// Le cycle s'est terminé par une mise en veille normale.
    COMPLETE,

    /// Le cycle existe et s'est terminé anormalement : carte pleine, mode erreur, journal interrompu.
    /// La nuit porte alors un motif.
    TRONQUEE,

    /// Aucun cycle ne couvre cette nuit : journal absent, ou entrées effacées par la rotation.
    ///
    /// Ce n'est **pas** un défaut de la nuit, et ce n'est pas une nuit saine non plus. C'est
    /// l'absence d'information, et elle se dit.
    INCONNUE
}

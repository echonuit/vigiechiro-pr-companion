package fr.univ_amu.iut.audit.model;

/// D'où l'audio d'une nuit **reviendrait** si l'on repartait d'une base neuve (#1151).
///
/// C'est la question à laquelle il faut savoir répondre **avant** un reset, nuit par nuit : pas après.
/// Les métadonnées et les observations, elles, reviennent toujours du serveur (#1050) ; l'audio, non.
///
/// La cascade est celle de l'issue : le **disque** d'abord (rapide, complet, hors réseau), le **serveur**
/// ensuite mais *seulement* si la nuit a été déposée en **WAV** et rattachée, et sinon **rien**.
public enum SourceAudio {

    /// Les fichiers sont **sur le disque** : ils se réimportent, la nuit redevient complète.
    DISQUE("l'audio est sur le disque : il se réimporte"),

    /// Le disque ne les a plus, mais la nuit a été déposée **en WAV** et **rattachée** : le serveur les a
    /// gardés (`pieces_jointes?wav=true` → S3). Cas rare : le mode par défaut est le ZIP.
    SERVEUR("l'audio n'est plus sur le disque, mais la nuit a été déposée en WAV : le serveur peut le rendre"),

    /// Ni disque, ni serveur. **Ce n'est pas une impasse** : depuis #1297, la nuit devient un **passage
    /// archivé** : observations et vérifications consultables, écoute impossible, réactivation possible si
    /// l'utilisateur retrouve un jour ses fichiers. Mais c'est une **perte assumée**, et elle doit être
    /// annoncée **avant** d'agir, pas découverte après.
    PERDU("l'audio est perdu : la nuit deviendra un passage archivé (consultable, non écoutable)");

    private final String explication;

    SourceAudio(String explication) {
        this.explication = explication;
    }

    /// Ce que cet état veut dire, en clair, pour l'utilisateur qui s'apprête à repartir de zéro.
    public String explication() {
        return explication;
    }

    /// `true` si l'audio de cette nuit ne survivra **pas** à un reset : le seul cas qui exige une
    /// confirmation explicite.
    public boolean perteDefinitive() {
        return this == PERDU;
    }
}

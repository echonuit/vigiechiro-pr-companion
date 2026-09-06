package fr.univ_amu.iut.commun.model;

/// La clé du réglage de désignation, **et son défaut**, au même endroit.
///
/// Le dépôt a payé cette leçon sur `ReglageConservationOriginaux` : un défaut recopié dans l'écran
/// des réglages et dans le lecteur est une divergence qui attend son tour, et rien ne signale
/// l'oubli quand on change d'avis d'un seul côté. Ici trois endroits les liraient - l'onglet, la
/// préférence persistée, et le défaut hors persistance - ce qui rend le regroupement d'autant plus
/// nécessaire.
public final class ReglageDesignation {

    /// Clé dans `app_setting`. Un **texte**, jamais un `Enum.name()` : le socle refuse volontairement
    /// `lireEnum` (#2042), renommer une constante ferait retomber le réglage de chaque utilisateur
    /// sur le défaut, en silence.
    public static final String CLE = "designation.dialogue.application";

    /// Défaut : **le dialogue du système**. Une installation qui n'y touche pas se comporte comme
    /// avant ce chantier, et c'est la contrepartie assumée du réglage - le natif reste ce que voit
    /// l'utilisateur ordinaire, avec ses raccourcis et ses favoris.
    public static final boolean DEFAUT = false;

    private ReglageDesignation() {}
}

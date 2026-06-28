package fr.univ_amu.iut.commun.view;

/// Les deux **prismes** de l'application, présentés comme deux sections sur l'écran d'accueil. Chaque
/// [ActiviteAccueil] déclare le sien ([ActiviteAccueil#prisme()]), et le [MainController] regroupe les
/// cartes par prisme — dans l'ordre de déclaration de cette énum.
///
/// L'application a deux portes d'entrée complémentaires : **produire** la donnée (workflow de collecte
/// des nuits et des passages) et l'**exploiter** (inventaire des espèces, biodiversité). Les rendre
/// explicites dès l'accueil évite une liste plate de cartes hétérogènes.
public enum Prisme {

    /// Produire la donnée : sites, passages, vue agrégée — le workflow de collecte.
    COLLECTE_PASSAGES("Collecte & passages", "fas-satellite-dish"),

    /// Exploiter la donnée : espèces observées, sons de référence — la biodiversité.
    ESPECES_BIODIVERSITE("Espèces & biodiversité", "fas-leaf");

    private final String libelle;
    private final String iconeLiteral;

    Prisme(String libelle, String iconeLiteral) {
        this.libelle = libelle;
        this.iconeLiteral = iconeLiteral;
    }

    /// Intitulé de la section d'accueil (ex. « Collecte & passages »).
    public String libelle() {
        return libelle;
    }

    /// Code d'icône [Ikonli](https://kordamp.org/ikonli/) FontAwesome 5 de l'en-tête de section. Comme
    /// pour les cartes, c'est le socle qui en construit le `FontIcon` (les features n'en dépendent pas).
    public String iconeLiteral() {
        return iconeLiteral;
    }
}

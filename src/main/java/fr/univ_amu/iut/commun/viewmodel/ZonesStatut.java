package fr.univ_amu.iut.commun.viewmodel;

/// Contenu des **trois zones de la barre de statut** du chrome (#495).
///
/// Value object immuable transporté du controller d'écran jusqu'au pied de page, via le contrat
/// `ResumeStatut` et le [NavigationViewModel]. Convention d'usage :
///
/// - **gauche** : contexte de l'écran (ex. « Carré 640380 · A1 »), optionnel ;
/// - **centre** : résumé de l'écran (ex. « 60 observation(s) ») ;
/// - **droite** : compteurs / état vivant (ex. « 12 / 60 revues »).
///
/// Une zone vide vaut la chaîne vide `""`. Quand **toutes** les zones sont vides ([#estVide]), le chrome
/// **masque** la barre de statut : aucune inscription inutile ne subsiste sur les écrans sans résumé.
/// Le chrome **superpose** les zones de l'écran sur un défaut (cf. [#superposer]) : une zone laissée vide
/// par l'écran retombe sur le défaut, de sorte qu'un écran n'a besoin de renseigner que les zones qui le
/// concernent.
///
/// @param gauche zone gauche (identité / contexte)
/// @param centre zone centre (résumé de l'écran)
/// @param droite zone droite (compteurs / état vivant)
public record ZonesStatut(String gauche, String centre, String droite) {

    /// Toutes les zones vides : un écran sans résumé laisse le chrome afficher son défaut partout.
    public static final ZonesStatut VIDE = new ZonesStatut("", "", "");

    /// Normalise les `null` en chaîne vide pour que le rendu et la superposition n'aient jamais à s'en soucier.
    public ZonesStatut {
        gauche = gauche == null ? "" : gauche;
        centre = centre == null ? "" : centre;
        droite = droite == null ? "" : droite;
    }

    /// Aucune zone renseignée : le chrome masque alors la barre de statut (rien à afficher).
    public boolean estVide() {
        return gauche.isBlank() && centre.isBlank() && droite.isBlank();
    }

    /// Résumé n'occupant que la zone **centre** (cas courant : la gauche reste vide).
    public static ZonesStatut centre(String centre) {
        return new ZonesStatut("", centre, "");
    }

    /// Résumé occupant le **centre** (résumé) et la **droite** (compteurs), la gauche restant au défaut.
    public static ZonesStatut centreEtDroite(String centre, String droite) {
        return new ZonesStatut("", centre, droite);
    }

    /// Première chaîne **non vide** parmi `candidats` (par priorité décroissante), ou `""` si toutes sont
    /// vides. Compose la **zone droite** « état vivant » d'un écran, où une seule information s'affiche à la
    /// fois : typiquement progression d'une tâche en cours &gt; alerte &gt; compteur / bilan au repos (#1016).
    public static String premierNonVide(String... candidats) {
        for (String candidat : candidats) {
            if (candidat != null && !candidat.isBlank()) {
                return candidat;
            }
        }
        return "";
    }

    /// Superpose `dessus` sur `dessous`, **zone par zone** : une zone non vide de `dessus` l'emporte,
    /// sinon celle de `dessous` est conservée. Permet à un écran de ne renseigner que certaines zones,
    /// les autres gardant le défaut du chrome (notamment l'identité en zone gauche).
    public static ZonesStatut superposer(ZonesStatut dessous, ZonesStatut dessus) {
        return new ZonesStatut(
                dessus.gauche.isBlank() ? dessous.gauche : dessus.gauche,
                dessus.centre.isBlank() ? dessous.centre : dessus.centre,
                dessus.droite.isBlank() ? dessous.droite : dessus.droite);
    }
}

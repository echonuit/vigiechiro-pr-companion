package fr.univ_amu.iut.recette;

import java.util.regex.Pattern;

/// La façon dont une session de recette déclare un cas, en **un seul endroit**.
///
/// ## Pourquoi cette classe existe
///
/// Trois lecteurs de ces fichiers coexistent déjà : [CorrespondanceRecetteTest] pour la couverture,
/// `recette.film.LibelleDesCas` pour le carton d'ouverture des clips, et [PageDesClipsTest] pour la
/// page qui les montre. Les deux premiers ont découvert **séparément**, à des semaines d'écart, que
/// certaines sessions cochent leurs puces :
///
/// ```
/// - [ ] **S10-01** · Lancer l'application…
/// - **S1-01** · L'accueil affiche…
/// ```
///
/// Le second l'a payé en rendant 345 libellés sur 392, les 47 manquants venant de deux fichiers.
/// Un carton sans libellé ne fait rougir personne : il se constate à l'œil, et seulement si
/// quelqu'un regarde le film.
///
/// ⚠️ Le motif est donc **ici**, et le troisième lecteur ne le réécrit pas. `LibelleDesCas` a le sien
/// pour l'instant, parce qu'il en extrait aussi le libellé : les faire converger est une suite, pas
/// un préalable à cette page.
public final class MotifDeCas {

    /// Un cas se déclare `- **S1-04** · texte`, et `- **S1-26** · *perceptif* · texte` s'il ne se
    /// tranche qu'à l'œil. La case à cocher facultative est le geste de qui **joue** la session.
    ///
    /// Groupe 1 : l'identifiant. Groupe 2 : non nul si le cas est perceptif.
    public static final Pattern CAS =
            Pattern.compile("^- (?:\\[[ xX]\\] )?\\*\\*(S\\d+-\\d+)\\*\\* ·( \\*perceptif\\* ·)?", Pattern.MULTILINE);

    private MotifDeCas() {}
}

package fr.univ_amu.iut.recette;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
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
///
/// ## Les marqueurs, et pourquoi ils sont une SUITE (#4417)
///
/// Un cas portait un seul marqueur possible, `*perceptif*`. Il en porte désormais plusieurs, parce
/// que les questions qu'ils tranchent sont indépendantes : *qui juge ce cas*, *à quel geste
/// appartient-il*, *qu'est-ce qui l'empêche d'être filmé*. Les mêler dans un marqueur unique aurait
/// forcé à inventer des combinaisons - `perceptif-et-hors-portée` - que personne ne veut lire.
public final class MotifDeCas {

    /// Un cas se déclare `- **S1-04** · texte`, et porte entre l'identifiant et le texte une suite -
    /// éventuellement vide - de marqueurs `*…*` séparés par `·`. La case à cocher facultative est le
    /// geste de qui **joue** la session.
    ///
    /// Groupe 1 : l'identifiant. Groupe 2 : la suite brute des marqueurs, à passer à [#marqueurs].
    public static final Pattern CAS =
            Pattern.compile("^- (?:\\[[ xX]\\] )?\\*\\*(S\\d+-\\d+)\\*\\* ·((?: \\*[^*]+\\* ·)*)", Pattern.MULTILINE);

    /// Un marqueur isolé dans la suite : `*perceptif*`, `*geste: fiche-recuperee*`, `*hors-portée: …*`.
    private static final Pattern MARQUEUR = Pattern.compile("\\*([^*]+)\\*");

    /// Le cas ne se tranche qu'à l'œil : aucun test ne le voit, et c'est normal.
    public static final String PERCEPTIF = "perceptif";

    /// Le cas appartient à un geste nommé, qu'un SEUL clip porte de bout en bout.
    ///
    /// C'est une **déclaration**, et non une déduction depuis l'étape de session. La déduction
    /// paraissait moins coûteuse - une étape est presque toujours un écran - mais elle ne laisse rien
    /// à confronter : un clip qui prétend porter un geste ne pourrait pas être pris en défaut d'en
    /// oublier un cas. Déclaré, le geste devient une promesse qu'un garde tient.
    public static final String GESTE = "geste";

    /// Une ÉTAPE du geste ne s'enregistre pas - ouvrir un navigateur, insérer une carte - et un
    /// carton descriptif la remplace pour ne pas couper le geste.
    ///
    /// À ne pas confondre avec [#HORS_PORTEE] : ici le geste EXISTE et se filme entier.
    public static final String CARTON = "carton";

    /// Le GESTE lui-même est impossible à jouer sur un banc, et le motif dit pourquoi.
    ///
    /// Ce marqueur porte un **fait**, pas une dette. Un cas sans clip et sans marqueur est une
    /// dette ; celui-ci dit qu'il n'y en aura jamais, et il doit donc se justifier.
    public static final String HORS_PORTEE = "hors-portée";

    /// Le cas se filmera, mais pas avant qu'un prérequis **nommé** existe, et le motif le nomme.
    ///
    /// C'est le mot qui manquait, et son absence a produit une dérive mesurée (#4458). Le
    /// vocabulaire n'offrait que deux états à un cas sans clip : rien du tout, c'est-à-dire une
    /// dette qu'aucun texte n'explique, ou [#HORS_PORTEE], qui affirme qu'il n'y aura **jamais** de
    /// clip. Les vingt-deux cas d'écriture de `S4` ont donc reçu le motif de leur bloc, et huit
    /// d'entre eux se sont retrouvés rangés avec la carte SD réelle et le validateur du MNHN, qui
    /// eux ne se débloqueront pas.
    ///
    /// La différence avec [#HORS_PORTEE] est celle du **jamais** et du **pas encore**, et elle se
    /// tient à ce que le motif nomme : un prérequis dont l'existence est **décidée**, pas espérée.
    /// « attend le générateur de bases déclarées (#4325) » se vérifie, « attend qu'on trouve une
    /// solution » ne dit rien et retombe dans le tapis que [#HORS_PORTEE] sans motif était déjà.
    public static final String PREREQUIS = "prérequis";

    private MotifDeCas() {}

    /// Les marqueurs d'un cas, du groupe 2 de [#CAS] vers `nom -> argument`.
    ///
    /// L'argument est la chaîne vide pour un marqueur qui n'en porte pas (`*perceptif*`), et le texte
    /// après le deux-points sinon (`*hors-portée: carte SD réelle*` rend `carte SD réelle`).
    ///
    /// L'ordre de déclaration est conservé : ce qui se lit d'abord dans la session se lit d'abord
    /// dans un message d'erreur.
    public static Map<String, String> marqueurs(String suite) {
        Map<String, String> lus = new LinkedHashMap<>();
        if (suite == null || suite.isBlank()) {
            return lus;
        }
        Matcher m = MARQUEUR.matcher(suite);
        while (m.find()) {
            String brut = m.group(1).trim();
            int deuxPoints = brut.indexOf(':');
            if (deuxPoints < 0) {
                lus.put(brut, "");
            } else {
                lus.put(
                        brut.substring(0, deuxPoints).trim(),
                        brut.substring(deuxPoints + 1).trim());
            }
        }
        return lus;
    }
}
